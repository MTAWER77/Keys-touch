package com.touchkeymapper.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.touchkeymapper.app.MainActivity

/**
 * The small floating "●" control button. Draggable anywhere on screen.
 * Tapping (without dragging) it opens a compact vertical menu:
 * Edit Layout / Profiles / Settings / Hide Controls / Close Overlay.
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val onBubbleTap: () -> Unit
) : FrameLayout(context) {

    private val bubbleSizePx = (48 * resources.displayMetrics.density).toInt()
    private var menuOpen = false
    private var menuView: LinearLayout? = null
    private var hidden = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val bubbleDot = View(context).apply {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#2196F3"))
        }
        background = bg
        alpha = 0.9f
    }

    init {
        addView(bubbleDot, LayoutParams(bubbleSizePx, bubbleSizePx))
        setOnTouchListener { _, event -> handleTouch(event) }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val params = layoutParams as? WindowManager.LayoutParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) isDragging = true
                if (isDragging) {
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(this, params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) toggleMenu()
                return true
            }
        }
        return false
    }

    fun setHiddenState(isHidden: Boolean) {
        hidden = isHidden
        bubbleDot.alpha = if (isHidden) 0.4f else 0.9f
    }

    private fun toggleMenu() {
        if (menuOpen) {
            closeMenu()
        } else {
            openMenu()
        }
    }

    private fun openMenu() {
        menuOpen = true
        val menu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                cornerRadius = 16 * resources.displayMetrics.density
                setColor(Color.parseColor("#EE1B1B2E"))
            }
            background = bg
            setPadding(24, 16, 24, 16)
        }

        menu.addView(menuLabel("TouchKey Mapper", bold = true))
        menu.addView(menuAction("Edit Layout") { launchApp("edit_layout") })
        menu.addView(menuAction("Profiles") { launchApp("profiles") })
        menu.addView(menuAction("Settings") { launchApp("settings") })
        menu.addView(menuAction(if (hidden) "Show Controls" else "Hide Controls") {
            val action = if (hidden) OverlayService.ACTION_SHOW_CONTROLS else OverlayService.ACTION_HIDE_CONTROLS
            context.startService(Intent(context, OverlayService::class.java).apply { this.action = action })
            setHiddenState(!hidden)
            closeMenu()
        })
        menu.addView(menuAction("Close Overlay") {
            OverlayService.stop(context)
            closeMenu()
        })

        menuView = menu
        addView(menu, LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = bubbleSizePx + 8
        })
    }

    private fun closeMenu() {
        menuOpen = false
        menuView?.let { removeView(it) }
        menuView = null
    }

    private fun menuLabel(text: String, bold: Boolean): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 13f
        setPadding(8, 4, 8, 8)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun menuAction(text: String, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        setPadding(8, 12, 8, 12)
        setOnClickListener { onClick() }
    }

    private fun launchApp(destination: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(MainActivity.EXTRA_DESTINATION, destination)
        }
        context.startActivity(intent)
        closeMenu()
    }
}

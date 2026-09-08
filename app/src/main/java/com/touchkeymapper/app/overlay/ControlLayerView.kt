package com.touchkeymapper.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import com.touchkeymapper.app.TouchKeyMapperApp
import com.touchkeymapper.app.accessibility.TouchInjectionService
import com.touchkeymapper.app.data.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Renders every button / joystick / mouse-area for the active [profile] as
 * live, touch-reactive views inside a single full-screen overlay window.
 * Each control forwards user touches to [TouchInjectionService] as real
 * gestures at the control's configured target coordinates.
 */
@SuppressLint("ClickableViewAccessibility")
class ControlLayerView(context: Context, private val profile: Profile) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // Screen-space rects (px) of every control. The window only "catches"
    // touches inside these rects; everywhere else, the touch passes straight
    // through to the app underneath. Without this, a full-screen overlay
    // window would silently block the whole game.
    private val controlRects = mutableListOf<Rect>()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        profile.buttons.filter { it.isVisible }.forEach { addButtonView(it) }
        profile.joysticks.filter { it.isVisible }.forEach { addJoystickView(it) }
        profile.mouseAreas.filter { it.isVisible }.forEach { addMouseAreaView(it) }

        viewTreeObserver.addOnComputeInternalInsetsListener { info ->
            info.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            val region = Region()
            controlRects.forEach { region.union(it) }
            info.touchableRegion.set(region)
        }
    }

    private fun trackTouchable(leftPx: Int, topPx: Int, widthPx: Int, heightPx: Int) {
        controlRects.add(Rect(leftPx, topPx, leftPx + widthPx, topPx + heightPx))
    }

    private fun addButtonView(config: ButtonConfig) {
        val view = TextView(context).apply {
            text = config.label
            setTextColor(Color.parseColor(config.textColorHex))
            textSize = config.textSizeSp
            gravity = android.view.Gravity.CENTER
            alpha = config.opacity
            background = buildButtonBackground(config)
        }
        val lp = LayoutParams(dp(config.width).toInt(), dp(config.height).toInt()).apply {
            leftMargin = dp(config.x).toInt()
            topMargin = dp(config.y).toInt()
        }
        view.rotation = config.rotationDegrees
        addView(view, lp)
        trackTouchable(lp.leftMargin, lp.topMargin, lp.width, lp.height)

        var toggledOn = false
        view.setOnTouchListener { _, event ->
            val service = TouchInjectionService.instance
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    vibrateShort()
                    when (config.inputMode) {
                        InputMode.HOLD -> onPressStart(service, config)
                        InputMode.TOGGLE -> {
                            toggledOn = !toggledOn
                            if (toggledOn) onPressStart(service, config) else onPressEnd(service, config)
                        }
                    }
                    view.alpha = min(1f, config.opacity + 0.25f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (config.inputMode == InputMode.HOLD) onPressEnd(service, config)
                    view.alpha = config.opacity
                    true
                }
                else -> false
            }
        }
    }

    private fun onPressStart(service: TouchInjectionService?, config: ButtonConfig) {
        when (config.actionType) {
            ActionType.TAP_TARGET -> service?.tap(dp(config.targetX), dp(config.targetY))
            ActionType.HOLD_TARGET -> service?.startHold(config.id, dp(config.targetX), dp(config.targetY))
            ActionType.SWIPE_TARGET -> service?.swipe(
                dp(config.targetX), dp(config.targetY), dp(config.targetEndX), dp(config.targetEndY)
            )
        }
    }

    private fun onPressEnd(service: TouchInjectionService?, config: ButtonConfig) {
        if (config.actionType == ActionType.HOLD_TARGET) service?.releaseHold(config.id)
    }

    private fun buildButtonBackground(config: ButtonConfig): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(config.backgroundColorHex))
        setStroke(dp(config.borderWidthDp).toInt(), Color.parseColor(config.borderColorHex))
        cornerRadius = when (config.shape) {
            ButtonShape.RECTANGLE -> 0f
            ButtonShape.ROUNDED -> dp(config.cornerRadiusDp)
            ButtonShape.CIRCLE -> dp(max(config.width, config.height))
        }
    }

    private fun addJoystickView(config: JoystickConfig) {
        val outer = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
                setStroke(dp(1.5f).toInt(), Color.WHITE)
            }
            alpha = config.opacity
        }
        val knobSize = (dp(config.diameter) * 0.4f).toInt()
        val knob = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CCFFFFFF"))
            }
        }
        val outerSize = dp(config.diameter).toInt()
        val outerLp = LayoutParams(outerSize, outerSize).apply {
            leftMargin = dp(config.x).toInt()
            topMargin = dp(config.y).toInt()
        }
        addView(outer, outerLp)
        trackTouchable(outerLp.leftMargin, outerLp.topMargin, outerLp.width, outerLp.height)
        val knobLp = LayoutParams(knobSize, knobSize).apply {
            leftMargin = dp(config.x).toInt() + (outerSize - knobSize) / 2
            topMargin = dp(config.y).toInt() + (outerSize - knobSize) / 2
        }
        addView(knob, knobLp)

        val centerX = dp(config.x) + outerSize / 2f
        val centerY = dp(config.y) + outerSize / 2f
        val maxRadius = outerSize / 2f

        outer.setOnTouchListener { _, event ->
            val service = TouchInjectionService.instance
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - maxRadius
                    val dy = event.y - maxRadius
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    val clampedDist = min(dist, maxRadius)
                    val angle = kotlin.math.atan2(dy, dx)
                    val knobX = clampedDist * kotlin.math.cos(angle)
                    val knobY = clampedDist * kotlin.math.sin(angle)
                    knob.translationX = knobX
                    knob.translationY = knobY

                    val normalized = clampedDist / maxRadius
                    if (normalized > config.deadZone) {
                        val anchorX = dp(config.anchorX)
                        val anchorY = dp(config.anchorY)
                        val reach = dp(config.diameter) * config.sensitivity
                        service?.dragTo(
                            config.id,
                            anchorX + (knobX / maxRadius) * reach,
                            anchorY + (knobY / maxRadius) * reach
                        )
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    knob.translationX = 0f
                    knob.translationY = 0f
                    service?.releaseHold(config.id)
                    true
                }
                else -> false
            }
        }
    }

    private fun addMouseAreaView(config: MouseAreaConfig) {
        val pad = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(Color.parseColor("#22FFFFFF"))
                setStroke(dp(1f).toInt(), Color.parseColor("#55FFFFFF"))
            }
            alpha = config.opacity
        }
        val lp = LayoutParams(dp(config.width).toInt(), dp(config.height).toInt()).apply {
            leftMargin = dp(config.x).toInt()
            topMargin = dp(config.y).toInt()
        }
        addView(pad, lp)
        trackTouchable(lp.leftMargin, lp.topMargin, lp.width, lp.height)

        var lastX = 0f
        var lastY = 0f
        val mouseKeyId = "mouse_${config.id}"

        pad.setOnTouchListener { _, event ->
            val service = TouchInjectionService.instance
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastX) * config.sensitivity * (if (config.invertX) -1 else 1)
                    val dy = (event.rawY - lastY) * config.sensitivity * (if (config.invertY) -1 else 1)
                    lastX = event.rawX
                    lastY = event.rawY
                    // Cursor movement is relayed as a small continuous drag from
                    // screen center — real free cursor control requires a system
                    // pointer icon API not exposed to third-party apps, so this
                    // models relative movement as directional drag gestures,
                    // matching how touch-only games already read pointer deltas.
                    val centerX = width / 2f
                    val centerY = height / 2f
                    service?.dragTo(mouseKeyId, centerX + dx, centerY + dy)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    service?.releaseHold(mouseKeyId)
                    true
                }
                else -> false
            }
        }
    }

    private fun vibrateShort() {
        val app = context.applicationContext as? TouchKeyMapperApp ?: return
        if (!app.prefs.getBoolean(TouchKeyMapperApp.KEY_VIBRATE_ON_PRESS, true)) return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(15)
        }
    }
}

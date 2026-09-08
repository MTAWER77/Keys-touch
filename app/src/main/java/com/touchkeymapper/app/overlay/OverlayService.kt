package com.touchkeymapper.app.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.touchkeymapper.app.MainActivity
import com.touchkeymapper.app.R
import com.touchkeymapper.app.TouchKeyMapperApp
import com.touchkeymapper.app.data.Profile

/**
 * Foreground service that owns exactly one WindowManager and lays two
 * layers on top of it: the always-present floating control bubble, and
 * (when a profile is active and controls aren't hidden) the button/
 * joystick/mouse-pad layer for that profile.
 *
 * All views are added with FLAG_NOT_TOUCH_MODAL + per-view touch regions
 * so areas without a control pass touches straight through to the game.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: FloatingBubbleView? = null
    private var controlLayer: ControlLayerView? = null
    private var currentProfile: Profile? = null
    private var controlsVisible = true

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_PROFILE -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                val app = application as TouchKeyMapperApp
                val profile = app.profileRepository.loadAll().find { it.id == profileId }
                if (profile != null) showProfile(profile)
            }
            ACTION_HIDE_CONTROLS -> setControlsVisible(false)
            ACTION_SHOW_CONTROLS -> setControlsVisible(true)
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun addBubble() {
        if (bubbleView != null) return
        val bubble = FloatingBubbleView(this, windowManager) {
            // On tap, bubble opens its own mini menu (handled inside the view)
        }
        bubbleView = bubble
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }
        windowManager.addView(bubble, params)
    }

    private fun showProfile(profile: Profile) {
        currentProfile = profile
        controlLayer?.let { windowManager.removeView(it) }
        val layer = ControlLayerView(this, profile)
        controlLayer = layer
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(layer, params)
        layer.visibility = if (controlsVisible) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        controlLayer?.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        bubbleView?.setHiddenState(!visible)
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun buildNotification(): Notification {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "TouchKey Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the game control overlay running" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("TouchKey Mapper active")
            .setContentText("Overlay controls are running")
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setContentIntent(pendingIntent)
            .addAction(0, "Close Overlay", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        controlLayer?.let { runCatching { windowManager.removeView(it) } }
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        controlLayer = null
        bubbleView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 42
        const val ACTION_SHOW_PROFILE = "com.touchkeymapper.app.SHOW_PROFILE"
        const val ACTION_HIDE_CONTROLS = "com.touchkeymapper.app.HIDE_CONTROLS"
        const val ACTION_SHOW_CONTROLS = "com.touchkeymapper.app.SHOW_CONTROLS"
        const val ACTION_STOP = "com.touchkeymapper.app.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun showProfile(context: Context, profileId: String) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW_PROFILE
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).apply { action = ACTION_STOP })
        }
    }
}

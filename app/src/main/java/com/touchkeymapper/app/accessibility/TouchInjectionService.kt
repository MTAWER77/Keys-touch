package com.touchkeymapper.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * TouchInjectionService
 *
 * This is the ONLY component that talks to a game underneath the overlay,
 * and it only ever does so through the public, documented
 * AccessibilityService.dispatchGesture() API — it simulates a finger
 * touching the glass, exactly as if the user had tapped there directly.
 *
 * It deliberately:
 *  - does NOT set canRetrieveWindowContent, so it cannot read what's on screen
 *  - does NOT use FLAG_REQUEST_FILTER_KEY_EVENTS, so it never intercepts
 *    the hardware/IME key stream
 *  - does NOT attempt any keycode injection into other processes, because
 *    that API (INJECT_EVENTS) simply isn't available to third-party apps
 *    on a non-rooted device
 *
 * Every "key press" from a virtual button therefore becomes a real,
 * OS-level touch event at the button's configured target coordinates.
 */
class TouchInjectionService : AccessibilityService() {

    private val activeStrokes = ConcurrentHashMap<String, GestureDescription.StrokeDescription>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally unused: this service does not inspect screen content.
    }

    override fun onInterrupt() { /* no-op */ }

    /** Simulates a quick tap at (x, y). Used for TAP_TARGET actions. */
    fun tap(x: Float, y: Float, durationMs: Long = 50L) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Begins a "hold" — a touch-down that stays pressed. Because Android's
     * gesture API requires a bounded duration per dispatch, holds are
     * implemented as a chain of short strokes with the `willContinue` flag,
     * re-issued every ~400ms until [releaseHold] is called. This keeps the
     * contact point alive on screen for as long as the user holds the button.
     */
    fun startHold(keyId: String, x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, HOLD_SEGMENT_MS, true)
        activeStrokes[keyId] = stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                continueHoldIfActive(keyId, x, y)
            }
        }, null)
    }

    private fun continueHoldIfActive(keyId: String, x: Float, y: Float) {
        if (!activeStrokes.containsKey(keyId)) return
        val prev = activeStrokes[keyId] ?: return
        val path = Path().apply { moveTo(x, y) }
        val stroke = prev.continueStroke(path, 0, HOLD_SEGMENT_MS, true)
        activeStrokes[keyId] = stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                continueHoldIfActive(keyId, x, y)
            }
        }, null)
    }

    fun releaseHold(keyId: String) {
        activeStrokes.remove(keyId)
    }

    /** Simulates a drag from (startX,startY) to (endX,endY) — e.g. camera flicks. */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 120L) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /** Used by the virtual joystick / mouse pad to stream continuous drag deltas. */
    fun dragTo(keyId: String, x: Float, y: Float) {
        // Reuses the hold machinery: a moving "hold" point is a live drag.
        val prev = activeStrokes[keyId]
        if (prev == null) {
            startHold(keyId, x, y)
        } else {
            continueHoldIfActive(keyId, x, y)
        }
    }

    companion object {
        private const val HOLD_SEGMENT_MS = 400L

        var instance: TouchInjectionService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}

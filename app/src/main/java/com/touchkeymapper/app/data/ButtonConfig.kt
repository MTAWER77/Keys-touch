package com.touchkeymapper.app.data

import java.util.UUID

/**
 * How the button injects its action into the game underneath the overlay.
 *
 * IMPORTANT LIMITATION (by design, not a bug):
 * Android does not allow a normal, non-rooted third-party app to inject
 * synthetic KeyEvents (like a real "W" keypress) into another app's process.
 * The INJECT_EVENTS permission required for that is signature|system only.
 *
 * The legitimate, widely-used alternative (the same one used by shipped
 * apps like gamepad/touch mappers) is: every virtual "key" is really a
 * mapping to a screen coordinate. Pressing the button asks the
 * AccessibilityService to dispatch a real touch gesture (tap / hold / drag)
 * at that coordinate, which the underlying game receives as ordinary touch
 * input — because that's genuinely how the game expects to be played.
 *
 * So "W" doesn't mean "send keycode 51" — it means "tap here, where the
 * game's own on-screen forward control lives, whenever the user holds this
 * button." The button gives the *feel* of a keyboard key even though under
 * the hood it's always a touch gesture.
 */
enum class ActionType {
    TAP_TARGET,      // Single tap at target x/y each time the button is pressed
    HOLD_TARGET,      // Touch-down at target x/y for as long as the button is held
    SWIPE_TARGET      // Drag from (x,y) to (endX,endY) — e.g. for camera flicks
}

enum class InputMode { HOLD, TOGGLE }

enum class ButtonShape { RECTANGLE, ROUNDED, CIRCLE }

data class ButtonConfig(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Button",
    var label: String = "W",              // Text shown on the button face

    // Layout / transform (all in dp relative to the phone-sized editor canvas)
    var x: Float = 100f,
    var y: Float = 100f,
    var width: Float = 70f,
    var height: Float = 70f,
    var rotationDegrees: Float = 0f,

    // Appearance
    var opacity: Float = 0.65f,
    var backgroundColorHex: String = "#2196F3",
    var borderColorHex: String = "#FFFFFF",
    var borderWidthDp: Float = 1.5f,
    var shape: ButtonShape = ButtonShape.ROUNDED,
    var cornerRadiusDp: Float = 12f,
    var textSizeSp: Float = 14f,
    var textColorHex: String = "#FFFFFF",

    // Behavior
    var isVisible: Boolean = true,
    var isLocked: Boolean = false,
    var inputMode: InputMode = InputMode.HOLD,
    var actionType: ActionType = ActionType.HOLD_TARGET,

    // Where this button's action actually lands on the real screen.
    // Defaults to the button's own position, but can be pointed anywhere
    // (useful when the visible button and the touch target should differ,
    // e.g. a "SPACE" button mapped onto the game's own jump icon).
    var targetX: Float = 100f,
    var targetY: Float = 100f,
    var targetEndX: Float = 100f,
    var targetEndY: Float = 100f
) {
    fun deepCopyWithNewId(): ButtonConfig = copy(id = UUID.randomUUID().toString())
}

data class JoystickConfig(
    val id: String = UUID.randomUUID().toString(),
    var x: Float = 90f,
    var y: Float = 600f,
    var diameter: Float = 140f,
    var opacity: Float = 0.55f,
    var deadZone: Float = 0.15f,
    var sensitivity: Float = 1.0f,
    var isVisible: Boolean = true,
    var isLocked: Boolean = false,
    // Screen-space anchor the joystick drags around, e.g. the game's own
    // virtual joystick center, so drag deltas land as real touch drags there.
    var anchorX: Float = 90f,
    var anchorY: Float = 600f
)

data class MouseAreaConfig(
    val id: String = UUID.randomUUID().toString(),
    var x: Float = 220f,
    var y: Float = 400f,
    var width: Float = 260f,
    var height: Float = 260f,
    var opacity: Float = 0.25f,
    var sensitivity: Float = 1.0f,
    var invertX: Boolean = false,
    var invertY: Boolean = false,
    var isVisible: Boolean = true,
    var isLocked: Boolean = false
)

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Profile",
    var buttons: MutableList<ButtonConfig> = mutableListOf(),
    var joysticks: MutableList<JoystickConfig> = mutableListOf(),
    var mouseAreas: MutableList<MouseAreaConfig> = mutableListOf()
) {
    fun deepCopy(): Profile = Profile(
        id = UUID.randomUUID().toString(),
        name = "$name Copy",
        buttons = buttons.map { it.deepCopyWithNewId() }.toMutableList(),
        joysticks = joysticks.map { it.copy(id = UUID.randomUUID().toString()) }.toMutableList(),
        mouseAreas = mouseAreas.map { it.copy(id = UUID.randomUUID().toString()) }.toMutableList()
    )
}

/** Preset labels shown as quick-add suggestions — not a restricted list. */
object PresetButtons {
    val quickLabels = listOf(
        "W", "A", "S", "D", "SPACE", "SHIFT", "CTRL", "E", "F", "R",
        "TAB", "ESC", "LMB", "RMB", "1", "2", "3", "Q"
    )
}

package com.touchkeymapper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.touchkeymapper.app.data.*

private val presetColors = listOf(
    "#2196F3", "#FF5252", "#4CAF50", "#FFC107", "#9C27B0", "#FF9800", "#607D8B", "#FFFFFF"
)

/**
 * Full button settings sheet: name, key/label, mode, opacity, size, shape,
 * colors, text size, target coordinates, visibility and lock state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonEditDialog(
    initial: ButtonConfig,
    onDismiss: () -> Unit,
    onSave: (ButtonConfig) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var config by remember { mutableStateOf(initial) }
    var showKeyPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Button Settings", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = config.name,
                    onValueChange = { config = config.copy(name = it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = config.label,
                    onValueChange = { config = config.copy(label = it) },
                    label = { Text("Label text on button") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                Text("Assign Key / Action", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { showKeyPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(config.label.ifBlank { "Press to assign" })
                }
                Spacer(Modifier.height(14.dp))

                Text("Mode", style = MaterialTheme.typography.labelLarge)
                Row {
                    FilterChip(
                        selected = config.inputMode == InputMode.HOLD,
                        onClick = { config = config.copy(inputMode = InputMode.HOLD) },
                        label = { Text("Hold") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = config.inputMode == InputMode.TOGGLE,
                        onClick = { config = config.copy(inputMode = InputMode.TOGGLE) },
                        label = { Text("Toggle") }
                    )
                }
                Spacer(Modifier.height(14.dp))

                Text("Shape", style = MaterialTheme.typography.labelLarge)
                Row {
                    ButtonShape.values().forEach { shape ->
                        FilterChip(
                            selected = config.shape == shape,
                            onClick = { config = config.copy(shape = shape) },
                            label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                LabeledSlider("Opacity", config.opacity, 0.1f..1f) { config = config.copy(opacity = it) }
                LabeledSlider("Width", config.width, 40f..220f) { config = config.copy(width = it) }
                LabeledSlider("Height", config.height, 40f..220f) { config = config.copy(height = it) }
                LabeledSlider("Rotation", config.rotationDegrees, 0f..359f) { config = config.copy(rotationDegrees = it) }
                LabeledSlider("Text size", config.textSizeSp, 8f..36f) { config = config.copy(textSizeSp = it) }
                LabeledSlider("Border width", config.borderWidthDp, 0f..6f) { config = config.copy(borderWidthDp = it) }

                Spacer(Modifier.height(10.dp))
                Text("Background color", style = MaterialTheme.typography.labelLarge)
                ColorSwatchRow(selected = config.backgroundColorHex) { config = config.copy(backgroundColorHex = it) }

                Spacer(Modifier.height(10.dp))
                Text("Text color", style = MaterialTheme.typography.labelLarge)
                ColorSwatchRow(selected = config.textColorHex) { config = config.copy(textColorHex = it) }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Visible", modifier = Modifier.weight(1f))
                    Switch(checked = config.isVisible, onCheckedChange = { config = config.copy(isVisible = it) })
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Locked", modifier = Modifier.weight(1f))
                    Switch(checked = config.isLocked, onCheckedChange = { config = config.copy(isLocked = it) })
                }

                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(config) }) { Text("Save") }
                }
            }
        }
    }

    if (showKeyPicker) {
        KeyPickerDialog(
            onDismiss = { showKeyPicker = false },
            onPick = { key ->
                config = config.copy(name = key, label = key)
                showKeyPicker = false
            }
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${"%.0f".format(if (label == "Opacity") value * 100 else value)}${if (label == "Opacity") "%" else ""}")
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ColorSwatchRow(selected: String, onPick: (String) -> Unit) {
    LazyRow {
        items(presetColors) { hex ->
            val color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(32.dp)
                    .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                    .then(
                        if (selected == hex) Modifier.border(
                            2.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape
                        ) else Modifier
                    )
                    .clickable { onPick(hex) }
            )
        }
    }
}

private val allKeys = listOf(
    "W", "A", "S", "D", "Space", "Shift", "Ctrl", "Alt", "Tab", "Enter", "Escape",
    "Q", "E", "R", "F", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    "Arrow Up", "Arrow Down", "Arrow Left", "Arrow Right", "Mouse Left", "Mouse Right"
)

@Composable
private fun KeyPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Key") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
                allKeys.forEach { key ->
                    TextButton(onClick = { onPick(key) }, modifier = Modifier.fillMaxWidth()) {
                        Text(key, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

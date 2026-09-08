package com.touchkeymapper.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchkeymapper.app.data.ButtonConfig
import com.touchkeymapper.app.data.ButtonShape
import com.touchkeymapper.app.data.Profile
import com.touchkeymapper.app.ui.components.ButtonEditDialog
import java.util.UUID

/**
 * Visual layout editor: a phone-sized canvas where every button can be
 * dragged, tapped to open full settings, duplicated, or deleted. New
 * buttons are unlimited and unrestricted in what key/action they hold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    profile: Profile,
    onProfileUpdated: (Profile) -> Unit,
    onBack: () -> Unit
) {
    var buttons by remember(profile.id) { mutableStateOf(profile.buttons.toMutableList()) }
    var selectedButtonId by remember { mutableStateOf<String?>(null) }
    var editingButton by remember { mutableStateOf<ButtonConfig?>(null) }

    fun commit(newButtons: MutableList<ButtonConfig>) {
        buttons = newButtons
        onProfileUpdated(profile.copy(buttons = newButtons))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Layout · ${profile.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = {
                        val newButton = ButtonConfig(name = "New", label = "NEW", x = 140f, y = 300f)
                        commit((buttons + newButton).toMutableList())
                        selectedButtonId = newButton.id
                    }) { Icon(Icons.Filled.Add, contentDescription = "Add button") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Drag buttons to reposition. Tap a button to customize it.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // Phone-sized preview canvas
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
                    .aspectRatio(9f / 18f)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .background(Color(0xFF11131A), RoundedCornerShape(24.dp))
            ) {
                // 1dp in editor == 1 layout unit, canvas is a scaled-down phone preview
                buttons.forEach { btn ->
                    EditableButton(
                        config = btn,
                        isSelected = btn.id == selectedButtonId,
                        onSelect = { selectedButtonId = btn.id },
                        onOpenSettings = { editingButton = btn },
                        onDrag = { dx, dy ->
                            if (!btn.isLocked) {
                                val updated = buttons.map {
                                    if (it.id == btn.id) it.copy(
                                        x = (it.x + dx).coerceIn(0f, 340f),
                                        y = (it.y + dy).coerceIn(0f, 680f)
                                    ) else it
                                }.toMutableList()
                                commit(updated)
                            }
                        }
                    )
                }
            }

            if (selectedButtonId != null) {
                val selected = buttons.find { it.id == selectedButtonId }
                if (selected != null) {
                    QuickActionBar(
                        onEdit = { editingButton = selected },
                        onDuplicate = {
                            val copy = selected.deepCopyWithNewId().apply { x += 20f; y += 20f }
                            commit((buttons + copy).toMutableList())
                        },
                        onLockToggle = {
                            commit(buttons.map { if (it.id == selected.id) it.copy(isLocked = !it.isLocked) else it }.toMutableList())
                        },
                        onHideToggle = {
                            commit(buttons.map { if (it.id == selected.id) it.copy(isVisible = !it.isVisible) else it }.toMutableList())
                        },
                        onDelete = {
                            commit(buttons.filterNot { it.id == selected.id }.toMutableList())
                            selectedButtonId = null
                        },
                        isLocked = selected.isLocked,
                        isVisible = selected.isVisible
                    )
                }
            }
        }
    }

    editingButton?.let { current ->
        ButtonEditDialog(
            initial = current,
            onDismiss = { editingButton = null },
            onSave = { updated ->
                commit(buttons.map { if (it.id == updated.id) updated else it }.toMutableList())
                editingButton = null
            },
            onDelete = {
                commit(buttons.filterNot { it.id == current.id }.toMutableList())
                editingButton = null
                selectedButtonId = null
            }
        )
    }
}

@Composable
private fun EditableButton(
    config: ButtonConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onOpenSettings: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val shape = when (config.shape) {
        ButtonShape.RECTANGLE -> RoundedCornerShape(0.dp)
        ButtonShape.ROUNDED -> RoundedCornerShape(config.cornerRadiusDp.dp)
        ButtonShape.CIRCLE -> RoundedCornerShape(50)
    }
    Box(
        modifier = Modifier
            .offset(x = config.x.dp * 0.42f, y = config.y.dp * 0.42f)
            .size(config.width.dp * 0.42f, config.height.dp * 0.42f)
            .background(
                Color(android.graphics.Color.parseColor(config.backgroundColorHex)).copy(alpha = config.opacity),
                shape
            )
            .border(
                if (isSelected) 2.dp else config.borderWidthDp.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else Color(android.graphics.Color.parseColor(config.borderColorHex)),
                shape
            )
            .pointerInput(config.id, config.isLocked) {
                detectTapGestures(onTap = { onSelect(); onOpenSettings() })
            }
            .pointerInput(config.id, config.isLocked) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onSelect()
                    // Convert screen px drag back into the same unit scale used for offset (0.42f factor)
                    onDrag(dragAmount.x / 0.42f / 2.7f, dragAmount.y / 0.42f / 2.7f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (config.isVisible) {
            Text(
                config.label,
                color = Color(android.graphics.Color.parseColor(config.textColorHex)),
                fontSize = (config.textSizeSp * 0.9f).sp,
                textAlign = TextAlign.Center
            )
        } else {
            Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
        }
        if (config.isLocked) {
            Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun QuickActionBar(
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onLockToggle: () -> Unit,
    onHideToggle: () -> Unit,
    onDelete: () -> Unit,
    isLocked: Boolean,
    isVisible: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionIcon(Icons.Filled.Tune, "Edit", onEdit)
        QuickActionIcon(Icons.Filled.ContentCopy, "Duplicate", onDuplicate)
        QuickActionIcon(if (isLocked) Icons.Filled.LockOpen else Icons.Filled.Lock, if (isLocked) "Unlock" else "Lock", onLockToggle)
        QuickActionIcon(if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (isVisible) "Hide" else "Show", onHideToggle)
        QuickActionIcon(Icons.Filled.Delete, "Delete", onDelete, tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun QuickActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint) }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

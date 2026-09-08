package com.touchkeymapper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.touchkeymapper.app.TouchKeyMapperApp
import com.touchkeymapper.app.accessibility.TouchInjectionService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TouchKeyMapperApp
    var vibrateOnPress by remember {
        mutableStateOf(app.prefs.getBoolean(TouchKeyMapperApp.KEY_VIBRATE_ON_PRESS, true))
    }
    var showGrid by remember {
        mutableStateOf(app.prefs.getBoolean(TouchKeyMapperApp.KEY_SHOW_EDIT_GRID, true))
    }
    val overlayGranted = android.provider.Settings.canDrawOverlays(context)
    val accessibilityRunning = TouchInjectionService.isRunning()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            SectionHeader("Permissions")
            PermissionRow(
                title = "Display over other apps",
                subtitle = if (overlayGranted) "Granted — required to draw the control overlay" else "Not granted",
                granted = overlayGranted,
                onClick = {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )
            PermissionRow(
                title = "Accessibility service (optional)",
                subtitle = if (accessibilityRunning)
                    "Enabled — lets button presses simulate real touches on the game"
                else
                    "Off — buttons won't reach the game until this is turned on in system settings",
                granted = accessibilityRunning,
                onClick = onOpenAccessibilitySettings
            )
            Text(
                "Why this is needed: Android doesn't allow apps to inject key " +
                    "presses into other apps. TouchKey Mapper instead simulates a " +
                    "real finger tap at each button's target location using the " +
                    "public Accessibility gesture API — the same mechanism screen " +
                    "readers and switch-access tools use. It never reads what's on " +
                    "your screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(16.dp))
            SectionHeader("Editor & Performance")
            SettingsToggleRow("Vibrate on button press", vibrateOnPress) {
                vibrateOnPress = it
                app.prefs.edit().putBoolean(TouchKeyMapperApp.KEY_VIBRATE_ON_PRESS, it).apply()
            }
            SettingsToggleRow("Show alignment grid in editor", showGrid) {
                showGrid = it
                app.prefs.edit().putBoolean(TouchKeyMapperApp.KEY_SHOW_EDIT_GRID, it).apply()
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader("About")
            Text(
                "TouchKey Mapper only uses officially supported Android APIs: " +
                    "SYSTEM_ALERT_WINDOW for the overlay and AccessibilityService " +
                    "gesture dispatch for input. No root, no exploits, no hidden " +
                    "permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text(if (granted) "ON" else "OFF", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

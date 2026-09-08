package com.touchkeymapper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.touchkeymapper.app.data.Profile
import com.touchkeymapper.app.data.ProfileRepository
import com.touchkeymapper.app.overlay.OverlayService
import com.touchkeymapper.app.ui.screens.*
import com.touchkeymapper.app.ui.theme.TouchKeyMapperTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: ProfileRepository

    // Android 13+ requires POST_NOTIFICATIONS to be requested at runtime,
    // or the required foreground-service notification simply won't show.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as TouchKeyMapperApp).profileRepository

        setContent {
            TouchKeyMapperTheme {
                val navController = rememberNavController()
                var profiles by remember { mutableStateOf(repository.loadAll()) }
                val activePrefsKey = TouchKeyMapperApp.KEY_ACTIVE_PROFILE_ID
                val prefs = (application as TouchKeyMapperApp).prefs
                var activeProfileId by remember {
                    mutableStateOf(prefs.getString(activePrefsKey, profiles.firstOrNull()?.id))
                }

                fun persist() {
                    repository.saveAll(profiles)
                }

                fun setActiveProfile(id: String) {
                    activeProfileId = id
                    prefs.edit().putString(activePrefsKey, id).apply()
                }

                LaunchedEffect(Unit) {
                    val dest = intent?.getStringExtra(EXTRA_DESTINATION)
                    if (dest == "profiles") navController.navigate("profiles")
                    if (dest == "settings") navController.navigate("settings")
                    if (dest == "edit_layout") {
                        activeProfileId?.let { navController.navigate("editor/$it") }
                    }
                }

                AppNavHost(
                    navController = navController,
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    onProfilesChanged = { updated -> profiles = updated; persist() },
                    onSetActiveProfile = ::setActiveProfile,
                    onEnableOverlay = { requestOverlayPermissionThenStart() },
                    onStopOverlay = { OverlayService.stop(this@MainActivity) },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() }
                )
            }
        }
    }

    private fun requestOverlayPermissionThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            ensureNotificationPermission()
            OverlayService.start(this)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    profiles: List<Profile>,
    activeProfileId: String?,
    onProfilesChanged: (List<Profile>) -> Unit,
    onSetActiveProfile: (String) -> Unit,
    onEnableOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val active = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()
            MainScreen(
                activeProfileName = active?.name ?: "None",
                onEnableOverlay = onEnableOverlay,
                onStopOverlay = onStopOverlay,
                onEditLayout = { active?.let { navController.navigate("editor/${it.id}") } },
                onOpenProfiles = { navController.navigate("profiles") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("profiles") {
            ProfilesScreen(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelect = { id ->
                    onSetActiveProfile(id)
                    com.touchkeymapper.app.overlay.OverlayService.showProfile(
                        navController.context, id
                    )
                },
                onCreate = { name ->
                    onProfilesChanged(profiles + Profile(name = name))
                },
                onRename = { id, newName ->
                    onProfilesChanged(profiles.map { if (it.id == id) it.copy(name = newName) else it })
                },
                onDuplicate = { id ->
                    val original = profiles.find { it.id == id }
                    if (original != null) onProfilesChanged(profiles + original.deepCopy())
                },
                onDelete = { id ->
                    onProfilesChanged(profiles.filterNot { it.id == id })
                },
                onEdit = { id -> navController.navigate("editor/$id") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("editor/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
            val profile = profiles.find { it.id == profileId }
            if (profile != null) {
                LayoutEditorScreen(
                    profile = profile,
                    onProfileUpdated = { updated ->
                        onProfilesChanged(profiles.map { if (it.id == updated.id) updated else it })
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("settings") {
            SettingsScreen(
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

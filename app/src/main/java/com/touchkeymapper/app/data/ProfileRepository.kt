package com.touchkeymapper.app.data

import android.content.Context
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Persists all profiles as a single local JSON file in app-private storage,
 * plus supports exporting/importing any single profile as standalone JSON
 * that matches the format the user specified.
 */
class ProfileRepository(context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val storeFile = File(context.filesDir, "profiles.json")
    private val appContext = context.applicationContext

    fun loadAll(): MutableList<Profile> {
        if (!storeFile.exists()) {
            val defaults = defaultProfiles()
            saveAll(defaults)
            return defaults
        }
        return try {
            val json = storeFile.readText()
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                MutableList::class.java, Profile::class.java
            ).type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(profiles: List<Profile>) {
        storeFile.writeText(gson.toJson(profiles))
    }

    fun exportProfileToJson(profile: Profile): String = gson.toJson(profile)

    fun importProfileFromJson(json: String): Profile? = try {
        gson.fromJson(json, Profile::class.java)?.let {
            // Always mint a fresh id on import so it never collides with an existing profile
            it.copy(id = java.util.UUID.randomUUID().toString())
        }
    } catch (e: Exception) {
        null
    }

    /** Writes an exported profile to app-private exports dir; returns the File. */
    fun writeExportFile(profile: Profile): File {
        val exportsDir = File(appContext.filesDir, "exports").apply { mkdirs() }
        val safeName = profile.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(exportsDir, "${safeName}_${profile.id.take(8)}.json")
        file.writeText(exportProfileToJson(profile))
        return file
    }

    private fun defaultProfiles(): MutableList<Profile> = mutableListOf(
        Profile(
            name = "FPS",
            buttons = mutableListOf(
                ButtonConfig(name = "W", label = "W", x = 140f, y = 520f, targetX = 140f, targetY = 520f),
                ButtonConfig(name = "A", label = "A", x = 70f, y = 590f, targetX = 70f, targetY = 590f),
                ButtonConfig(name = "S", label = "S", x = 140f, y = 590f, targetX = 140f, targetY = 590f),
                ButtonConfig(name = "D", label = "D", x = 210f, y = 590f, targetX = 210f, targetY = 590f),
                ButtonConfig(name = "SPACE", label = "SPC", x = 320f, y = 620f, width = 100f, targetX = 320f, targetY = 620f),
                ButtonConfig(name = "ESC", label = "ESC", x = 320f, y = 60f, width = 60f, height = 40f, targetX = 320f, targetY = 60f)
            ),
            joysticks = mutableListOf(),
            mouseAreas = mutableListOf(MouseAreaConfig())
        ),
        Profile(name = "Racing"),
        Profile(name = "Platformer"),
        Profile(name = "Minecraft"),
        Profile(name = "Custom")
    )
}

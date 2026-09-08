package com.touchkeymapper.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.touchkeymapper.app.data.ProfileRepository

class TouchKeyMapperApp : Application() {

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var prefs: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        profileRepository = ProfileRepository(this)
        prefs = getSharedPreferences("touchkey_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        const val KEY_GLOBAL_OPACITY = "global_opacity"
        const val KEY_VIBRATE_ON_PRESS = "vibrate_on_press"
        const val KEY_SHOW_EDIT_GRID = "show_edit_grid"
    }
}

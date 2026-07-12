package com.rickd.mushclient.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rickd.mushclient.data.AppPreferences
import com.rickd.mushclient.data.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences.get(application)
    val themeMode: StateFlow<ThemeMode> = prefs.themeMode

    fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)
}

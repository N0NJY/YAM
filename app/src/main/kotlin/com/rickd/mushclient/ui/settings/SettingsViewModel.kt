package com.rickd.mushclient.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rickd.mushclient.data.AppPreferences
import com.rickd.mushclient.data.FontChoice
import com.rickd.mushclient.data.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences.get(application)
    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
    val autoReconnect: StateFlow<Boolean> = prefs.autoReconnect
    val terminalFont: StateFlow<FontChoice> = prefs.terminalFont
    val terminalFontSize: StateFlow<Int> = prefs.terminalFontSize
    val inputFont: StateFlow<FontChoice> = prefs.inputFont
    val inputFontSize: StateFlow<Int> = prefs.inputFontSize

    fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)
    fun setAutoReconnect(enabled: Boolean) = prefs.setAutoReconnect(enabled)
    fun setTerminalFont(font: FontChoice) = prefs.setTerminalFont(font)
    fun setTerminalFontSize(sizeSp: Int) = prefs.setTerminalFontSize(sizeSp)
    fun setInputFont(font: FontChoice) = prefs.setInputFont(font)
    fun setInputFontSize(sizeSp: Int) = prefs.setInputFontSize(sizeSp)
}

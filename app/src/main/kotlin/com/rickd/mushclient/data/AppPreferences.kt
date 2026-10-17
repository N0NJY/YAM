package com.rickd.mushclient.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SharedPreferences-backed app settings, exposed as StateFlows kept in sync on write.
 * A handful of small settings don't warrant pulling in DataStore; add more properties
 * here as the settings page grows.
 */
class AppPreferences private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private fun readThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    private val _autoReconnect = MutableStateFlow(prefs.getBoolean(KEY_AUTO_RECONNECT, true))
    val autoReconnect: StateFlow<Boolean> = _autoReconnect

    fun setAutoReconnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
        _autoReconnect.value = enabled
    }

    // Fonts: separate family and size for the scrollback ("terminal") and the input line.
    private val _terminalFont = MutableStateFlow(
        FontChoice.fromName(prefs.getString(KEY_TERMINAL_FONT, null), FontChoice.MONOSPACE),
    )
    val terminalFont: StateFlow<FontChoice> = _terminalFont

    fun setTerminalFont(font: FontChoice) {
        prefs.edit().putString(KEY_TERMINAL_FONT, font.name).apply()
        _terminalFont.value = font
    }

    private val _terminalFontSize = MutableStateFlow(
        clampFontSize(prefs.getInt(KEY_TERMINAL_FONT_SIZE, DEFAULT_TERMINAL_FONT_SIZE_SP)),
    )
    val terminalFontSize: StateFlow<Int> = _terminalFontSize

    fun setTerminalFontSize(sizeSp: Int) {
        val size = clampFontSize(sizeSp)
        prefs.edit().putInt(KEY_TERMINAL_FONT_SIZE, size).apply()
        _terminalFontSize.value = size
    }

    private val _inputFont = MutableStateFlow(
        FontChoice.fromName(prefs.getString(KEY_INPUT_FONT, null), FontChoice.SANS_SERIF),
    )
    val inputFont: StateFlow<FontChoice> = _inputFont

    fun setInputFont(font: FontChoice) {
        prefs.edit().putString(KEY_INPUT_FONT, font.name).apply()
        _inputFont.value = font
    }

    private val _inputFontSize = MutableStateFlow(
        clampFontSize(prefs.getInt(KEY_INPUT_FONT_SIZE, DEFAULT_INPUT_FONT_SIZE_SP)),
    )
    val inputFontSize: StateFlow<Int> = _inputFontSize

    fun setInputFontSize(sizeSp: Int) {
        val size = clampFontSize(sizeSp)
        prefs.edit().putInt(KEY_INPUT_FONT_SIZE, size).apply()
        _inputFontSize.value = size
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_TERMINAL_FONT = "terminal_font"
        private const val KEY_TERMINAL_FONT_SIZE = "terminal_font_size"
        private const val KEY_INPUT_FONT = "input_font"
        private const val KEY_INPUT_FONT_SIZE = "input_font_size"

        @Volatile private var instance: AppPreferences? = null

        fun get(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context).also { instance = it }
            }
    }
}

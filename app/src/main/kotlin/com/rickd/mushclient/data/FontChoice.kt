package com.rickd.mushclient.data

/**
 * The font families Android exposes to an app without bundling font files. Monospace is the
 * terminal default because MUD banners, tables and ASCII art assume fixed-width columns;
 * the proportional choices exist mainly for the input line.
 */
enum class FontChoice(val label: String) {
    MONOSPACE("Monospace"),
    SANS_SERIF("Sans Serif"),
    SERIF("Serif");

    companion object {
        /** An unknown or missing stored name falls back to [default] instead of crashing. */
        fun fromName(name: String?, default: FontChoice): FontChoice =
            entries.firstOrNull { it.name == name } ?: default
    }
}

const val MIN_FONT_SIZE_SP = 8
const val MAX_FONT_SIZE_SP = 32
const val DEFAULT_TERMINAL_FONT_SIZE_SP = 16
const val DEFAULT_INPUT_FONT_SIZE_SP = 16

fun clampFontSize(size: Int): Int = size.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)

package com.rickd.mushclient.ansi

import androidx.compose.ui.graphics.Color

/**
 * xterm 256-color palette: 0-15 are the standard/bright 16 colors MUD servers actually
 * use in practice, 16-231 are the 6x6x6 color cube, 232-255 are the grayscale ramp —
 * present for completeness since a handful of MUSH color scripts emit 38;5;n codes.
 */
object AnsiPalette {

    private val base16 = longArrayOf(
        0xFF000000, 0xFFCD0000, 0xFF00CD00, 0xFFCDCD00, 0xFF0000EE, 0xFFCD00CD, 0xFF00CDCD, 0xFFE5E5E5,
        0xFF7F7F7F, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF5C5CFF, 0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF,
    )

    private val cubeSteps = intArrayOf(0, 95, 135, 175, 215, 255)

    fun color(index: Int): Color = when {
        index < 0 -> Color.Unspecified
        index < 16 -> Color(base16[index])
        index < 232 -> {
            val i = index - 16
            Color(cubeSteps[i / 36], cubeSteps[(i / 6) % 6], cubeSteps[i % 6])
        }
        index < 256 -> {
            val level = 8 + (index - 232) * 10
            Color(level, level, level)
        }
        else -> Color.Unspecified
    }
}

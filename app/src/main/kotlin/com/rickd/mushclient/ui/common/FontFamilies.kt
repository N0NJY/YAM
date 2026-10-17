package com.rickd.mushclient.ui.common

import androidx.compose.ui.text.font.FontFamily
import com.rickd.mushclient.data.FontChoice

fun FontChoice.toFontFamily(): FontFamily = when (this) {
    FontChoice.MONOSPACE -> FontFamily.Monospace
    FontChoice.SANS_SERIF -> FontFamily.SansSerif
    FontChoice.SERIF -> FontFamily.Serif
}

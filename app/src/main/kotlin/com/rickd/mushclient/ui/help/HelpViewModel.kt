package com.rickd.mushclient.ui.help

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HelpViewModel(application: Application) : AndroidViewModel(application) {

    val sections: List<HelpSection> = parseHelp(
        application.assets.open("help.md").bufferedReader().use { it.readText() },
    )

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    fun jumpTo(index: Int) {
        _currentIndex.value = index.coerceIn(sections.indices)
    }

    fun next() {
        if (_currentIndex.value < sections.lastIndex) _currentIndex.value += 1
    }

    fun previous() {
        if (_currentIndex.value > 0) _currentIndex.value -= 1
    }
}

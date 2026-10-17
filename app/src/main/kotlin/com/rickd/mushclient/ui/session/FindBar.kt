package com.rickd.mushclient.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** One hit: [start] (inclusive) to [end] (exclusive) within the text of line [line]. */
data class FindMatch(val line: Int, val start: Int, val end: Int)

/**
 * Plain-text (not regex) search over [lines], case-insensitive unless [caseSensitive]. Matches
 * within a line don't overlap. An empty query matches nothing. Results are in scrollback order.
 */
fun findMatches(lines: List<String>, query: String, caseSensitive: Boolean): List<FindMatch> {
    if (query.isEmpty()) return emptyList()
    val out = mutableListOf<FindMatch>()
    lines.forEachIndexed { index, text ->
        var from = 0
        while (true) {
            val at = text.indexOf(query, from, ignoreCase = !caseSensitive)
            if (at < 0) break
            out += FindMatch(index, at, at + query.length)
            from = at + query.length
        }
    }
    return out
}

/** Moves a match cursor by [delta], wrapping at both ends; -1 when there is nothing to step to. */
fun stepIndex(current: Int, size: Int, delta: Int): Int = when {
    size <= 0 -> -1
    current < 0 -> if (delta > 0) 0 else size - 1
    else -> Math.floorMod(current + delta, size)
}

private val MATCH_BACKGROUND = Color(0xFF7A6A00)
private val CURRENT_BACKGROUND = Color(0xFFFFA500)

/**
 * Overlays highlight spans for [matches] (all of one line); [current], if it is on this line,
 * gets the stronger color. Purely additive: the text, ANSI styling and links underneath stay.
 */
fun AnnotatedString.withFindHighlights(matches: List<FindMatch>?, current: FindMatch?): AnnotatedString {
    if (matches.isNullOrEmpty()) return this
    return buildAnnotatedString {
        append(this@withFindHighlights)
        for (m in matches) {
            val end = m.end.coerceAtMost(length)
            if (m.start >= end) continue
            addStyle(SpanStyle(background = MATCH_BACKGROUND), m.start, end)
        }
        if (current != null) {
            val end = current.end.coerceAtMost(length)
            if (current.start < end) {
                addStyle(SpanStyle(background = CURRENT_BACKGROUND, color = Color.Black), current.start, end)
            }
        }
    }
}

/** Stateless find bar: all state lives with the caller so it can survive tab switches. */
@Composable
fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    caseSensitive: Boolean,
    onToggleCase: () -> Unit,
    matchCount: Int,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Find in scrollback") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        FilterChip(selected = caseSensitive, onClick = onToggleCase, label = { Text("Aa") })
        Text(
            text = when {
                query.isEmpty() -> ""
                matchCount == 0 -> "No matches"
                else -> "${currentIndex + 1} / $matchCount"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onPrevious, enabled = matchCount > 0) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
        }
        IconButton(onClick = onNext, enabled = matchCount > 0) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close find", modifier = Modifier.size(20.dp))
        }
    }
}

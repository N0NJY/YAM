package com.rickd.mushclient.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** True if [text] contains a line break, i.e. a paste (the Send key never inserts one). */
fun hasLineBreak(text: String): Boolean = text.any { it == '\n' || it == '\r' }

/**
 * Splits pasted text into the lines to send. Line endings are normalized (CRLF, CR, LF), blank
 * lines are skipped — YAM never sends an empty line — and a trailing line break, routine when
 * copying a block from elsewhere, does not count as an extra line.
 */
fun splitPastedLines(text: String): List<String> =
    text.replace("\r\n", "\n").replace('\r', '\n').split('\n').filter { it.isNotBlank() }

/** Joins a paste that turned out to be a single line back into one line of box text. */
fun collapseToSingleLine(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n').split('\n').filter { it.isNotBlank() }.joinToString(" ")

private const val PREVIEW_LINES = 10

/**
 * A paste of several lines is shown here first rather than sent at once, so a stray paste of a
 * long block can't fire a burst of commands before it has been looked at.
 */
@Composable
fun PasteConfirmDialog(lines: List<String>, onSend: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send ${lines.size} lines?") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                lines.take(PREVIEW_LINES).forEach { line ->
                    Text(text = line, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (lines.size > PREVIEW_LINES) {
                    Text(
                        text = "…and ${lines.size - PREVIEW_LINES} more",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Each line is sent in order, as if you had typed it and pressed Send.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onSend) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

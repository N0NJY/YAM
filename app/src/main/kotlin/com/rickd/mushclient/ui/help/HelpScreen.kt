package com.rickd.mushclient.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.ui.common.SettingsIconButton

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HelpViewModel = viewModel(),
) {
    val sections = viewModel.sections
    val currentIndex by viewModel.currentIndex.collectAsState()
    val section = sections.getOrNull(currentIndex)

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("< Back") }
                Text("Help", style = MaterialTheme.typography.titleLarge)
            }
            SettingsIconButton(onClick = onOpenSettings)
        }

        // Jump to any section directly, rather than only being able to page through in order.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sections.size) { index ->
                FilterChip(
                    selected = index == currentIndex,
                    onClick = { viewModel.jumpTo(index) },
                    label = { Text(sections[index].title) },
                )
            }
        }

        if (section != null) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Text(text = section.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(section.lines) { line -> HelpLineView(line) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(onClick = viewModel::previous, enabled = currentIndex > 0) { Text("Previous") }
            Button(onClick = viewModel::next, enabled = currentIndex < sections.lastIndex) { Text("Next") }
        }
    }
}

@Composable
private fun HelpLineView(line: HelpLine) {
    when (line) {
        is HelpLine.SubHeading -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = inlineMarkdown(line.text), style = MaterialTheme.typography.titleMedium)
        }
        is HelpLine.TableRow -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(text = inlineMarkdown(line.left), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(text = inlineMarkdown(line.right), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
        }
        is HelpLine.Bullet -> {
            val bulletText = buildAnnotatedString {
                append("• ")
                append(inlineMarkdown(line.text))
            }
            Text(text = bulletText, style = MaterialTheme.typography.bodyMedium)
        }
        is HelpLine.Paragraph -> Text(text = inlineMarkdown(line.text), style = MaterialTheme.typography.bodyMedium)
        HelpLine.Blank -> Spacer(modifier = Modifier.height(8.dp))
    }
}

// Help.md only ever uses **bold** and *italic* inline emphasis — a small hand-rolled splitter
// covers that without pulling in a markdown library.
private val INLINE_STYLE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in INLINE_STYLE.findAll(text)) {
        append(text.substring(last, match.range.first))
        val (bold, italic, code) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            else -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

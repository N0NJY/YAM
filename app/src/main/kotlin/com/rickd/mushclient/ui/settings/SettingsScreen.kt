package com.rickd.mushclient.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rickd.mushclient.data.FontChoice
import com.rickd.mushclient.data.MAX_FONT_SIZE_SP
import com.rickd.mushclient.data.MIN_FONT_SIZE_SP
import com.rickd.mushclient.ui.common.toFontFamily
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.data.ThemeMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenWorlds: () -> Unit,
    onOpenPlayers: () -> Unit,
    onOpenTriggers: () -> Unit,
    onOpenSlashCommands: () -> Unit,
    onOpenHelp: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val autoReconnect by viewModel.autoReconnect.collectAsState()
    val terminalFont by viewModel.terminalFont.collectAsState()
    val terminalFontSize by viewModel.terminalFontSize.collectAsState()
    val inputFont by viewModel.inputFont.collectAsState()
    val inputFontSize by viewModel.inputFontSize.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Text("Theme", style = MaterialTheme.typography.titleMedium)
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setThemeMode(mode) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == themeMode, onClick = { viewModel.setThemeMode(mode) })
                Text(mode.label())
            }
        }

        Text("Fonts", style = MaterialTheme.typography.titleMedium)
        FontSetting(
            title = "Terminal font",
            subtitle = "The scrollback. Monospace keeps banners and tables lined up.",
            font = terminalFont,
            sizeSp = terminalFontSize,
            onFont = viewModel::setTerminalFont,
            onSize = viewModel::setTerminalFontSize,
        )
        FontSetting(
            title = "Input font",
            subtitle = "The command line you type into.",
            font = inputFont,
            sizeSp = inputFontSize,
            onFont = viewModel::setInputFont,
            onSize = viewModel::setInputFontSize,
        )

        Text("Connection", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAutoReconnect(!autoReconnect) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Auto-reconnect")
                Text(
                    text = "Automatically reconnect after a dropped connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoReconnect, onCheckedChange = viewModel::setAutoReconnect)
        }

        Text("Manage", style = MaterialTheme.typography.titleMedium)
        ManageRow(
            title = "Worlds & Characters",
            subtitle = "Add, edit, or delete saved worlds and their characters",
            onClick = onOpenWorlds,
        )
        ManageRow(
            title = "Characters",
            subtitle = "Every saved name and password, across all worlds, in one list",
            onClick = onOpenPlayers,
        )
        ManageRow(
            title = "Triggers",
            subtitle = "React to incoming text: recolor, hide, auto-reply, or fill the input box",
            onClick = onOpenTriggers,
        )
        ManageRow(
            title = "Slash Commands",
            subtitle = "Define /name shortcuts that expand into a full command when typed",
            onClick = onOpenSlashCommands,
        )
        ManageRow(
            title = "Help",
            subtitle = "How to use YAM, section by section",
            onClick = onOpenHelp,
        )
    }
}

@Composable
private fun FontSetting(
    title: String,
    subtitle: String,
    font: FontChoice,
    sizeSp: Int,
    onFont: (FontChoice) -> Unit,
    onSize: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontChoice.entries.forEach { choice ->
                FilterChip(
                    selected = choice == font,
                    onClick = { onFont(choice) },
                    label = { Text(choice.label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Slider(
                value = sizeSp.toFloat(),
                onValueChange = { onSize(it.roundToInt()) },
                valueRange = MIN_FONT_SIZE_SP.toFloat()..MAX_FONT_SIZE_SP.toFloat(),
                steps = MAX_FONT_SIZE_SP - MIN_FONT_SIZE_SP - 1,
                modifier = Modifier.weight(1f),
            )
            Text("$sizeSp sp")
        }
        // A live sample, so the choice is visible before leaving Settings.
        Text(
            text = "The quick brown fox jumps over 0123456789",
            fontFamily = font.toFontFamily(),
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 1.35f).sp,
        )
    }
}

@Composable
private fun ManageRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

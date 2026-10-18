package com.rickd.mushclient.ui.worlds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.data.World
import com.rickd.mushclient.data.WorldGroup
import com.rickd.mushclient.ui.common.SettingsIconButton

/** The categories on the left, like MushTato's World Properties; later batches add more. */
private enum class PropertiesSection(val label: String) {
    BASIC("Basic"),
    CONNECTION("Connection"),
}

/**
 * Per-world settings that don't belong in the quick New/Edit World dialog. Edits are held in
 * local state and only written when Save is tapped; Back or Cancel discards them.
 */
@Composable
fun WorldPropertiesScreen(
    worldId: Long,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: WorldPropertiesViewModel = viewModel(),
) {
    LaunchedEffect(worldId) { viewModel.load(worldId) }
    val world by viewModel.world.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val groups by viewModel.groups.collectAsState()

    val current = world
    when {
        current != null -> PropertiesForm(
            world = current,
            groups = groups,
            onSave = { updated -> viewModel.save(updated, onSaved = onBack) },
            onCancel = onBack,
            onOpenSettings = onOpenSettings,
        )
        loaded -> Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text("That world no longer exists.")
        }
        else -> Unit
    }
}

@Composable
private fun PropertiesForm(
    world: World,
    groups: List<WorldGroup>,
    onSave: (World) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var section by remember { mutableStateOf(PropertiesSection.BASIC) }
    var name by remember(world.id) { mutableStateOf(world.name) }
    var host by remember(world.id) { mutableStateOf(world.host) }
    var port by remember(world.id) { mutableStateOf(world.port.toString()) }
    var groupId by remember(world.id) { mutableStateOf(world.groupId) }
    var nopKeepalive by remember(world.id) { mutableStateOf(world.nopKeepalive) }
    var useSsl by remember(world.id) { mutableStateOf(world.useSsl) }
    var host2 by remember(world.id) { mutableStateOf(world.host2) }
    var port2 by remember(world.id) { mutableStateOf(if (world.port2 > 0) world.port2.toString() else "") }
    var useSsl2 by remember(world.id) { mutableStateOf(world.useSsl2) }
    var proxyHost by remember(world.id) { mutableStateOf(world.proxyHost) }
    var proxyPort by remember(world.id) { mutableStateOf(if (world.proxyPort > 0) world.proxyPort.toString() else "") }

    val error = validateWorldBasics(name, host, port) ?: validateFallback(host2, port2) ?: validateProxy(proxyHost, proxyPort)

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("< Back") }
                Text("Properties: ${world.name}", style = MaterialTheme.typography.titleLarge)
            }
            SettingsIconButton(onClick = onOpenSettings)
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.width(200.dp).fillMaxHeight()) {
                PropertiesSection.entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (entry == section) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            )
                            .clickable { section = entry }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                    ) { Text(entry.label, style = MaterialTheme.typography.titleMedium) }
                }
            }
            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (section) {
                    PropertiesSection.BASIC -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        GroupSelector(groups = groups, selectedGroupId = groupId, onSelect = { groupId = it })
                    }
                    PropertiesSection.CONNECTION -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { useSsl = !useSsl },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text("Use SSL/TLS")
                                Text(
                                    text = "Encrypt the connection. Use the port your server offers for SSL " +
                                        "(it is usually a different port from plain telnet). The server's " +
                                        "certificate is remembered the first time you connect; if it later " +
                                        "changes, YAM refuses to connect until you type /ssl-forget host:port.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = useSsl, onCheckedChange = { useSsl = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { nopKeepalive = !nopKeepalive },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text("Telnet NOP keepalive")
                                Text(
                                    text = "Send an invisible telnet \"no operation\" every 60 seconds. " +
                                        "Helps with servers or routers that drop idle connections.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = nopKeepalive, onCheckedChange = { nopKeepalive = it })
                        }
                        Text("Fallback address", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Optional. If the main address can't be reached, YAM tries this one. It is " +
                                "tried only after the main address fails, on every connect, and needs both a " +
                                "host and a port. It applies when you connect to this world's own address.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = host2,
                            onValueChange = { host2 = it },
                            label = { Text("Fallback host") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = port2,
                            onValueChange = { port2 = it },
                            label = { Text("Fallback port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { useSsl2 = !useSsl2 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Use SSL/TLS for the fallback")
                            Switch(checked = useSsl2, onCheckedChange = { useSsl2 = it })
                        }
                        Text("Proxy (SOCKS4)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Optional. Relay this world's connections through a SOCKS4 or SOCKS4a proxy " +
                                "(for example a Tor or SSH-tunnel proxy). The proxy looks up host names itself, " +
                                "and SSL, if used, still runs end to end with the server. Needs both a host and a port.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            label = { Text("Proxy host") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it },
                            label = { Text("Proxy port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "YAM also always uses TCP keepalive, so a connection that dies " +
                                "silently is noticed within about a minute.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onSave(
                        world.copy(
                            name = name.trim(),
                            host = host.trim(),
                            port = port.trim().toInt(),
                            groupId = groupId,
                            nopKeepalive = nopKeepalive,
                            useSsl = useSsl,
                            host2 = host2.trim(),
                            port2 = port2.trim().toIntOrNull() ?: 0,
                            useSsl2 = useSsl2,
                            proxyHost = proxyHost.trim(),
                            proxyPort = proxyPort.trim().toIntOrNull() ?: 0,
                        ),
                    )
                },
                enabled = error == null,
            ) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

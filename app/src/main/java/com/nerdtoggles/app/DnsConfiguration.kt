package com.nerdtoggles.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun DnsConfiguration(state: DnsState, access: Access?, applyHost: (String) -> DnsResult, close: () -> Unit) {
    var input by remember(state) { mutableStateOf(state.host?.takeIf(::validDnsHost).orEmpty()) }
    var message by remember { mutableStateOf("") }
    val reading = state.reading()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextButton(onClick = close) { Text("Back to controls") }
            Text("Private DNS", style = MaterialTheme.typography.headlineSmall)
            Text("Current setting: ${reading.value}")
            reading.reason?.let { Text(it) }
            Text("Enter the DNS-over-TLS hostname supplied by your provider, or choose a preset.")
            OutlinedTextField(input, { input = it; message = "" }, Modifier.fillMaxWidth(),
                label = { Text("Provider hostname") }, singleLine = true,
                isError = input.isNotEmpty() && !validDnsHost(input))
            TextButton(onClick = { input = "one.one.one.one"; message = "" }) { Text("Cloudflare · Standard resolver") }
            TextButton(onClick = { input = "dns.google"; message = "" }) { Text("Google · Public DNS") }
            TextButton(onClick = { input = "dns.adguard-dns.com"; message = "" }) { Text("AdGuard · Ad and tracker filtering") }
            if (access != Access.Granted) Text("Check secure settings access in Setup.")
            Button(onClick = { message = applyHost(input).message },
                enabled = access == Access.Granted && state.error == null && validDnsHost(input)) {
                Text("Use custom DNS")
            }
            if (message.isNotEmpty()) Text(message)
        }
    }
}

@Composable
internal fun AddTile(label: Int, icon: Int, service: Class<out TileService>, enabled: Boolean) {
    val context = LocalContext.current
    val title = context.getString(label)
    var message by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp)) {
        TextButton(enabled = enabled, onClick = {
            val manager = context.getSystemService(StatusBarManager::class.java)
            message = "Waiting for Android…"
            try {
                if (manager == null) {
                    message = "System add request unavailable."
                } else {
                    manager.requestAddTileService(ComponentName(context, service),
                        title, Icon.createWithResource(context, icon),
                        context.mainExecutor) { result ->
                        message = when (result) {
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Android added the tile."
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Android reports the tile is already added."
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Tile was not added."
                            else -> "Android could not complete the request."
                        }
                    }
                }
            } catch (_: RuntimeException) {
                message = "System add request unavailable."
            }
        }) { Text("Add to Quick Settings") }
        if (message.isNotEmpty()) Text(message)
        Text("Manual placement: Quick Settings → Edit → $title", style = MaterialTheme.typography.bodySmall)
    }
}

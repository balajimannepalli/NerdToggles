package com.nerdtoggles.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

@Composable
internal fun SecureConfiguration(feature: Feature, state: FeatureState?, refresh: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.controlPrefs()
    var message by rememberSaveable(feature) { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextButton(onClick = close) { Text("Back to controls") }
            Text(feature.title, style = MaterialTheme.typography.headlineSmall)
            Text(state?.reading?.value ?: "Unknown")
            if (feature == Feature.AnimationScale) {
                var input by rememberSaveable { mutableStateOf(prefs.getString("cycle", defaultScales).orEmpty()) }
                val parsed = scaleCycle(input)
                Text("Choose distinct scales from 0, 0.5, 1, 1.5 and 2 in cycle order. Mixed or unlisted scales start at the first entry.")
                OutlinedTextField(input, { input = it }, label = { Text("Cycle order") }, isError = parsed == null)
                Button(enabled = parsed != null, onClick = {
                    prefs.edit { putString("cycle", input) }
                    close()
                }) { Text("Save cycle") }
            } else {
                var consent by remember { mutableStateOf(prefs.getBoolean("allowDisable", false)) }
                Text("Disabling Developer Options may affect debugging or other developer settings on this device. This app changes only the master setting.")
                TextButton(onClick = {
                    consent = !consent
                    prefs.edit { putBoolean("allowDisable", consent) }
                }) { Text(if (consent) "Withdraw permission to disable" else "Allow disabling Developer Options") }
                val current = when (state?.reading?.value) { "On" -> true; "Off", UNVERIFIED_OFF -> false; else -> null }
                Text("Perform a change here to verify write access before using the tile.")
                Button(enabled = current != null && state?.access == Access.Granted && (current == false || consent),
                    onClick = {
                        val result = context.developerOptions().change(requireNotNull(current),
                            consent && prefs.getBoolean("allowDisable", false))
                        context.recordDeveloperVerification(result == null)
                        message = result ?: "Master setting changed and verified."
                        refresh()
                    }) { Text(if (current == true) "Disable and verify" else "Enable and verify") }
                if (message.isNotEmpty()) Text(message)
            }
        }
    }
}

@Composable
internal fun NetworkShortcut() {
    val context = LocalContext.current
    var manual by remember { mutableStateOf(false) }
    TextButton(onClick = {
        val intent = listOf(Settings.ACTION_NETWORK_OPERATOR_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
            .map(::Intent).firstOrNull { it.resolveActivity(context.packageManager) != null }
        manual = if (intent == null) true else try {
            context.startActivity(intent)
            false
        } catch (_: ActivityNotFoundException) { true } catch (_: SecurityException) { true }
    }) { Text("Open mobile network settings") }
    if (manual) Text("Open Settings → Mobile network → SIM → Preferred network type.")
}

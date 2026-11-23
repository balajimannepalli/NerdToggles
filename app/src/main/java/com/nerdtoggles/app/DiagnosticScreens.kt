package com.nerdtoggles.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private fun FeatureState.controlLabel() = when (controlStatus) {
    Status.PermissionNeeded -> "Write permission needed · Device support untested"
    Status.Unsupported -> "Direct control unsupported"
    else -> "Device support untested"
}

@Composable
private fun InfoRow(title: String, value: String, icon: Int? = null) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            if (icon != null) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
}

@Composable
fun Device(states: List<FeatureState>) {
    SectionTitle("About this device")
    InfoRow("Manufacturer", Build.MANUFACTURER)
    InfoRow("Model", Build.MODEL)
    InfoRow("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    InfoRow("Firmware build", Build.DISPLAY)
    SectionTitle("Compatibility")
    states.forEach { state ->
        val diagnostic = when (state.readStatus) {
            Status.Available -> "Readback available"
            Status.Unsupported -> "Readback unavailable"
            else -> "Readback incomplete"
        }
        InfoRow(state.feature.title, "$diagnostic · ${state.controlLabel()}")
        if (state.feature == Feature.Network) Text("A 5G preference can coexist with a 4G connection. The vivo shell diagnostics do not establish app access to the modem.", Modifier.padding(16.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun Setup(permissions: Permissions, refresh: () -> Unit) {
    val context = LocalContext.current
    val command = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    var showManualPath by remember { mutableStateOf(false) }
    var secureOpen by rememberSaveable(permissions.secure) { mutableStateOf(permissions.secure != Access.Granted) }
    var systemOpen by rememberSaveable(permissions.system) { mutableStateOf(permissions.system != Access.Granted) }
    SectionTitle("Permissions")
    Disclosure("Secure settings access", permissions.secure.label, secureOpen, { secureOpen = !secureOpen }) {
        Text("For a one-time grant, connect the phone by USB, enable USB debugging and authorize your Windows computer. Run this command from a terminal with adb on PATH. You can then disconnect.")
        Text(command, modifier = Modifier.padding(vertical = 8.dp))
        TextButton(onClick = {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("One-time USB ADB grant", command))
        }) { Text("Copy command") }
        Text("The manufacturer may block this grant. Use Check access to see what Android reports.")
    }
    Disclosure("Modify system settings", permissions.system.label, systemOpen, { systemOpen = !systemOpen }) {
        Text("Open Android Settings and allow Nerd Toggles to modify system settings. Returning here refreshes both checks.")
        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", context.packageName, null))
            try {
                context.startActivity(intent)
                showManualPath = false
            } catch (_: ActivityNotFoundException) {
                showManualPath = true
            } catch (_: SecurityException) {
                showManualPath = true
            }
        }) { Text("Open modify system settings") }
        if (showManualPath) {
            Text("Android could not open this screen. Search Settings for “Modify system settings” and select Nerd Toggles.")
        }
    }
    TextButton(onClick = refresh, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Check access") }
}

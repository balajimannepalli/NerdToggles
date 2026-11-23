package com.nerdtoggles.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.edit

data class Diagnostics(val permissions: Permissions, val features: List<FeatureState>)

private fun checkAccess(check: () -> Boolean): Access = try {
    if (check()) Access.Granted else Access.Denied
} catch (_: SecurityException) {
    Access.Unknown
}

internal fun Context.secureAccess() = checkAccess {
    checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
}

private fun Context.settingsReader() = SettingsReader { setting ->
        when (setting) {
            Setting.Timeout -> Settings.System.getString(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            else -> Settings.Global.getString(contentResolver, setting.key)
        }
}

internal fun Context.privateDns() = PrivateDnsController(settingsReader(), SettingsWriter { setting, value ->
    require(setting == Setting.DnsHost || setting == Setting.DnsMode)
    Settings.Global.putString(contentResolver, setting.key, value)
}, { secureAccess() == Access.Granted })

internal fun Context.systemAccess() = checkAccess { Settings.System.canWrite(this) }

internal fun Context.screenTimeout() = ScreenTimeoutController(settingsReader(), SettingsWriter { setting, value ->
    require(setting == Setting.Timeout)
    Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, value.toInt())
}, { systemAccess() == Access.Granted })

private fun Context.timeoutPreferences() = getSharedPreferences("screen_timeout", Context.MODE_PRIVATE)

internal fun Context.timeoutCycleText(): String =
    timeoutPreferences().getString("cycle_seconds", DEFAULT_TIMEOUT_CYCLE) ?: DEFAULT_TIMEOUT_CYCLE

internal fun Context.saveTimeoutCycle(durations: List<Int>) {
    timeoutPreferences().edit {
        putString("cycle_seconds", durations.joinToString(", ") { (it / 1000).toString() })
    }
}

fun Context.readDiagnostics(): Diagnostics {
    val permissions = Permissions(secureAccess(), systemAccess())
    return Diagnostics(permissions, readFeatures(settingsReader(), permissions, developerVerified()))
}

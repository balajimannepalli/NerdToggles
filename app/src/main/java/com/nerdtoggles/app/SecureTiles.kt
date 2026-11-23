package com.nerdtoggles.app

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Build
import android.os.Looper

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.edit

internal fun Context.controlPrefs() = getSharedPreferences("secure_controls", Context.MODE_PRIVATE)
internal fun Context.developerVerified() = controlPrefs().getString("verifiedBuild", null) == Build.FINGERPRINT
internal fun Context.recordDeveloperVerification(verified: Boolean) {
    controlPrefs().edit { putString("verifiedBuild", if (verified) Build.FINGERPRINT else null) }
}
private val globalKeys = scaleKeys + Setting.DeveloperOptions
private fun Context.globalReader() = SettingsReader { Settings.Global.getString(contentResolver, it.key) }
private fun Context.globalWriter() = SettingsWriter { key, value ->
    require(key in globalKeys)
    Settings.Global.putString(contentResolver, key.key, value)
}
internal fun Context.animations() = AnimationScaleController(globalReader(), globalWriter()) { secureAccess() == Access.Granted }
internal fun Context.developerOptions() = DeveloperOptionsController(globalReader(), globalWriter()) { secureAccess() == Access.Granted }

abstract class SecureControlTile : TileService() {
    protected abstract val keys: List<Setting>
    protected abstract fun status(): Pair<Int, String>
    protected abstract fun change(): String?
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { if (observing) render() }
    }
    override fun onStartListening() {
        super.onStartListening()
        if (!observing) try {
            for (key in keys) {
                contentResolver.registerContentObserver(Settings.Global.getUriFor(key.key), false, observer)
                observing = true
            }
        } catch (_: SecurityException) { /* Listen and click still refresh. */ }
        render()
    }
    override fun onStopListening() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        observing = false
        super.onStopListening()
    }
    private fun render() {
        val tile = qsTile ?: return
        val (state, label) = if (secureAccess() == Access.Granted) status()
            else Tile.STATE_UNAVAILABLE to "Check permission"
        tile.state = state
        tile.subtitle = label
        tile.contentDescription = "${tile.label}, $label"
        tile.updateTile()
    }
    override fun onClick() {
        super.onClick()
        val action = Runnable {
            change()?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
            render()
        }
        if (isLocked) unlockAndRun(action) else action.run()
    }
}

class AnimationScaleTile : SecureControlTile() {
    override val keys = scaleKeys
    private val controller by lazy { animations() }
    override fun status(): Pair<Int, String> {
        val values = controller.snapshot().values
        if (null in values) return Tile.STATE_UNAVAILABLE to "Unknown"
        val common = values.distinct().singleOrNull()
        return (if (common == 0f) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE) to
            (common?.let { "${it}×" } ?: "Mixed")
    }
    override fun change(): String? {
        val cycle = scaleCycle(controlPrefs().getString("cycle", defaultScales).orEmpty())
            ?: return "Check the animation cycle in Controls."
        return controller.cycle(cycle)
    }
}

class DeveloperOptionsTile : SecureControlTile() {
    override val keys = listOf(Setting.DeveloperOptions)
    private val controller by lazy { developerOptions() }
    override fun status(): Pair<Int, String> {
        if (!developerVerified()) return Tile.STATE_UNAVAILABLE to "Verify in Controls"
        return when (controller.read()) {
            true -> Tile.STATE_ACTIVE to "On"
            false -> Tile.STATE_INACTIVE to "Off"
            null -> Tile.STATE_UNAVAILABLE to "Unknown"
        }
    }
    override fun change(): String? {
        val prefs = controlPrefs()
        if (!developerVerified()) return "Verify in Controls first."
        val before = controller.read() ?: return "State unavailable."
        val consent = prefs.getBoolean("allowDisable", false)
        val result = controller.change(before, consent)
        if (result != null && (!before || consent)) recordDeveloperVerification(false)
        return result
    }
}

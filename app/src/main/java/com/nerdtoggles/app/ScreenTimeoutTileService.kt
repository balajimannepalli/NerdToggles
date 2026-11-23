package com.nerdtoggles.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class ScreenTimeoutTileService : TileService() {
    private val controller by lazy { screenTimeout() }
    private var observing = false
    private var receiving = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (observing) render()
        }
    }
    private val screenEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (receiving) render()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!observing) {
            try {
                val uri = Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT)
                contentResolver.registerContentObserver(uri, false, observer)
                observing = true
            } catch (_: SecurityException) {
                // Listening and taps still read the current setting.
            }
        }
        if (!receiving) {
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenEvents, filter, RECEIVER_NOT_EXPORTED)
            receiving = true
        }
        render()
    }

    override fun onStopListening() {
        stopWatching()
        super.onStopListening()
    }

    override fun onDestroy() {
        stopWatching()
        super.onDestroy()
    }

    private fun stopWatching() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        if (receiving) unregisterReceiver(screenEvents)
        observing = false
        receiving = false
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::cycle) else cycle()
    }

    private fun cycle() {
        val durations = parseTimeoutCycle(timeoutCycleText()) ?: parseTimeoutCycle(DEFAULT_TIMEOUT_CYCLE)!!
        val failure = controller.cycle(durations)
        render()
        if (failure != null) Toast.makeText(this, failure, Toast.LENGTH_LONG).show()
    }

    private fun render() {
        val tile = qsTile ?: return
        val current = controller.read()
        val allowed = systemAccess() == Access.Granted
        tile.state = if (allowed && current != null) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        tile.subtitle = when {
            !allowed -> "Check permission"
            current == null -> "Unknown"
            else -> formatTimeout(current)
        }
        tile.contentDescription = "${getString(R.string.screen_timeout)}, ${tile.subtitle}"
        tile.updateTile()
    }
}

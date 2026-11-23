package com.nerdtoggles.app

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class PrivateDnsTile : TileService() {
    private val controller by lazy { privateDns() }
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { if (observing) render() }
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!observing) {
            try {
                for (setting in listOf(Setting.DnsMode, Setting.DnsHost)) {
                    contentResolver.registerContentObserver(Settings.Global.getUriFor(setting.key), false, observer)
                    observing = true
                }
            } catch (_: SecurityException) {
                // Some providers restrict observation. Each listen and tap still reads live values.
            }
        }
        render()
    }

    override fun onStopListening() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        observing = false
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { cycle() } else cycle()
    }

    private fun cycle() {
        val result = controller.cycle()
        render(result == DnsResult.Failed)
        when (result) {
            DnsResult.Configure -> {
                val intent = Intent(this, MainActivity::class.java).setAction(ACTION_QS_TILE_PREFERENCES)
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(this, PrivateDnsTile::class.java))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                try {
                    startActivityAndCollapse(pending)
                } catch (_: RuntimeException) {
                    Toast.makeText(this, "Open Private DNS in Nerd Toggles to choose a hostname.", Toast.LENGTH_LONG).show()
                }
            }
            DnsResult.Verified -> Unit
            else -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun render(failed: Boolean = false) {
        val tile = qsTile ?: return
        val dns = controller.read()
        val allowed = secureAccess() == Access.Granted
        tile.state = when {
            !allowed || dns.error != null || failed -> Tile.STATE_UNAVAILABLE
            dns.mode == DnsMode.Off || (dns.mode == DnsMode.Custom && !validDnsHost(dns.host)) -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        tile.subtitle = when {
            !allowed -> "Check permission"
            failed -> "Change unverified"
            dns.error != null -> "Unknown"
            dns.mode == DnsMode.Custom && !validDnsHost(dns.host) -> "Choose hostname"
            else -> dns.mode?.label
        }
        tile.contentDescription = "${getString(R.string.private_dns)}, ${tile.subtitle}"
        tile.updateTile()
    }
}

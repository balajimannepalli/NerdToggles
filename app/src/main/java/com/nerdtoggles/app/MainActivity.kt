package com.nerdtoggles.app

import android.os.Bundle
import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var diagnostics by mutableStateOf<Diagnostics?>(null)
    private var configuring by mutableStateOf<Feature?>(null)
    private var dns by mutableStateOf<DnsState?>(null)
    private val controller by lazy { privateDns() }

    private fun tilePreferences(intent: Intent): Feature? {
        if (intent.action != TileService.ACTION_QS_TILE_PREFERENCES) return null
        val tile = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        return when (tile) {
            ComponentName(this, AnimationScaleTile::class.java) -> Feature.AnimationScale
            ComponentName(this, DeveloperOptionsTile::class.java) -> Feature.DeveloperOptions
            ComponentName(this, ScreenTimeoutTileService::class.java) -> Feature.ScreenTimeout
            null, ComponentName(this, PrivateDnsTile::class.java) -> Feature.PrivateDns
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tilePreferences(intent)?.let {
            configuring = it
            refresh()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tilePreferences", configuring?.id)
    }

    private fun refresh() {
        diagnostics = readDiagnostics()
        dns = controller.read()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuring = if (savedInstanceState == null) tilePreferences(intent)
            else Feature.entries.find { it.id == savedInstanceState.getString("tilePreferences") }
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(this)
            } else {
                dynamicLightColorScheme(this)
            }
            MaterialTheme(colorScheme = colors) {
                val screens = rememberSaveableStateHolder()
                BackHandler(enabled = configuring != null) { configuring = null }
                when (configuring) {
                    Feature.AnimationScale, Feature.DeveloperOptions -> {
                        val feature = requireNotNull(configuring)
                        SecureConfiguration(feature, diagnostics?.features?.find { it.feature == feature },
                            ::refresh) { configuring = null }
                    }
                    Feature.PrivateDns -> {
                        val current = dns
                        if (current != null) DnsConfiguration(current, diagnostics?.permissions?.secure, { host ->
                            controller.useCustom(host, current).also { refresh() }
                        }, { configuring = null })
                    }
                    Feature.ScreenTimeout -> {
                        val state = diagnostics?.features?.first { it.feature == Feature.ScreenTimeout }
                        TimeoutConfiguration(state) { configuring = null }
                    }
                    else -> screens.SaveableStateProvider("catalogue") { NerdToggles(diagnostics, dns, ::refresh) { feature ->
                        configuring = feature
                        refresh()
                    } }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NerdToggles(diagnostics: Diagnostics?, dns: DnsState?, refresh: () -> Unit, configure: (Feature) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = rememberSaveableStateHolder()
    val destinations = stringArrayResource(R.array.destinations)

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.app_name),
                    Modifier.padding(16.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                PrimaryTabRow(selectedTabIndex = selected) {
                    destinations.forEachIndexed { index, label ->
                        Tab(
                            selected = index == selected,
                            onClick = { selected = index },
                            text = { Text(label) },
                        )
                    }
                }
                tabs.SaveableStateProvider(selected) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (diagnostics != null) {
                            when (selected) {
                                0 -> Controls(diagnostics.features, dns, refresh, configure)
                                1 -> Setup(diagnostics.permissions, refresh)
                                else -> Device(diagnostics.features)
                            }
                        }
                    }
                }
            }
        }
    }
}

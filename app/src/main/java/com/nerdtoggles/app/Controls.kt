package com.nerdtoggles.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun Disclosure(title: String, value: String, open: Boolean, toggle: () -> Unit,
    icon: Int? = null, body: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = toggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_expand), null, Modifier.rotate(if (open) 180f else 0f))
            }
            if (open) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), content = body)
        }
    }
}

@Composable
private fun <T> Selector(label: String, options: List<T>, draft: T?, enabled: Boolean,
    name: (T) -> String, select: (T) -> Unit, apply: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = options.isNotEmpty(), onClick = { open = true }) {
            Text("$label · " + (draft?.let(name) ?: "Choose"))
            Icon(painterResource(R.drawable.ic_expand), null)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEach { choice ->
                DropdownMenuItem(text = { Text(name(choice)) }, onClick = { select(choice); open = false })
            }
        }
    }
    Button(enabled = enabled && draft in options, onClick = apply) { Text("Apply") }
}

@Composable
internal fun Controls(states: List<FeatureState>, dns: DnsState?, refresh: () -> Unit, configure: (Feature) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Spacer(Modifier.height(8.dp))
    states.forEach { state ->
        val feature = state.feature
        val icon = when (feature) {
            Feature.PrivateDns -> R.drawable.ic_dns
            Feature.ScreenTimeout -> R.drawable.ic_screen_timeout
            Feature.AnimationScale -> R.drawable.ic_animation
            Feature.DeveloperOptions -> R.drawable.ic_developer
            Feature.Network -> R.drawable.ic_network
        }
        val summary = state.reading.value.substringBefore('\n').substringBefore(" · Hostname:")
        Disclosure(feature.title, summary, expanded == feature.id, {
            expanded = if (expanded == feature.id) null else feature.id
            if (expanded != null) refresh()
        }, icon) {
            var feedback by remember { mutableStateOf("") }
            val allowed = state.access == Access.Granted
            val ready = allowed && state.reading.reason == null
            if (!allowed && feature != Feature.Network) Text("Check permission in Setup.")
            state.reading.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            when (feature) {
                Feature.PrivateDns -> if (dns != null) {
                    var mode by rememberSaveable(dns.mode) { mutableStateOf(dns.mode) }
                    Text("Saved hostname · " + (dns.host?.takeIf(::validDnsHost) ?: "Unavailable"))
                    Selector("Mode", DnsMode.entries, mode, allowed && dns.error == null && mode != dns.mode,
                        { it.label }, { mode = it; feedback = "" }) {
                        if (mode == DnsMode.Custom && !validDnsHost(dns.host)) configure(feature)
                        else {
                            feedback = context.privateDns().setMode(requireNotNull(mode), dns).message
                            refresh()
                        }
                    }
                    TextButton(onClick = { configure(feature) }) { Text("Configure custom hostname") }
                    AddTile(R.string.private_dns, icon, PrivateDnsTile::class.java, ready)
                }
                Feature.ScreenTimeout -> {
                    val choices = remember { parseTimeoutCycle(context.timeoutCycleText()).orEmpty() }
                    var draft by rememberSaveable(summary) { mutableStateOf(choices.find { formatTimeout(it) == summary }) }
                    Selector("Duration", choices, draft, ready && draft?.let(::formatTimeout) != summary,
                        ::formatTimeout, { draft = it; feedback = "" }) {
                        feedback = context.screenTimeout().cycle(listOf(requireNotNull(draft))) ?: "Timeout verified."
                        refresh()
                    }
                    TextButton(onClick = { configure(feature) }) { Text("Configure cycle") }
                    AddTile(R.string.screen_timeout, icon, ScreenTimeoutTileService::class.java, ready)
                }
                Feature.AnimationScale -> {
                    Text(state.reading.value.substringAfter('\n'), style = MaterialTheme.typography.bodyMedium)
                    val choices = remember { (listOf(0f, 0.5f, 1f) +
                        scaleCycle(context.controlPrefs().getString("cycle", defaultScales).orEmpty()).orEmpty()).distinct() }
                    val current = summary.removeSuffix("×").toFloatOrNull()
                    var draft by rememberSaveable(summary) { mutableStateOf(current?.takeIf { it in choices }) }
                    Selector("Scale", choices, draft, ready && draft != current,
                        { "${it.toString().removeSuffix(".0")}×" }, { draft = it; feedback = "" }) {
                        feedback = context.animations().cycle(listOf(requireNotNull(draft))) ?: "All three scales verified."
                        refresh()
                    }
                    TextButton(onClick = { configure(feature) }) { Text("Configure cycle") }
                    AddTile(R.string.animation_scale, icon, AnimationScaleTile::class.java, ready)
                }
                Feature.DeveloperOptions -> {
                    TextButton(onClick = { configure(feature) }) { Text("Review access and change state") }
                    AddTile(R.string.developer_options, icon, DeveloperOptionsTile::class.java, ready && context.developerVerified())
                }
                Feature.Network -> NetworkShortcut()
            }
            if (feedback.isNotEmpty()) Text(feedback, style = MaterialTheme.typography.bodySmall)
        }
    }
}

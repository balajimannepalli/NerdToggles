package com.nerdtoggles.app

enum class Setting(val key: String) {
    DnsMode("private_dns_mode"),
    DnsHost("private_dns_specifier"),
    Timeout("screen_off_timeout"),
    WindowScale("window_animation_scale"),
    TransitionScale("transition_animation_scale"),
    AnimatorScale("animator_duration_scale"),
    DeveloperOptions("development_settings_enabled"),
}

fun interface SettingsReader {
    fun read(setting: Setting): String?
}

// A provider accepting a write would still require independent readback.
fun interface SettingsWriter {
    fun write(setting: Setting, value: String): Boolean
}

enum class Feature(val id: String, val title: String) {
    PrivateDns("private_dns", "Private DNS"),
    ScreenTimeout("screen_timeout", "Screen timeout"),
    AnimationScale("animation_scale", "Animation scale"),
    DeveloperOptions("developer_options", "Developer options"),
    Network("network_preference", "4G/5G preference (experimental)"),
}

enum class Status { Available, PermissionNeeded, Untested, Unsupported }
enum class Access(val label: String) { Granted("Granted"), Denied("Not granted"), Unknown("Unknown") }
data class Permissions(val secure: Access, val system: Access)
data class Reading(val value: String, val reason: String? = null)
data class FeatureState(val feature: Feature, val reading: Reading, val access: Access?) {
    val readStatus = when {
        feature == Feature.Network -> Status.Unsupported
        reading.reason == null -> Status.Available
        else -> Status.Untested
    }
    val controlStatus = when {
        feature == Feature.Network -> Status.Unsupported
        access == Access.Denied -> Status.PermissionNeeded
        else -> Status.Untested
    }
}

private fun SettingsReader.field(
    setting: Setting,
    format: (String) -> String?,
): Reading = try {
    val raw = read(setting)
    when {
        raw == null -> Reading("Unknown", "${setting.key} is unset or unavailable.")
        else -> format(raw)?.let(::Reading) ?: Reading("Unknown", "Unexpected ${setting.key} value.")
    }
} catch (_: SecurityException) {
    Reading("Unknown", "Android restricted access to ${setting.key}.")
} catch (_: IllegalArgumentException) {
    Reading("Unknown", "The settings provider rejected ${setting.key}.")
}

private fun SettingsReader.animation(): Reading {
    fun scale(setting: Setting) = field(setting) {
        it.toFloatOrNull()?.takeIf { n -> n.isFinite() && n >= 0f }?.let { n -> "${n}×" }
    }
    val window = scale(Setting.WindowScale)
    val transition = scale(Setting.TransitionScale)
    val animator = scale(Setting.AnimatorScale)
    val fields = listOf(window, transition, animator)
    val reason = fields.mapNotNull { it.reason }.joinToString(" ").ifEmpty { null }
    val values = fields.filter { it.reason == null }.map { it.value }.distinct()
    val summary = when {
        values.size > 1 -> "Mixed"
        reason != null -> "Unknown"
        else -> values.single()
    }
    return Reading("$summary\nWindow: ${window.value} · Transition: ${transition.value} · Animator: ${animator.value}", reason)
}

internal const val UNVERIFIED_OFF = "Off or hidden by Android"

fun readFeatures(reader: SettingsReader, permissions: Permissions, developerVerified: Boolean = false): List<FeatureState> =
    Feature.entries.map { feature ->
        val reading = when (feature) {
            Feature.PrivateDns -> reader.readDns().reading()
            Feature.AnimationScale -> reader.animation()
            Feature.ScreenTimeout -> reader.field(Setting.Timeout) {
                it.toIntOrNull()?.takeIf { ms -> ms >= 0 }?.let(::formatTimeout)
            }
            Feature.DeveloperOptions -> reader.field(Setting.DeveloperOptions) {
                when (it) {
                    "0" -> if (developerVerified) "Off" else UNVERIFIED_OFF
                    "1" -> "On"
                    else -> null
                }
            }.let { if (it.value == UNVERIFIED_OFF) it.copy(reason = "Master state unverified.") else it }
            Feature.Network -> Reading("Unknown", "Direct network selection is unsupported.")
        }
        val access = when (feature) {
            Feature.ScreenTimeout -> permissions.system
            Feature.Network -> null
            else -> permissions.secure
        }
        FeatureState(feature, reading, access)
    }

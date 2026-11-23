package com.nerdtoggles.app

internal enum class DnsMode(val key: String, val label: String) {
    Off("off", "Off"), Automatic("opportunistic", "Automatic"), Custom("hostname", "Custom"),
}

internal data class DnsState(val mode: DnsMode?, val host: String?, val error: String?) {
    fun reading(): Reading {
        if (mode != DnsMode.Custom) return Reading(mode?.label ?: "Unknown", error)
        val valid = validDnsHost(host)
        return Reading("Custom · Hostname: " + if (valid) host else "Unknown",
            error ?: if (valid) null else "A valid provider hostname is needed.")
    }
}

private val dnsLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

internal fun validDnsHost(host: String?): Boolean {
    if (host == null || host.length !in 1..253) return false
    val labels = host.removeSuffix(".").split('.')
    return labels.size >= 2 && labels.last().any { it.isLetter() } && labels.all(dnsLabel::matches)
}

internal fun SettingsReader.readDns(): DnsState {
    var error: String? = null
    fun get(key: Setting) = try {
        read(key)
    } catch (_: RuntimeException) {
        error = "Android could not read the Private DNS settings."
        null
    }
    val rawMode = get(Setting.DnsMode)
    val host = get(Setting.DnsHost)
    val mode = DnsMode.entries.find { it.key == rawMode }
    return DnsState(mode, host, error ?: if (mode == null) "Private DNS mode is unknown." else null)
}

internal enum class DnsResult(val message: String) {
    Verified("Settings verified. DNS connectivity has not been tested."),
    Configure("Choose a provider hostname."),
    Denied("Secure settings permission needed. Check Setup."),
    Changed("Settings changed. Review the current values and try again."),
    Failed("Android rejected the change or readback did not match. Check the current values."),
}

internal class PrivateDnsController(
    private val reader: SettingsReader,
    private val writer: SettingsWriter,
    private val permitted: () -> Boolean,
) {
    fun read() = reader.readDns()

    fun cycle(): DnsResult {
        val before = read()
        if (!permitted()) return DnsResult.Denied
        if (before.error != null) return DnsResult.Failed
        val target = when (before.mode) {
            DnsMode.Off -> DnsMode.Automatic
            DnsMode.Automatic -> DnsMode.Custom
            DnsMode.Custom -> DnsMode.Off
            null -> return DnsResult.Failed
        }
        if (before.mode != DnsMode.Off && !validDnsHost(before.host)) return DnsResult.Configure
        return apply(before, target, before.host)
    }

    fun useCustom(host: String, displayed: DnsState): DnsResult {
        if (!validDnsHost(host)) return DnsResult.Configure
        return setMode(DnsMode.Custom, displayed, host)
    }

    fun setMode(mode: DnsMode, displayed: DnsState, host: String? = displayed.host): DnsResult {
        val before = read()
        if (before != displayed) return DnsResult.Changed
        if (before.error != null) return DnsResult.Failed
        if (mode == DnsMode.Custom && !validDnsHost(host)) return DnsResult.Configure
        return apply(before, mode, if (mode == DnsMode.Custom) host else before.host)
    }

    private fun apply(before: DnsState, mode: DnsMode, host: String?): DnsResult {
        if (!permitted()) return DnsResult.Denied
        val expected = DnsState(mode, host, null)
        // Stage and verify an explicitly chosen hostname before enabling Custom.
        try {
            if (host != before.host && host != null) {
                val accepted = writer.write(Setting.DnsHost, host)
                val staged = read()
                if (!accepted || staged != before.copy(host = host)) return DnsResult.Failed
            }
            if (mode != before.mode) {
                if (!permitted()) return DnsResult.Denied
                val accepted = writer.write(Setting.DnsMode, mode.key)
                val after = read()
                return if (accepted && after == expected) DnsResult.Verified else DnsResult.Failed
            }
        } catch (_: RuntimeException) {
            read() // A provider failure can occur after a partial change.
            return if (permitted()) DnsResult.Failed else DnsResult.Denied
        }
        return if (read() == expected) DnsResult.Verified else DnsResult.Failed
    }
}

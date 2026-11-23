package com.nerdtoggles.app

import org.junit.Assert.*
import org.junit.Test

class PrivateDnsControllerTest {
    private val values = mutableMapOf(Setting.DnsMode to "off", Setting.DnsHost to "personal.resolver.example")
    private val writes = mutableListOf<Setting>()
    private var granted = true
    private fun controller(write: (Setting, String) -> Boolean = { key, value -> values[key] = value; true }) =
        PrivateDnsController(SettingsReader(values::get), SettingsWriter { key, value ->
            writes.add(key)
            write(key, value)
        }, { granted })

    @Test fun modeSelectionAppliesOnlyTheRequestedModeAndPreservesHost() {
        val c = controller()
        val displayed = c.read()
        assertEquals(DnsResult.Verified, c.setMode(DnsMode.Custom, displayed))
        assertEquals(listOf(Setting.DnsMode), writes)
        assertEquals(displayed.host, c.read().host)
        assertEquals(DnsResult.Changed, c.setMode(DnsMode.Automatic, displayed))
        granted = false
        assertEquals(DnsResult.Denied, c.setMode(DnsMode.Off, c.read()))
        assertEquals(listOf(Setting.DnsMode), writes)
    }

    @Test fun completeCycleNeverWritesHostname() {
        val c = controller()
        for (mode in listOf("opportunistic", "hostname", "off")) {
            assertEquals(DnsResult.Verified, c.cycle())
            assertEquals(mode, values[Setting.DnsMode])
            assertEquals("personal.resolver.example", values[Setting.DnsHost])
        }
        assertEquals(List(3) { Setting.DnsMode }, writes)
    }

    @Test fun invalidOrAbsentHostRequestsConfigurationWithoutChangingSettings() {
        values.remove(Setting.DnsHost)
        val c = controller()
        assertEquals(DnsResult.Verified, c.cycle())
        writes.clear()
        for (mode in listOf("opportunistic", "hostname")) {
            values[Setting.DnsMode] = mode
            assertEquals(DnsResult.Configure, c.cycle())
            values[Setting.DnsHost] = "https://resolver.example"
            assertEquals(DnsResult.Configure, c.cycle())
        }
        assertTrue(writes.isEmpty())
    }

    @Test fun unknownModeAndRevokedPermissionBlockWrites() {
        val c = controller()
        values[Setting.DnsMode] = "unexpected"
        assertEquals(DnsResult.Failed, c.cycle())
        values[Setting.DnsMode] = "off"
        granted = false
        assertEquals(DnsResult.Denied, c.cycle())
        assertTrue(writes.isEmpty())
    }

    @Test fun detectsHostDriftEvenWhenModeMatches() {
        val c = controller { key, value ->
            values[key] = value
            values.remove(Setting.DnsHost)
            true
        }
        assertEquals(DnsResult.Failed, c.cycle())
    }

    @Test fun explicitHostMustVerifyBeforeCustomMode() {
        val c = controller { _, _ -> true }
        assertEquals(DnsResult.Failed, c.useCustom("new.example", c.read()))
        assertEquals(listOf(Setting.DnsHost), writes)
    }

    @Test fun staleFormCannotOverwriteExternalChange() {
        val c = controller()
        val displayed = c.read()
        values[Setting.DnsHost] = "changed.example"
        assertEquals(DnsResult.Changed, c.useCustom("new.example", displayed))
        assertTrue(writes.isEmpty())
        assertEquals(DnsResult.Verified, c.useCustom("new.example", c.read()))
        assertEquals(listOf(Setting.DnsHost, Setting.DnsMode), writes)
    }

    @Test fun partialWritesAndProviderExceptionsAreReported() {
        val c = controller { key, value ->
            if (key == Setting.DnsMode) throw SecurityException()
            values[key] = value
            true
        }
        assertEquals(DnsResult.Failed, c.useCustom("new.example", c.read()))
        assertEquals("new.example", c.read().host)
        assertEquals(DnsMode.Off, c.read().mode)
    }

    @Test fun validatesHostnameWithoutResolvingIt() {
        for (bad in listOf("", "1.1.1.1", "::1", "https://dns.google", "-a.example", "a..example",
            "a.example:853", "a".repeat(64) + ".example")) assertFalse(validDnsHost(bad))
        for (good in listOf("one.one.one.one", "dns.google", "dns.adguard-dns.com", "ABC-123.example"))
            assertTrue(validDnsHost(good))
    }
}

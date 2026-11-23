package com.nerdtoggles.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureStateTest {
    @Test fun unverifiedDeveloperZeroMayBeRedacted() {
        val reader = SettingsReader { "0" }
        val access = Permissions(Access.Granted, Access.Granted)
        fun state(verified: Boolean) = readFeatures(reader, access, verified).single { it.feature == Feature.DeveloperOptions }
        assertEquals(Status.Untested, state(false).readStatus)
        assertEquals(UNVERIFIED_OFF, state(false).reading.value)
        assertEquals("Off", state(true).reading.value)
    }
    private val grants = Permissions(Access.Granted, Access.Granted)

    @Test fun readableValuesAndWritePermissionsAreIndependent() {
        val states = readFeatures(SettingsReader { "30000" }, Permissions(Access.Granted, Access.Denied))
        val timeout = states.single { it.feature == Feature.ScreenTimeout }
        assertEquals("30 s", timeout.reading.value)
        assertEquals(Status.Available, timeout.readStatus)
        assertEquals(Status.PermissionNeeded, timeout.controlStatus)
        assertEquals(Status.Untested, states.single { it.feature == Feature.PrivateDns }.controlStatus)
        assertEquals(Status.Unsupported, states.single { it.feature == Feature.Network }.controlStatus)
        assertEquals(5, states.map { it.feature.id }.distinct().size)
    }

    @Test fun preservesCustomModeWhenHostnameReadIsRestricted() {
        val state = readFeatures(SettingsReader {
            if (it == Setting.DnsHost) throw SecurityException()
            if (it == Setting.DnsMode) "hostname" else null
        }, grants).single { it.feature == Feature.PrivateDns }
        assertEquals("Custom · Hostname: Unknown", state.reading.value)
        assertNotNull(state.reading.reason)
    }

    @Test fun showsConfiguredHostnameInCustomMode() {
        val values = mapOf(Setting.DnsMode to "hostname", Setting.DnsHost to "resolver.example")
        val state = readFeatures(SettingsReader(values::get), grants).single { it.feature == Feature.PrivateDns }
        assertEquals("Custom · Hostname: resolver.example", state.reading.value)
        assertEquals(Status.Available, state.readStatus)
    }

    @Test fun reportsIndependentScalesIncludingPartialMixedReadback() {
        val values = mapOf(Setting.WindowScale to "0.5", Setting.TransitionScale to "1", Setting.AnimatorScale to "NaN")
        val state = readFeatures(SettingsReader(values::get), grants).single { it.feature == Feature.AnimationScale }
        assertEquals("Mixed\nWindow: 0.5× · Transition: 1.0× · Animator: Unknown", state.reading.value)
        assertEquals(Status.Untested, state.readStatus)
        assertTrue(state.reading.reason.orEmpty().contains("animator_duration_scale"))
    }

    @Test fun missingAndUnexpectedValuesRemainUnknown() {
        val values = mapOf(Setting.DeveloperOptions to "2", Setting.Timeout to "-3")
        val states = readFeatures(SettingsReader(values::get), grants)
        for (feature in listOf(Feature.PrivateDns, Feature.DeveloperOptions, Feature.ScreenTimeout)) {
            val state = states.single { it.feature == feature }
            assertEquals("Unknown", state.reading.value)
            assertNotNull(state.reading.reason)
        }
    }
}

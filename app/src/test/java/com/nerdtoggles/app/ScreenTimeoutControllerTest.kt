package com.nerdtoggles.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeoutControllerTest {
    @Test fun cycleUsesConfiguredOrderAndStartsAtFirstForExternalValues() {
        val cycle = requireNotNull(parseTimeoutCycle("60, 15, 30"))
        assertEquals(60_000, nextTimeout(42_000, cycle))
        assertEquals(15_000, nextTimeout(60_000, cycle))
        assertEquals(60_000, nextTimeout(30_000, cycle))
        assertEquals(5_000, nextTimeout(5_000, listOf(5_000)))
    }

    @Test fun validatesWholeSecondsAndPreservesDefaults() {
        assertEquals(listOf(15_000, 30_000, 60_000, 120_000, 300_000), parseTimeoutCycle(DEFAULT_TIMEOUT_CYCLE))
        assertEquals(listOf(1_800_000, 5_000), parseTimeoutCycle("1800, 5"))
        for (input in listOf("", "15,", "15, 015", "4", "1801", "1.5", "99999999999999")) {
            assertNull(input, parseTimeoutCycle(input))
        }
    }

    @Test fun formatsExternalValuesWithoutRoundingAwayFractions() {
        assertEquals("0 s", formatTimeout(0))
        assertEquals("2 min", formatTimeout(120_000))
        assertEquals("1 min 2.5 s", formatTimeout(62_500))
    }

    @Test fun readsLiveValuesAndReportsClamping() {
        var current = "42000"
        val controller = ScreenTimeoutController(SettingsReader { current }, SettingsWriter { setting, value ->
            assertEquals(Setting.Timeout, setting)
            current = if (value == "60000") "45000" else value
            true
        }, { true })
        val cycle = listOf(15_000, 60_000)
        assertNull(controller.cycle(cycle))
        assertEquals(15_000, controller.read())
        assertEquals("Requested 1 min. Android reports 45 s.", controller.cycle(cycle))
        assertEquals(45_000, controller.read())
    }

    @Test fun skipsUnknownAndDeniedWritesAndHandlesRevocationDuringWrite() {
        var granted = false
        var current: String? = null
        var writes = 0
        val controller = ScreenTimeoutController(SettingsReader { current }, SettingsWriter { _, _ ->
            writes++
            granted = false
            throw SecurityException()
        }, { granted })
        val cycle = listOf(15_000)
        assertTrue(controller.cycle(cycle).orEmpty().contains("Setup"))
        granted = true
        assertTrue(controller.cycle(cycle).orEmpty().contains("could not read"))
        assertEquals(0, writes)
        current = "30000"
        assertTrue(controller.cycle(cycle).orEmpty().contains("Setup"))
        assertEquals(1, writes)
    }
}

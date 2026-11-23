package com.nerdtoggles.app

import org.junit.Assert.*
import org.junit.Test

class SecureControlsTest {
    private val values = scaleKeys.associateWith { "1" }.toMutableMap()
    private val reader = SettingsReader(values::get)
    private val writer = SettingsWriter { key, value -> values[key] = value; true }
    private val cycle = requireNotNull(scaleCycle(defaultScales))

    @Test fun mixedAndUnlistedStartAtFirstThenAdvanceAndWrap() {
        val c = AnimationScaleController(reader, writer) { true }
        values[Setting.AnimatorScale] = "2"
        for (expected in listOf(0f, .5f, 1f, 0f)) {
            assertNull(c.cycle(cycle))
            assertTrue(c.snapshot().values.all { it == expected })
        }
        scaleKeys.forEach { values[it] = "1.5" }
        assertNull(c.cycle(cycle))
        assertTrue(c.snapshot().values.all { it == 0f })
    }
    @Test fun validatesCycle() {
        for (text in listOf("", "0,0.0", "NaN", "-1", "1,3")) assertNull(scaleCycle(text))
        assertEquals(listOf(2f, 0f), scaleCycle("2,0"))
    }
    @Test fun partialFailureRestoresEvenIfProviderChangedThenThrew() {
        val c = AnimationScaleController(reader, SettingsWriter { key, value ->
            writer.write(key, value)
            if (key == Setting.TransitionScale && value == "0.0") throw SecurityException()
            true
        }) { true }
        assertTrue(c.cycle(cycle)!!.contains("Original"))
        assertTrue(c.snapshot().values.all { it == 1f })
    }
    @Test fun externalChangeDuringRollbackSurvives() {
        val c = AnimationScaleController(reader, SettingsWriter { key, value ->
            if (key == Setting.TransitionScale) {
                values[Setting.WindowScale] = "2"
                false
            } else writer.write(key, value)
        }) { true }
        assertTrue(c.cycle(cycle)!!.contains("Partial"))
        assertEquals("2", values[Setting.WindowScale])
    }
    @Test fun permissionAndIncompleteSnapshotPreventWrites() {
        val forbidden = SettingsWriter { _, _ -> error("unexpected write") }
        assertNotNull(AnimationScaleController(reader, forbidden) { false }.cycle(cycle))
        values.remove(Setting.WindowScale)
        assertNotNull(AnimationScaleController(reader, forbidden) { true }.cycle(cycle))
    }
    @Test fun developerRequiresConsentPermissionAndAccurateReadback() {
        values[Setting.DeveloperOptions] = "1"
        var allowed = false
        val c = DeveloperOptionsController(reader, writer) { allowed }
        assertNotNull(c.change(true, true))
        allowed = true
        assertNotNull(c.change(true, false))
        assertEquals(true, c.read())
        assertNull(c.change(true, true))
        assertEquals(false, c.read())
        assertNotNull(c.change(true, true))
        assertNotNull(DeveloperOptionsController(reader, SettingsWriter { _, _ -> true }) { true }.change(false, false))
        assertNull(c.change(false, false))
    }
}

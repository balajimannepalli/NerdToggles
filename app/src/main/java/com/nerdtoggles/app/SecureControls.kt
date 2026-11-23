package com.nerdtoggles.app

internal val scaleKeys = listOf(Setting.WindowScale, Setting.TransitionScale, Setting.AnimatorScale)
internal const val defaultScales = "0, 0.5, 1"

internal fun scaleCycle(text: String): List<Float>? {
    val scales = text.split(',').map {
        it.trim().toFloatOrNull()?.takeIf { n -> n in listOf(0f, 0.5f, 1f, 1.5f, 2f) } ?: return null
    }
    return scales.takeIf { it.distinct().size == it.size }
}

internal class AnimationScaleController(private val reader: SettingsReader, private val writer: SettingsWriter,
    private val allowed: () -> Boolean) {
    private fun value(key: Setting): Float? = try {
        reader.read(key)?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
    } catch (_: RuntimeException) { null }

    fun snapshot() = scaleKeys.associateWith(::value)

    private fun write(key: Setting, target: Float): Boolean = try {
        allowed() && writer.write(key, target.toString())
    } catch (_: RuntimeException) { false }

    fun cycle(cycle: List<Float>): String? {
        if (!allowed()) return "Check secure settings permission."
        val before = snapshot()
        if (before.containsValue(null)) return "Could not read all three scales."
        val common = before.values.distinct().singleOrNull()
        val target = cycle[(cycle.indexOf(common) + 1) % cycle.size]
        val attempted = mutableListOf<Setting>()
        for (key in scaleKeys) {
            if (before[key] == target) continue
            if (value(key) != before[key]) break
            attempted += key
            if (!write(key, target) || value(key) != target) return restore(before, attempted, target)
        }
        if (snapshot().values.all { it == target }) return null
        return restore(before, attempted, target)
    }

    private fun restore(before: Map<Setting, Float?>, attempted: List<Setting>, target: Float): String {
        for (key in attempted.asReversed()) {
            // Preserve any intervening value that differs from our attempted write.
            if (value(key) == target) write(key, before.getValue(key)!!)
        }
        return if (snapshot() == before) "Change failed. Original scales verified."
            else "Partial or external change. Check all three values in Controls."
    }
}

internal class DeveloperOptionsController(private val reader: SettingsReader, private val writer: SettingsWriter,
    private val allowed: () -> Boolean) {
    fun read(): Boolean? = try {
        when (reader.read(Setting.DeveloperOptions)) { "1" -> true; "0" -> false; else -> null }
    } catch (_: RuntimeException) { null }

    fun change(expected: Boolean, allowDisable: Boolean): String? {
        if (!allowed()) return "Check secure settings permission."
        if (read() != expected) return "State changed or unreadable. Check again."
        if (expected && !allowDisable) return "Allow disabling in Developer options configuration first."
        val accepted = try {
            writer.write(Setting.DeveloperOptions, if (expected) "0" else "1")
        } catch (_: RuntimeException) { false }
        val actual = read()
        return if (accepted && actual == !expected) null else "Android rejected the change or readback failed."
    }
}

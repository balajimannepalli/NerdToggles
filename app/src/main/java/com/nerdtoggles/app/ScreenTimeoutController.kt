package com.nerdtoggles.app

internal const val DEFAULT_TIMEOUT_CYCLE = "15, 30, 60, 120, 300"

internal fun parseTimeoutCycle(input: String): List<Int>? {
    val seconds = input.split(',').map { value ->
        value.trim().toIntOrNull()?.takeIf { it in 5..1800 } ?: return null
    }
    if (seconds.distinct().size != seconds.size) return null
    return seconds.map { it * 1000 }
}

internal fun nextTimeout(current: Int, cycle: List<Int>): Int =
    cycle[(cycle.indexOf(current) + 1) % cycle.size]

internal fun formatTimeout(milliseconds: Int): String {
    val minutes = milliseconds / 60_000
    val seconds = (milliseconds % 60_000).toBigDecimal().movePointLeft(3).stripTrailingZeros()
    return when {
        minutes == 0 -> "${seconds.toPlainString()} s"
        seconds.signum() == 0 -> "$minutes min"
        else -> "$minutes min ${seconds.toPlainString()} s"
    }
}

internal class ScreenTimeoutController(
    private val reader: SettingsReader,
    private val writer: SettingsWriter,
    private val permitted: () -> Boolean,
) {
    fun read(): Int? = try {
        reader.read(Setting.Timeout)?.toIntOrNull()?.takeIf { it >= 0 }
    } catch (_: RuntimeException) {
        null
    }

    // The tile renders live readback independently, including after rejected writes.
    fun cycle(durations: List<Int>): String? {
        if (!permitted()) return "Allow Modify system settings in Setup."
        val current = read() ?: return "Android could not read the screen timeout."
        val target = nextTimeout(current, durations)
        if (current == target) return null
        val accepted = try {
            writer.write(Setting.Timeout, target.toString())
        } catch (_: RuntimeException) {
            false
        }
        val actual = read()
        return when {
            !permitted() -> "Allow Modify system settings in Setup."
            !accepted -> "Android rejected the screen timeout change."
            actual == null -> "Android could not verify the screen timeout."
            actual != target -> "Requested ${formatTimeout(target)}. Android reports ${formatTimeout(actual)}."
            else -> null
        }
    }
}

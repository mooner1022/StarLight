package dev.mooner.starlight.plugincore.logger

import android.util.Log

/**
 * The level of a log entry: `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR` or `CRITICAL`.
 *
 * `entry.type.name()` gives the level as a string, as in `entry.type.name() === "ERROR"`. The levels are declared in
 * the order `WARN`, `INFO`, `ERROR`, `CRITICAL`, `DEBUG`, `VERBOSE`, so `ordinal()` and `compareTo` don't follow
 * severity; compare [priority] instead.
 */
enum class LogType(
    /**
     * How severe the level is, as Android's log priority: 2 for `VERBOSE`, 3 `DEBUG`, 4 `INFO`, 5 `WARN`, and 6 for
     * both `ERROR` and `CRITICAL`.
     *
     * Higher is more severe, so `entry.type.priority >= 5` selects warnings and worse.
     */
    val priority: Int
) {
    WARN(Log.WARN),
    INFO(Log.INFO),
    ERROR(Log.ERROR),
    CRITICAL(Log.ERROR),
    DEBUG(Log.DEBUG),
    VERBOSE(Log.VERBOSE)
}
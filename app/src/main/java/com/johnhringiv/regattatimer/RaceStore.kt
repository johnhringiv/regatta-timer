package com.johnhringiv.regattatimer

import android.content.Context

/**
 * Persists the rare state transitions (start / sync / gun / reset) keyed on WALL-CLOCK
 * time, so an in-flight race survives process death and even a watch reboot
 * (elapsedRealtime anchors don't; System.currentTimeMillis() does).
 */
class RaceStore(context: Context) {

    private val prefs = context.getSharedPreferences("race_state", Context.MODE_PRIVATE)

    data class Persisted(val phase: String, val mode: Mode, val wallMs: Long, val savedAtMs: Long)

    /** [deadlineWallMs]: wall-clock time of the upcoming gun. */
    fun saveCountdown(mode: Mode, deadlineWallMs: Long) = save("COUNTDOWN", mode, deadlineWallMs)

    /** [gunWallMs]: wall-clock time the race started. */
    fun saveCountUp(mode: Mode, gunWallMs: Long) = save("COUNTUP", mode, gunWallMs)

    fun clear() = prefs.edit().clear().apply()

    fun load(): Persisted? {
        val phase = prefs.getString("phase", null) ?: return null
        val mode = runCatching { Mode.valueOf(prefs.getString("mode", "") ?: "") }
            .getOrNull() ?: return null
        return Persisted(phase, mode, prefs.getLong("wall", 0L), prefs.getLong("savedAt", 0L))
    }

    /**
     * The persisted race, or null if none / expired. Single source of validity for both
     * the ViewModel restore and the tile's running-state display.
     */
    fun activeRace(maxAgeMs: Long = MAX_AGE_MS): Persisted? {
        val p = load() ?: return null
        if (System.currentTimeMillis() - p.savedAtMs > maxAgeMs) return null
        return p
    }

    companion object {
        const val MAX_AGE_MS = 12 * 60 * 60_000L
    }

    private fun save(phase: String, mode: Mode, wallMs: Long) {
        prefs.edit()
            .putString("phase", phase)
            .putString("mode", mode.name)
            .putLong("wall", wallMs)
            .putLong("savedAt", System.currentTimeMillis())
            .apply()
    }
}

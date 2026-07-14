package com.johnh.regattatimer

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Named haptic cues for the start sequence. */
class Haptics(context: Context) {

    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator

    /** Light confirmation for button presses (start / sync / mode toggle). */
    fun click() =
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

    /** Short tick for final-countdown seconds 10..6. */
    fun tick() =
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

    /** Stronger tick for the last five seconds — the stage change marks "five to go". */
    fun heavyTick() =
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))

    /** Single buzz for intermediate minute signals (3-minute sequence). */
    fun minute() =
        vibrator.vibrate(VibrationEffect.createOneShot(500, 255))

    /** Double buzz at 4:00 — the preparatory signal (RRS 26). */
    fun prep() =
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))

    /** One long buzz at 1:00 — the one-minute signal. */
    fun oneMinute() =
        vibrator.vibrate(VibrationEffect.createOneShot(700, 255))

    /** Long, distinct double-buzz at the gun (0:00). */
    fun gun() =
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 800), -1))

    /** Double-pulse confirming a long-press reset. */
    fun resetConfirm() =
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1))
}

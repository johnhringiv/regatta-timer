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

    /** Short tick for each of the final 10 seconds. */
    fun tick() =
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

    /** Strong buzz at each whole minute remaining. */
    fun minute() =
        vibrator.vibrate(VibrationEffect.createOneShot(500, 255))

    /** Long, distinct double-buzz at the gun (0:00). */
    fun gun() =
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 800), -1))

    /** Double-pulse confirming a long-press reset. */
    fun resetConfirm() =
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1))
}

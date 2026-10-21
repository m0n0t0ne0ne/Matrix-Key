package com.matrixkey.keyboard

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** Key-press vibration and sound, scaled by the person's strength settings. */
class Feedback(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var hapticEnabled = true
    /** 0–100 from the settings slider. */
    var strength = 50
    var soundEnabled = false
    var volume = 40

    private val supportsPrimitives: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK
        ) == true
    }

    /** A normal key press. */
    fun key(view: View, type: KeyType = KeyType.CHAR) {
        if (hapticEnabled) click(view)
        if (soundEnabled) {
            val fx = when (type) {
                KeyType.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, volume / 100f)
        }
    }

    private val hasAmplitude: Boolean by lazy { vibrator?.hasAmplitudeControl() == true }

    /**
     * Key press. The strength slider maps to both how long and how hard the
     * motor runs (8–28 ms, amplitude 90–255), so the top of the slider is a
     * firm, clearly felt click on Nothing's motor.
     */
    private fun click(view: View) {
        val v = vibrator
        val s = (strength / 100f).coerceIn(0f, 1f)
        if (v != null && v.hasVibrator()) {
            val ms = (8 + 20 * s).toLong()
            val amp = if (hasAmplitude) (90 + 165 * s).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
            if (s < 0.25f && supportsPrimitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Very light settings: the motor's own crisp click primitive.
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (0.4f + s * 2.4f).coerceAtMost(1f)).compose())
            } else {
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * A light, crisp tick: one per letter the cursor moves (spacebar slide)
     * and per word when swiping to delete. Short enough to feel like the
     * clicks of a dial even when sliding quickly.
     */
    fun tick(view: View) {
        if (!hapticEnabled) return
        val v = vibrator
        val s = (strength / 100f).coerceIn(0f, 1f)
        if (v != null && v.hasVibrator()) {
            val ms = (4 + 8 * s).toLong()
            val amp = if (hasAmplitude) (80 + 175 * s).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

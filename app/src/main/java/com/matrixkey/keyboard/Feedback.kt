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

    private fun click(view: View) {
        val v = vibrator
        val scale = (strength / 100f).coerceIn(0.05f, 1f)
        if (v != null && v.hasVibrator()) {
            if (supportsPrimitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale).compose())
            } else {
                v.vibrate(VibrationEffect.createOneShot(12, (scale * 255).toInt().coerceIn(1, 255)))
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
        val scale = (strength / 100f).coerceIn(0.05f, 1f)
        if (v != null && v.hasVibrator()) {
            if (supportsPrimitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale).compose())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                v.vibrate(VibrationEffect.createOneShot(5, (scale * 200).toInt().coerceIn(1, 255)))
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

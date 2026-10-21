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
     * Key press, scaled by the strength slider across the motor's whole range:
     * both power (about 10% to 100%) and length (6 to 50 ms) grow with the
     * slider, so 0 is a faint tick and 100 a firm thump.
     */
    private fun click(view: View) {
        val v = vibrator
        if (v == null || !v.hasVibrator()) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        vibrateAt(v, (strength / 100f).coerceIn(0f, 1f))
    }

    private fun vibrateAt(v: Vibrator, s: Float) {
        val ms = (6 + 44 * s).toLong()
        val amp = if (hasAmplitude) (25 + 230 * Math.pow(s.toDouble(), 1.3)).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        play(v, VibrationEffect.createOneShot(ms, amp))
    }

    /**
     * Android scales "touch" vibrations by the phone's own touch-feedback
     * intensity, which squashed the slider into a narrow, faint band. Played
     * as media vibrations, the slider controls the strength itself.
     */
    private fun play(v: Vibrator, effect: VibrationEffect) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_MEDIA))
            else -> @Suppress("DEPRECATION") v.vibrate(effect,
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
        }
    }

    /** Plays one key press at [strengthPercent] (0–100), for the settings slider preview. */
    fun preview(view: View, strengthPercent: Int) {
        val v = vibrator
        if (v == null || !v.hasVibrator()) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); return }
        vibrateAt(v, (strengthPercent / 100f).coerceIn(0f, 1f))
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
            play(v, VibrationEffect.createOneShot(ms, amp))
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

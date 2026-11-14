package com.widgeon.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.widgeon.app.widgeon

/**
 * Central haptics vocabulary. Every interaction that should "feel" something
 * goes through here; the master toggle lives in Settings.
 */
class Haptics(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val enabled get() = context.widgeon.prefs.hapticsEnabled

    private fun wave(timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun prebaked(id: Int) {
        if (!enabled) return
        runCatching {
            vibrator?.vibrate(VibrationEffect.createPredefined(id))
        }
    }

    /** Ultra-light UI tick: chips, cards, minor state flips. */
    fun tick() = prebaked(VibrationEffect.EFFECT_TICK)

    /** Heavier click: primary buttons, bottom-bar switches. */
    fun click() = prebaked(VibrationEffect.EFFECT_CLICK)

    /** Distinct double-pulse for ON. */
    fun toggleOn() = wave(longArrayOf(0, 18, 40, 26), intArrayOf(0, 120, 0, 190))

    /** Single soft pulse for OFF. */
    fun toggleOff() = wave(longArrayOf(0, 22), intArrayOf(0, 110))

    /** Triumphant little pattern for completed generations. */
    fun success() = wave(longArrayOf(0, 24, 60, 24, 60, 44), intArrayOf(0, 140, 0, 140, 0, 230))

    /** Low double-buzz for errors. */
    fun error() = wave(longArrayOf(0, 40, 80, 40), intArrayOf(0, 170, 0, 170))

    /** Tiny movement when undoing / redoing. */
    fun undo() = wave(longArrayOf(0, 14, 50, 10), intArrayOf(0, 90, 0, 60))

    companion object {
        /** Shared instance (cheap object; vibrator resolved lazily). */
        @Volatile private var instance: Haptics? = null
        fun get(context: Context): Haptics =
            instance ?: synchronized(this) { instance ?: Haptics(context.applicationContext).also { instance = it } }
    }
}

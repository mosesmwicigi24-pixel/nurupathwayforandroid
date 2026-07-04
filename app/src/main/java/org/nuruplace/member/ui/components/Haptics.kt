package org.nuruplace.member.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** One haptic voice for the whole app, routed through the OS effect map
 *  (performHapticFeedback) — Samsung's One UI engine, Tecno's HiOS and stock
 *  Android each render their own native-feeling tick instead of a raw buzz.
 *  Also means we automatically respect the user's system haptic settings. */
object Haptics {
    /** Light tap — buttons, sends, toggles. */
    fun tap(v: View) { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    /** Tiny tick — tab switches, selection changes, scrubbing detents. */
    fun tick(v: View) { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    /** Success — celebrations, check-ins, submissions accepted. */
    fun confirm(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Failure — rejected input, error states. */
    fun reject(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            v.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

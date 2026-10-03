package com.monkaydee.tcgcatalogue.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** A light haptic "tick" (e.g. when a page has turned); respects the system's touch-feedback setting. */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { tick(view) } }
}

private fun tick(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

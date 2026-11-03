package org.koitharu.kotatsu.core.media

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Light haptic ticks. [View.performHapticFeedback] already respects the system touch-feedback setting. */
object UiHaptics {

    /** A selection toggled or a pull-to-refresh triggered. */
    fun tick(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Entering a mode, such as multi-select. */
    fun longPress(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** An action succeeded, such as Undo. */
    fun confirm(view: View?) {
        view?.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
        )
    }
}

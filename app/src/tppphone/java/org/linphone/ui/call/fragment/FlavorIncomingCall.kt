// SPDX-License-Identifier: GPL-3.0-or-later
package org.linphone.ui.call.fragment

import android.view.View
import ar.com.tpp.phone.ui.call.SwipeUpToAnswer

/** TPP Phone: WhatsApp-style vertical swipe on the incoming call button. Returns true when handled. */
fun attachFlavorSlidingButton(button: View, track: View, onAnswer: () -> Unit, onDecline: () -> Unit): Boolean {
    SwipeUpToAnswer.attach(button, track, onAnswer, onDecline)
    return true
}

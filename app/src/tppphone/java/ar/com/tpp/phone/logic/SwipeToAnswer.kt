// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

enum class SwipeOutcome { ANSWER, DECLINE, NONE }

/**
 * WhatsApp-style incoming call button: dragging it up more than [threshold] of [trackHeight]
 * answers, dragging it down as far declines, anything else springs back.
 */
fun swipeOutcome(startY: Float, currentY: Float, trackHeight: Float, threshold: Float = 0.6f): SwipeOutcome {
    if (trackHeight <= 0f) return SwipeOutcome.NONE
    val progress = (startY - currentY) / trackHeight
    return when {
        progress >= threshold -> SwipeOutcome.ANSWER
        progress <= -threshold -> SwipeOutcome.DECLINE
        else -> SwipeOutcome.NONE
    }
}

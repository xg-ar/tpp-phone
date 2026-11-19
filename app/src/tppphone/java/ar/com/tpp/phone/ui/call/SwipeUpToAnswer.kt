// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.call

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import ar.com.tpp.phone.logic.SwipeOutcome
import ar.com.tpp.phone.logic.swipeOutcome
import org.linphone.R

/**
 * WhatsApp-style incoming call: the green [button] is dragged up inside [track] to answer, or down
 * to decline. Released half way, it springs back.
 */
class SwipeUpToAnswer private constructor(
    private val track: View,
    private val onAnswer: () -> Unit,
    private val onDecline: () -> Unit
) : View.OnTouchListener {
    companion object {
        @SuppressLint("ClickableViewAccessibility")
        fun attach(button: View, track: View, onAnswer: () -> Unit, onDecline: () -> Unit) {
            button.setOnTouchListener(SwipeUpToAnswer(track, onAnswer, onDecline))
            // The call screen is edge to edge: keep "Rechazar" above the navigation bar.
            ViewCompat.setOnApplyWindowInsetsListener(track) { view, insets ->
                view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
                insets
            }
            animateHint(track.findViewById(R.id.tpp_swipe_hint))
        }

        private fun animateHint(hint: View) {
            val distance = hint.resources.displayMetrics.density * -10
            val animator = ObjectAnimator.ofFloat(hint, View.TRANSLATION_Y, 0f, distance).apply {
                duration = 700
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
            hint.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) = animator.cancel()
            })
        }
    }

    private var restY = Float.NaN
    private var touchOffset = 0f
    private var finished = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (finished) return true
        val hint = track.findViewById<View>(R.id.tpp_swipe_hint)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (restY.isNaN()) restY = view.y
                view.animate().cancel()
                touchOffset = view.y - event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val maxY = (track.height - track.paddingBottom - view.height).toFloat()
                view.y = (event.rawY + touchOffset).coerceIn(0f, maxY)
                hint.alpha = 1f - ((restY - view.y) / restY).coerceIn(0f, 1f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val downRange = track.height - track.paddingBottom - view.height - restY
                val outcome = when {
                    event.actionMasked == MotionEvent.ACTION_CANCEL -> SwipeOutcome.NONE
                    view.y < restY -> swipeOutcome(restY, view.y, restY)
                    else -> swipeOutcome(restY, view.y, downRange)
                }
                when (outcome) {
                    SwipeOutcome.ANSWER -> {
                        finished = true
                        onAnswer()
                    }
                    SwipeOutcome.DECLINE -> {
                        finished = true
                        onDecline()
                    }
                    SwipeOutcome.NONE -> {
                        view.animate().y(restY).setDuration(250).start()
                        hint.animate().alpha(1f).setDuration(250).start()
                    }
                }
            }
        }
        return true
    }
}

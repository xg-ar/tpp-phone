// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * "Live" pulse of the green check (home and "Todo en orden"): a halo grows and fades behind it
 * while the check beats once per cycle. Whether it runs is decided by logic/LivePulse.kt.
 * The halo stays inside the card padding (max scale 1.55 on a 34-36dp icon).
 */
class LivePulseAnimator(private val halo: View, private val icon: View) {
    companion object {
        private const val CYCLE_MS = 1500L
    }

    private var animators: List<ObjectAnimator> = emptyList()

    val isRunning: Boolean
        get() = animators.isNotEmpty()

    fun start() {
        if (isRunning) return
        val haloPulse = ObjectAnimator.ofPropertyValuesHolder(
            halo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.55f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.55f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 0f)
        ).apply { interpolator = DecelerateInterpolator() }
        // Quick beat at the start of each cycle, then rest.
        val beat =
            arrayOf(Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.12f, 1.1f), Keyframe.ofFloat(0.3f, 1f), Keyframe.ofFloat(1f, 1f))
        val iconBeat = ObjectAnimator.ofPropertyValuesHolder(
            icon,
            PropertyValuesHolder.ofKeyframe(View.SCALE_X, *beat),
            PropertyValuesHolder.ofKeyframe(View.SCALE_Y, *beat)
        )
        animators = listOf(haloPulse, iconBeat).onEach {
            it.duration = CYCLE_MS
            it.repeatCount = ValueAnimator.INFINITE
            it.start()
        }
    }

    fun stop() {
        animators.forEach { it.cancel() }
        animators = emptyList()
        halo.alpha = 0f
        for (view in listOf(halo, icon)) {
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }

    fun update(run: Boolean) = if (run) start() else stop()
}

// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import ar.com.tpp.phone.logic.playedBars
import ar.com.tpp.phone.logic.seekFraction
import kotlin.math.abs

/**
 * WhatsApp-style voice waveform: one rounded bar per value (0..31), the played ones in brand red.
 * Tapping or dragging sideways seeks: the bars follow the finger, [onSeek] fires when it is lifted.
 * A vertical drag is left to the scroll view.
 */
class WaveformView
    @JvmOverloads
    constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    companion object {
        private const val MAX_VALUE = 31f
        private const val PLAYED_COLOR = 0xFFEE1B22.toInt()
        private const val REST_COLOR = 0xFFBDBDBD.toInt()
    }

    private val density = resources.displayMetrics.density
    private val minBarHeight = 3 * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var bars: List<Int> = emptyList()
    private var progress = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f

    var onSeek: ((Float) -> Unit)? = null

    fun setBars(values: List<Int>) {
        bars = values
        invalidate()
    }

    fun setProgress(value: Float) {
        if (dragging) return
        progress = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty() || width == 0) return
        val left = paddingLeft.toFloat()
        val usable = (width - paddingLeft - paddingRight).toFloat()
        val top = paddingTop.toFloat()
        val height = (this.height - paddingTop - paddingBottom).toFloat()
        val slot = usable / bars.size
        val barWidth = slot * 0.6f
        val radius = barWidth / 2
        val played = playedBars(progress, bars.size)
        bars.forEachIndexed { i, v ->
            val h = maxOf(minBarHeight, v / MAX_VALUE * height)
            val x = left + i * slot + (slot - barWidth) / 2
            val y = top + (height - h) / 2
            rect.set(x, y, x + barWidth, y + h)
            paint.color = if (i < played) PLAYED_COLOR else REST_COLOR
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onSeek == null) return false
        val fraction = seekFraction(event.x - paddingLeft, (width - paddingLeft - paddingRight).toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Not yet a seek drag: the scroll view may still take a vertical one (then ACTION_CANCEL).
                downX = event.x
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.x - downX) > touchSlop) {
                    // Sideways: keep the scroll view from taking it.
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragging = true
                }
                if (dragging) {
                    progress = fraction
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                // A tap or the end of a sideways drag.
                dragging = false
                progress = fraction
                invalidate()
                onSeek?.invoke(fraction)
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}

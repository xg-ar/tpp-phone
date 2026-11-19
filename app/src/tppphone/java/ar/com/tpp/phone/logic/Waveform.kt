// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

/** Bars of the voice message bubble already played (shown in brand red), for progress 0..1. */
fun playedBars(progress: Float, bars: Int): Int {
    if (progress.isNaN()) return 0
    return (progress.coerceIn(0f, 1f) * bars).toInt().coerceIn(0, bars)
}

/** Where a tap or drag at [x] on a waveform [width] wide lands, as a 0..1 fraction. */
fun seekFraction(x: Float, width: Float): Float {
    if (width <= 0f || x.isNaN()) return 0f
    return (x / width).coerceIn(0f, 1f)
}

/**
 * Audio focus change while a voice message plays: a short notification only lowers the volume
 * (the system ducks it); losing focus for a call or another app's audio pauses it.
 */
fun pausesOnFocusChange(change: Int): Boolean =
    change < 0 && change != android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK

/** MediaPlayer position over duration, 0..1. */
fun playbackProgress(positionMs: Int, durationMs: Int): Float {
    if (durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** "m:ss" elapsed while playing, from progress and the message's length. */
fun elapsedText(progress: Float, durationSec: Int): String {
    val sec = if (progress.isNaN()) 0 else (progress.coerceIn(0f, 1f) * durationSec).toInt()
    return "%d:%02d".format(sec / 60, sec % 60)
}

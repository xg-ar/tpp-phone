// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.home

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ar.com.tpp.phone.logic.pausesOnFocusChange
import ar.com.tpp.phone.logic.playbackProgress
import java.io.File
import org.linphone.core.tools.Log

/** [id] null: nothing loaded. [progress] 0..1. */
data class PlayerState(val id: String?, val progress: Float, val playing: Boolean)

/**
 * One MediaPlayer for all the voice messages: starting another message stops the previous one.
 * Plays the WebM/Opus file from the cache through the speaker (media stream). Main thread only.
 * Holds transient audio focus while playing: another app's audio or a cellular call ringing pauses it,
 * and it does not resume by itself. A short notification sound only lowers its volume.
 */
@MainThread
class VoicePlayer(context: Context) {
    companion object {
        private const val TAG = "[TPP Voice Player]"
        private const val TICK_MS = 100L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var currentId: String? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        // A short notification only lowers the volume: the system ducks it.
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener({ change -> if (pausesOnFocusChange(change)) pause() }, handler)
        .build()
    private var hasFocus = false

    private val _state = MutableLiveData(PlayerState(null, 0f, false))
    val state: LiveData<PlayerState> = _state

    private val ticker = object : Runnable {
        override fun run() {
            publish()
            if (player?.isPlaying == true) handler.postDelayed(this, TICK_MS)
        }
    }

    /** Resumes [id] if it is the loaded message, otherwise loads [file] and plays it from the start. */
    fun play(id: String, file: File): Boolean {
        val loaded = player
        if (loaded != null && id == currentId) {
            if (!requestFocus()) return false
            loaded.start()
            startTicking()
            return true
        }
        stop()
        // Denied, e.g. during a call: nothing plays.
        if (!requestFocus()) return false
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(file.path)
            mp.prepare() // a small local file
        } catch (e: Exception) {
            Log.e("$TAG Can't play voice message: ${e.javaClass.simpleName}")
            mp.release()
            abandonFocus()
            // A broken download: get it again next time.
            file.delete()
            _state.value = PlayerState(null, 0f, false)
            return false
        }
        // A decoding error mid-way: drop the player and the file, so the next tap downloads it again.
        // Returning true keeps Android from also calling the completion listener.
        mp.setOnErrorListener { failed, what, extra ->
            Log.e("$TAG Playback error [$what/$extra]")
            if (failed === player) {
                stop()
            } else {
                failed.release()
            }
            file.delete()
            true
        }
        // Back to the start once played, as WhatsApp does.
        mp.setOnCompletionListener {
            handler.removeCallbacks(ticker)
            if (it !== player) return@setOnCompletionListener
            abandonFocus()
            try {
                it.seekTo(0)
            } catch (e: IllegalStateException) {
                stop()
                return@setOnCompletionListener
            }
            _state.value = PlayerState(currentId, 0f, false)
        }
        player = mp
        currentId = id
        mp.start()
        startTicking()
        return true
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        handler.removeCallbacks(ticker)
        abandonFocus()
        publish()
    }

    /** Seeks the loaded message; [fraction] 0..1. */
    fun seekTo(fraction: Float) {
        val mp = player ?: return
        val duration = mp.duration
        if (duration <= 0) return
        mp.seekTo((fraction.coerceIn(0f, 1f) * duration).toInt())
        publish(fraction.coerceIn(0f, 1f))
    }

    /** Unloads the message (e.g. it was deleted). */
    fun stop() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        currentId = null
        abandonFocus()
        _state.value = PlayerState(null, 0f, false)
    }

    fun release() = stop()

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) Log.w("$TAG Audio focus denied")
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun startTicking() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun publish(progress: Float? = null) {
        val mp = player ?: return
        _state.value = PlayerState(
            currentId,
            progress ?: playbackProgress(mp.currentPosition, mp.duration),
            mp.isPlaying
        )
    }
}

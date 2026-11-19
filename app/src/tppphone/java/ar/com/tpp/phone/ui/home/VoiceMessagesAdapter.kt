// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ar.com.tpp.phone.logic.VoiceMessageRow
import ar.com.tpp.phone.logic.elapsedText
import ar.com.tpp.phone.prov.VoiceMessage
import org.linphone.R
import org.linphone.databinding.TppVoiceMessageCellBinding

/** A message and its texts (the sender's contact name included, once Task 11 resolves it). */
data class VoiceMessageItem(val message: VoiceMessage, val row: VoiceMessageRow)

class VoiceMessagesAdapter(
    private val onPlayPause: (VoiceMessage) -> Unit,
    private val onSeek: (VoiceMessage, Float) -> Unit,
    private val onLongPress: (VoiceMessage) -> Unit
) : ListAdapter<VoiceMessageItem, VoiceMessagesAdapter.Holder>(Diff()) {
    private companion object {
        const val PLAYBACK = "playback"
    }

    class Holder(val binding: TppVoiceMessageCellBinding) : RecyclerView.ViewHolder(binding.root)

    private var player = PlayerState(null, 0f, false)
    private var downloading: Set<String> = emptySet()

    fun setPlayerState(state: PlayerState) {
        val before = player
        player = state
        refreshPlayback(setOfNotNull(before.id, state.id))
    }

    fun setDownloading(ids: Set<String>) {
        val before = downloading
        downloading = ids
        refreshPlayback(before + ids)
    }

    private fun refreshPlayback(ids: Set<String>) {
        if (ids.isEmpty()) return
        currentList.forEachIndexed { i, item -> if (item.message.id in ids) notifyItemChanged(i, PLAYBACK) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(TppVoiceMessageCellBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.row = item.row
        holder.binding.isLast = position == itemCount - 1
        holder.binding.waveform.setBars(item.message.waveform)
        holder.binding.playButton.setOnClickListener { onPlayPause(item.message) }
        val longPress = View.OnLongClickListener {
            onLongPress(item.message)
            true
        }
        holder.binding.bubble.setOnLongClickListener(longPress)
        holder.binding.playButton.setOnLongClickListener(longPress)
        bindPlayback(holder, item)
        holder.binding.executePendingBindings()
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PLAYBACK }) {
            bindPlayback(holder, getItem(position))
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private fun bindPlayback(holder: Holder, item: VoiceMessageItem) {
        val b = holder.binding
        val m = item.message
        val loaded = player.id == m.id
        val playing = loaded && player.playing
        val isDownloading = m.id in downloading
        b.downloading.visibility = if (isDownloading) View.VISIBLE else View.GONE
        b.playIcon.visibility = if (isDownloading) View.INVISIBLE else View.VISIBLE
        b.playIcon.setImageResource(if (playing) R.drawable.tpp_ic_pause else R.drawable.tpp_ic_play)
        b.playButton.contentDescription = b.root.context.getString(
            if (playing) R.string.tpp_voice_pause else R.string.tpp_voice_play
        )
        b.waveform.setProgress(if (loaded) player.progress else 0f)
        // Only the loaded message seeks; on the others a touch is a plain tap or long press on the row.
        b.waveform.onSeek = if (loaded) { f -> onSeek(m, f) } else null
        b.duration.text = if (playing || (loaded && player.progress > 0f)) {
            elapsedText(player.progress, m.durationSec)
        } else {
            item.row.durationText
        }
    }

    private class Diff : DiffUtil.ItemCallback<VoiceMessageItem>() {
        override fun areItemsTheSame(oldItem: VoiceMessageItem, newItem: VoiceMessageItem) =
            oldItem.message.id == newItem.message.id

        override fun areContentsTheSame(oldItem: VoiceMessageItem, newItem: VoiceMessageItem) = oldItem == newItem
    }
}

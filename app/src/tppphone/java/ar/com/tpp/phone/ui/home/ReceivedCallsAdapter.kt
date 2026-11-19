// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ar.com.tpp.phone.logic.ReceivedCallRow
import org.linphone.databinding.TppReceivedCallCellBinding

/** Read-only list: no call back, no menu. */
class ReceivedCallsAdapter : ListAdapter<ReceivedCallRow, ReceivedCallsAdapter.Holder>(Diff()) {
    class Holder(val binding: TppReceivedCallCellBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(TppReceivedCallCellBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.row = getItem(position)
        holder.binding.isLast = position == itemCount - 1
        holder.binding.executePendingBindings()
    }

    private class Diff : DiffUtil.ItemCallback<ReceivedCallRow>() {
        override fun areItemsTheSame(oldItem: ReceivedCallRow, newItem: ReceivedCallRow) = oldItem == newItem

        override fun areContentsTheSame(oldItem: ReceivedCallRow, newItem: ReceivedCallRow) = oldItem == newItem
    }
}

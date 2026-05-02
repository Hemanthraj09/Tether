package com.tether.app.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tether.app.databinding.ItemFeedCardBinding

class FeedAdapter(private var items: List<FeedItem>) :
    RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    fun updateItems(newItems: List<FeedItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class FeedViewHolder(val binding: ItemFeedCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val binding = ItemFeedCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvUserName.text = item.name
            tvAvatarInitials.text = item.initials
            tvHours.text = formatHours(item.hours)
            tvNote.text = item.note
            tvTimeAgo.text = item.timeAgo
            
            flAvatar.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor(item.avatarColorHex)
            )
        }
    }

    private fun formatHours(hours: Double): String {
        val totalMinutes = (hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    override fun getItemCount() = items.size
}
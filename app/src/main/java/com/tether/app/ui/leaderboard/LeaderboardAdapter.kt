package com.tether.app.ui.leaderboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tether.app.R
import com.tether.app.databinding.ItemLeaderboardRowBinding

class LeaderboardAdapter(
    private val groupId: String,
    private var items: List<LeaderboardItem>,
    private val onNudge: (String, String) -> Unit
) :
    RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    fun updateItems(newItems: List<LeaderboardItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun markNudged(nudgedUid: String) {
        val index = items.indexOfFirst { it.uid == nudgedUid }
        if (index != -1) {
            items = items.toMutableList().also {
                it[index] = it[index].copy(hasNudgedToday = true)
            }
            notifyItemChanged(index)
        }
    }

    class LeaderboardViewHolder(val binding: ItemLeaderboardRowBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = ItemLeaderboardRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context

        with(holder.binding) {
            tvRank.text = "#${position + 1}"
            
            val rankColor = when (position) {
                0 -> Color.parseColor("#FFD700") // Gold
                1 -> Color.parseColor("#C0C0C0") // Silver
                2 -> Color.parseColor("#CD7F32") // Bronze
                else -> ContextCompat.getColor(context, R.color.colorTextSecondary)
            }
            tvRank.setTextColor(rankColor)

            tvAvatarInitials.text = item.initials
            flAvatar.backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.avatarColorHex))

            if (position < 3) {
                ivTrophyBadge.visibility = View.VISIBLE
                ivTrophyBadge.imageTintList = ColorStateList.valueOf(rankColor)
            } else {
                ivTrophyBadge.visibility = View.GONE
            }

            tvUserName.text = if (item.isCurrentUser) "${item.name} (You)" else item.name
            tvUserName.setTextColor(
                if (item.isCurrentUser) ContextCompat.getColor(context, R.color.colorAccent)
                else ContextCompat.getColor(context, R.color.colorTextPrimary)
            )

            tvStreak.text = "${item.streak} ${context.getString(R.string.day_streak)}"
            tvHours.text = formatHours(item.hours)

            if (!item.isCurrentUser && groupId.isNotEmpty()) {
                flAvatar.setOnClickListener {
                    if (!item.hasNudgedToday) {
                        onNudge(groupId, item.uid)
                    } else {
                        android.widget.Toast.makeText(
                            context, "Already nudged today ✓", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                // Subtle ring to indicate tappable for other users
                if (item.hasNudgedToday) {
                    flAvatar.alpha = 0.5f
                } else {
                    flAvatar.alpha = 1.0f
                }
            } else {
                flAvatar.setOnClickListener(null)
                flAvatar.alpha = 1.0f
            }

            if (item.paceLabel.isNotEmpty()) {
                tvPaceLabel.visibility = View.VISIBLE
                tvPaceLabel.text = item.paceLabel
            } else {
                tvPaceLabel.visibility = View.GONE
            }

            if (item.isCurrentUser) {
                root.background = ContextCompat.getDrawable(context, R.drawable.bg_leaderboard_row_active)
            } else {
                root.background = ContextCompat.getDrawable(context, R.drawable.bg_leaderboard_row)
            }
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
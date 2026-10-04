package com.tether.app.ui.leaderboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tether.app.R
import com.tether.app.databinding.ItemLeaderboardRowBinding
import com.tether.app.utils.Formatters
import com.tether.app.utils.TetherToast

/**
 * Ranked member list (group feed + leaderboard tab).
 * DiffUtil only rebinds rows that changed and animates rank changes.
 * The rank is item.id (1-based), so moved rows always show the right number.
 */
class LeaderboardAdapter(
    private val onNudge: ((LeaderboardItem) -> Unit)?
) : ListAdapter<LeaderboardItem, LeaderboardAdapter.LeaderboardViewHolder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<LeaderboardItem>() {
        override fun areItemsTheSame(oldItem: LeaderboardItem, newItem: LeaderboardItem) =
            oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: LeaderboardItem, newItem: LeaderboardItem) =
            oldItem == newItem
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
        val item = getItem(position)
        val context = holder.binding.root.context
        val rankIndex = item.id - 1

        with(holder.binding) {
            tvRank.text = "#${item.id}"

            val rankColor = when (rankIndex) {
                0 -> Color.parseColor("#FFD700") // Gold
                1 -> Color.parseColor("#C0C0C0") // Silver
                2 -> Color.parseColor("#CD7F32") // Bronze
                else -> ContextCompat.getColor(context, R.color.colorTextSecondary)
            }
            tvRank.setTextColor(rankColor)

            tvAvatarInitials.text = item.initials
            flAvatar.backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.avatarColorHex))

            if (rankIndex < 3) {
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
            tvHours.text = Formatters.formatHours(item.hours)

            if (!item.isCurrentUser && onNudge != null) {
                flAvatar.setOnClickListener {
                    if (!item.hasNudgedToday) {
                        onNudge.invoke(item)
                    } else {
                        TetherToast.show(context, "Already nudged today ✓")
                    }
                }
                // Dimmed avatar = already nudged today
                flAvatar.alpha = if (item.hasNudgedToday) 0.5f else 1.0f
            } else {
                flAvatar.setOnClickListener(null)
                flAvatar.isClickable = false
                flAvatar.alpha = 1.0f
            }

            if (item.paceLabel.isNotEmpty()) {
                tvPaceLabel.visibility = View.VISIBLE
                tvPaceLabel.text = item.paceLabel
            } else {
                tvPaceLabel.visibility = View.GONE
            }

            root.background = ContextCompat.getDrawable(
                context,
                if (item.isCurrentUser) R.drawable.bg_leaderboard_row_active
                else R.drawable.bg_leaderboard_row
            )
        }
    }
}

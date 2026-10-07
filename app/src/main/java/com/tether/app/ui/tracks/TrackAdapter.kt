package com.tether.app.ui.tracks

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tether.app.R
import com.tether.app.databinding.ItemTrackItemBinding
import com.tether.app.databinding.ItemTrackSectionBinding
import com.tether.app.domain.CompletionSource

class TrackAdapter(
    private val onOpen: (TrackRow.Item) -> Unit,
    private val onToggle: (TrackRow.Item) -> Unit
) : ListAdapter<TrackRow, RecyclerView.ViewHolder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<TrackRow>() {
        override fun areItemsTheSame(oldItem: TrackRow, newItem: TrackRow): Boolean = when {
            oldItem is TrackRow.Header && newItem is TrackRow.Header -> oldItem.name == newItem.name
            oldItem is TrackRow.Item && newItem is TrackRow.Item -> oldItem.item.key == newItem.item.key &&
                    oldItem.item.title == newItem.item.title
            else -> false
        }
        override fun areContentsTheSame(oldItem: TrackRow, newItem: TrackRow) = oldItem == newItem
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position) is TrackRow.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemTrackSectionBinding.inflate(inflater, parent, false))
        } else {
            ItemHolder(ItemTrackItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is TrackRow.Header -> (holder as HeaderHolder).bind(row)
            is TrackRow.Item -> (holder as ItemHolder).bind(row)
        }
    }

    class HeaderHolder(private val b: ItemTrackSectionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: TrackRow.Header) {
            b.tvSectionName.text = row.name
            b.tvSectionCount.text = "${row.done} / ${row.total}"
        }
    }

    inner class ItemHolder(private val b: ItemTrackItemBinding) : RecyclerView.ViewHolder(b.root) {
        private val dots: List<TextView> = listOf(b.solver1, b.solver2, b.solver3, b.solver4, b.solver5, b.solver6)

        fun bind(row: TrackRow.Item) {
            val context = b.root.context
            b.tvItemTitle.text = row.item.title

            val difficulty = row.item.difficulty
            b.tvItemDifficulty.visibility = if (difficulty == null) View.GONE else View.VISIBLE
            b.tvItemDifficulty.text = difficulty
            b.tvItemDifficulty.setTextColor(difficultyColor(difficulty))

            b.tvItemSource.text = when {
                row.item.isVerifiable -> "LeetCode"
                row.item.url != null -> "GFG"
                else -> "Manual"
            }

            dots.forEachIndexed { i, dot ->
                val solver = row.solvers.getOrNull(i)
                dot.visibility = if (solver == null) View.GONE else View.VISIBLE
                if (solver != null) {
                    dot.text = solver.first
                    dot.backgroundTintList = ColorStateList.valueOf(Color.parseColor(solver.second))
                }
            }

            val (background, tint, alpha) = when (row.status) {
                CompletionSource.LEETCODE -> Triple(R.drawable.bg_status_verified, Color.WHITE, 1f)
                CompletionSource.SELF -> Triple(R.drawable.bg_status_self, ContextCompat.getColor(context, R.color.colorAccent), 1f)
                null -> Triple(R.drawable.bg_status_none, Color.TRANSPARENT, 1f)
            }
            b.ivItemStatus.setBackgroundResource(background)
            b.ivItemStatus.imageTintList = ColorStateList.valueOf(tint)
            b.ivItemStatus.alpha = alpha
            b.btnItemStatus.contentDescription = when (row.status) {
                CompletionSource.LEETCODE -> "Verified on LeetCode"
                CompletionSource.SELF -> "Marked done, tap to undo"
                null -> "Mark as done"
            }

            b.root.setOnClickListener { onOpen(row) }
            b.btnItemStatus.setOnClickListener { onToggle(row) }
        }

        private fun difficultyColor(difficulty: String?): Int = when (difficulty?.lowercase()) {
            "easy", "basic" -> Color.parseColor("#22C55E")
            "medium", "core" -> Color.parseColor("#EAB308")
            "hard", "pro" -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#888888")
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}

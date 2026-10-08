package com.tether.app.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tether.app.R
import com.tether.app.data.repository.TodayLog
import com.tether.app.data.repository.TodayRepository
import com.tether.app.databinding.ItemTodayLogBinding
import com.tether.app.domain.Proof
import com.tether.app.utils.Formatters
import java.util.Date

/**
 * The group's "Today" list. Photos are loaded lazily through [bindPhoto]
 * (one small Firestore read per photo, cached), so the list itself only
 * carries text.
 */
class TodayLogAdapter(
    private val onReact: (TodayLog, String?) -> Unit,
    private val bindPhoto: (TodayLog, ImageView, status: TextView) -> Unit
) : ListAdapter<TodayLog, TodayLogAdapter.ViewHolder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<TodayLog>() {
        override fun areItemsTheSame(oldItem: TodayLog, newItem: TodayLog) = oldItem.log.id == newItem.log.id
        override fun areContentsTheSame(oldItem: TodayLog, newItem: TodayLog) = oldItem == newItem
    }

    class ViewHolder(val binding: ItemTodayLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemTodayLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val log = item.log
        val context = holder.binding.root.context
        with(holder.binding) {
            tvAvatarInitials.text = log.userInitials
            runCatching { flAvatar.backgroundTintList = ColorStateList.valueOf(Color.parseColor(log.avatarColorHex)) }

            val name = if (item.isMine) "You" else log.userName
            tvHeadline.text = "$name · ${Formatters.formatHours(log.value)}"
            val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(log.createdAt))
            tvMeta.text = when (log.source) {
                Proof.SOURCE_TIMER -> "$time · ⏱ focus session"
                else -> if (log.hasPhoto) "$time · 📷 with photo" else time
            }
            tvNote.text = log.note
            tvNote.visibility = if (log.note.isBlank()) View.GONE else View.VISIBLE

            ivPhoto.setImageDrawable(null)
            ivPhoto.tag = log.id
            if (log.hasPhoto) {
                flPhoto.visibility = View.VISIBLE
                tvPhotoStatus.text = "📷 Loading photo…"
                bindPhoto(item, ivPhoto, tvPhotoStatus)
            } else {
                flPhoto.visibility = View.GONE
            }

            bindReaction(tvReactOk, "✓", item.okCount, item.myReaction == TodayRepository.OK, item.isMine) {
                onReact(item, if (item.myReaction == TodayRepository.OK) null else TodayRepository.OK)
            }
            bindReaction(tvReactDoubt, "🤨", item.doubtCount, item.myReaction == TodayRepository.DOUBT, item.isMine) {
                onReact(item, if (item.myReaction == TodayRepository.DOUBT) null else TodayRepository.DOUBT)
            }
            // Your own log: only show reactions you've received.
            rowReactions.visibility =
                if (item.isMine && item.okCount == 0 && item.doubtCount == 0) View.GONE else View.VISIBLE
        }
    }

    private fun bindReaction(
        view: TextView,
        symbol: String,
        count: Int,
        selected: Boolean,
        isMine: Boolean,
        onClick: () -> Unit
    ) {
        view.text = if (count > 0) "$symbol $count" else symbol
        view.setBackgroundResource(if (selected) R.drawable.bg_reaction_selected else R.drawable.bg_reaction)
        // You can't react to your own log (also enforced by the security rules).
        if (isMine) {
            view.visibility = if (count > 0) View.VISIBLE else View.GONE
            view.setOnClickListener(null)
            view.isClickable = false
        } else {
            view.visibility = View.VISIBLE
            view.setOnClickListener { onClick() }
        }
    }
}

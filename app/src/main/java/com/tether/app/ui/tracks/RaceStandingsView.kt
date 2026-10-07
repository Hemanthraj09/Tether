package com.tether.app.ui.tracks

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.tether.app.R
import com.tether.app.databinding.ItemRaceMemberBinding

/** Renders race standings (≤ 6 rows) into a LinearLayout; shared by the group card and track header. */
object RaceStandingsView {

    fun render(
        container: LinearLayout,
        standings: List<RaceStanding>,
        leetCodeTotals: Map<String, Int> = emptyMap()
    ) {
        val inflater = LayoutInflater.from(container.context)
        // Reuse existing rows; add or remove only the difference.
        while (container.childCount > standings.size) container.removeViewAt(container.childCount - 1)
        while (container.childCount < standings.size) {
            container.addView(ItemRaceMemberBinding.inflate(inflater, container, false).root)
        }

        standings.forEachIndexed { index, standing ->
            val b = ItemRaceMemberBinding.bind(container.getChildAt(index))
            val member = standing.member
            b.tvRaceInitials.text = member.initials
            b.tvRaceInitials.backgroundTintList = ColorStateList.valueOf(Color.parseColor(member.avatarColorHex))
            b.tvRaceName.text = if (standing.isMe) "${member.name} (You)" else member.name
            b.tvRaceName.setTextColor(ContextCompat.getColor(container.context,
                if (standing.isMe) R.color.colorAccent else R.color.colorTextPrimary))
            b.tvRaceCount.text = "${standing.progress.done} / ${standing.progress.total}"
            b.progressRace.setProgressCompat((standing.progress.fraction * 1000).toInt(), true)

            val total = leetCodeTotals[member.uid]
            b.tvRaceSubtitle.visibility = View.VISIBLE
            b.tvRaceSubtitle.text = when {
                member.leetcodeUsername == null -> "LeetCode not connected"
                total != null -> "$total solved on LeetCode"
                else -> "@${member.leetcodeUsername}"
            }
        }
    }
}

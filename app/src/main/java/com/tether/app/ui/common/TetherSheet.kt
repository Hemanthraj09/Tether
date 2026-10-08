package com.tether.app.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tether.app.R
import com.tether.app.databinding.ItemSheetOptionBinding
import com.tether.app.databinding.SheetOptionsBinding

/** One row of a [TetherSheet]: an icon (drawable or emoji), label and optional description. */
data class SheetOption(
    val label: String,
    val description: String? = null,
    @param:DrawableRes val icon: Int? = null,
    val emoji: String? = null,
    /** Shows a ✓ (for "pick one" sheets such as the photo mode). */
    val selected: Boolean = false,
    /** Red row for delete / leave / disconnect. */
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Tether's menus and choices as a bottom sheet, matching the log sheet:
 * rounded dark surface, drag handle, large touch targets with icons and a
 * short description for each option. Replaces AlertDialog item lists.
 */
object TetherSheet {

    fun show(
        context: Context,
        title: String,
        options: List<SheetOption>,
        subtitle: String? = null,
        /** Large monospace text, e.g. an invite code. */
        headline: String? = null
    ): BottomSheetDialog {
        val dialog = BottomSheetDialog(context, R.style.BottomSheetDialogTheme)
        val inflater = LayoutInflater.from(context)
        val binding = SheetOptionsBinding.inflate(inflater)

        binding.tvSheetTitle.text = title
        binding.tvSheetSubtitle.text = subtitle
        binding.tvSheetSubtitle.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvSheetHeadline.text = headline
        binding.tvSheetHeadline.visibility = if (headline.isNullOrBlank()) View.GONE else View.VISIBLE

        val danger = ContextCompat.getColor(context, R.color.colorDanger)
        options.forEach { option ->
            val row = ItemSheetOptionBinding.inflate(inflater, binding.sheetOptions, true)
            row.tvOptionLabel.text = option.label
            row.tvOptionDescription.text = option.description
            row.tvOptionDescription.visibility = if (option.description.isNullOrBlank()) View.GONE else View.VISIBLE
            when {
                option.icon != null -> {
                    row.ivOptionIcon.setImageResource(option.icon)
                    row.ivOptionIcon.visibility = View.VISIBLE
                    if (option.destructive) row.ivOptionIcon.imageTintList = ColorStateList.valueOf(danger)
                }
                option.emoji != null -> {
                    row.tvOptionEmoji.text = option.emoji
                    row.tvOptionEmoji.visibility = View.VISIBLE
                }
                else -> row.flOptionIcon.visibility = View.GONE
            }
            row.flOptionIcon.setBackgroundResource(when {
                option.destructive -> R.drawable.bg_icon_container_danger
                option.selected -> R.drawable.bg_icon_container_accent
                else -> R.drawable.bg_icon_container
            })
            if (option.destructive) row.tvOptionLabel.setTextColor(danger)
            row.ivOptionCheck.visibility = if (option.selected) View.VISIBLE else View.GONE
            row.root.contentDescription =
                listOfNotNull(option.label, option.description, if (option.selected) "selected" else null)
                    .joinToString(", ")
            row.root.setOnClickListener {
                dialog.dismiss()
                option.onClick()
            }
        }

        dialog.setContentView(binding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
        return dialog
    }
}

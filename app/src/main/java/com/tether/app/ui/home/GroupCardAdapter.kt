package com.tether.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tether.app.R
import com.tether.app.data.model.Group
import com.tether.app.databinding.ItemGroupCardBinding

class GroupCardAdapter(
    private val groups: List<Group>,
    private val onGroupClick: (Group) -> Unit,
    private val onGroupLongPress: (Group) -> Unit
) : RecyclerView.Adapter<GroupCardAdapter.GroupCardViewHolder>() {

    inner class GroupCardViewHolder(
        val binding: ItemGroupCardBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GroupCardViewHolder {
        val binding = ItemGroupCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false)
        return GroupCardViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: GroupCardViewHolder,
        position: Int
    ) {
        val group = groups[position]
        val binding = holder.binding

        binding.tvGroupName.text = group.name
        binding.tvGroupGoal.text = group.goalType
        binding.tvMemberCount.text =
            "${group.members.size} member" +
            if (group.members.size != 1) "s" else ""

        val goalIcon = when (group.goalType
            .lowercase()) {
            "gym" -> R.drawable.ic_fitness
            "coding" -> R.drawable.ic_code
            "other" -> R.drawable.ic_sparkle
            else -> R.drawable.ic_book
        }
        binding.ivGroupGoalIcon.setImageResource(
            goalIcon)

        binding.root.setOnClickListener {
            onGroupClick(group)
        }

        binding.root.setOnLongClickListener {
            onGroupLongPress(group)
            true
        }
    }

    override fun getItemCount() = groups.size
}

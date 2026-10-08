package com.tether.app.ui.leaderboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tether.app.R
import com.tether.app.data.repository.LeaderboardEntry
import com.tether.app.databinding.FragmentLeaderboardBinding
import com.tether.app.ui.common.SheetOption
import com.tether.app.ui.common.TetherSheet
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LeaderboardViewModel by viewModels()

    private lateinit var adapter: LeaderboardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(
            inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        adapter = LeaderboardAdapter { item ->
            TetherToast.show(requireContext(), "Nudge sent! ⚡")
            viewModel.sendNudge(item.uid)
        }
        binding.leaderboardRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.leaderboardRecyclerView.adapter = adapter

        binding.btnToday.setOnClickListener { viewModel.setWeeklyMode(false) }
        binding.btnWeekly.setOnClickListener { viewModel.setWeeklyMode(true) }
        binding.tvLeaderboardGroup.setOnClickListener { showGroupPicker() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.groups, viewModel.currentGroup) { groups, current ->
                        groups to current
                    }.collect { (groups, current) ->
                        val multiple = (groups?.size ?: 0) > 1
                        binding.tvLeaderboardGroup.visibility =
                            if (current != null) View.VISIBLE else View.INVISIBLE
                        binding.tvLeaderboardGroup.text =
                            if (multiple) "${current?.name} ▾" else current?.name ?: ""
                        binding.tvLeaderboardGroup.isClickable = multiple
                        binding.tvLeaderboardEmpty.visibility =
                            if (groups != null && groups.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    combine(viewModel.entries, viewModel.weeklyMode) { entries, weekly ->
                        entries to weekly
                    }.collect { (entries, weekly) ->
                        renderToggle(weekly)
                        if (entries != null) render(entries, weekly)
                    }
                }
                launch {
                    viewModel.messages.collect { message ->
                        TetherToast.show(requireContext(), message, isError = true)
                    }
                }
            }
        }
    }

    private fun render(
        entries: List<LeaderboardEntry>,
        weekly: Boolean
    ) {
        // Coding groups may rank by verified LeetCode solves (hours break ties).
        val bySolves = entries.any { it.rankedBySolves }
        val sorted = when {
            bySolves && weekly -> entries.sortedWith(compareByDescending<LeaderboardEntry> { it.solvedWeek ?: 0 }.thenByDescending { it.hours })
            bySolves -> entries.sortedWith(compareByDescending<LeaderboardEntry> { it.solvedToday ?: 0 }.thenByDescending { it.todayHours })
            weekly -> entries.sortedByDescending { it.hours }
            else -> entries.sortedByDescending { it.todayHours }
        }
        adapter.submitList(sorted.toLeaderboardItems(weekly))
    }

    private fun renderToggle(weekly: Boolean) {
        val active = ContextCompat.getDrawable(requireContext(), R.drawable.bg_toggle_active)
        val primary = ContextCompat.getColor(requireContext(), R.color.colorTextPrimary)
        val secondary = ContextCompat.getColor(requireContext(), R.color.colorTextSecondary)
        binding.btnWeekly.background = if (weekly) active else null
        binding.btnWeekly.setTextColor(if (weekly) primary else secondary)
        binding.btnToday.background = if (weekly) null else active
        binding.btnToday.setTextColor(if (weekly) secondary else primary)
    }

    private fun showGroupPicker() {
        val groups = viewModel.groups.value ?: return
        if (groups.size <= 1) return
        val current = viewModel.currentGroup.value?.id
        TetherSheet.show(
            requireContext(),
            title = "Switch group",
            options = groups.map { group ->
                val count = group.members.size
                SheetOption(group.name, "${group.goalType} • $count member" + if (count != 1) "s" else "",
                    icon = R.drawable.ic_group, selected = group.id == current) { viewModel.selectGroup(group.id) }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

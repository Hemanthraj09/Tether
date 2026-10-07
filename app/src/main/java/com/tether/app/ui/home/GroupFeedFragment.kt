package com.tether.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tether.app.R
import com.tether.app.databinding.FragmentGroupFeedBinding
import com.tether.app.timer.TetherTimerService
import com.tether.app.timer.TimerControlFragment
import com.tether.app.timer.TimerModeDialogFragment
import com.tether.app.timer.TimerNoteDialogFragment
import com.tether.app.ui.leaderboard.LeaderboardAdapter
import com.tether.app.ui.leaderboard.toLeaderboardItems
import com.tether.app.ui.tracks.RaceStandingsView
import com.tether.app.utils.navigateSafe
import androidx.core.os.bundleOf
import com.tether.app.ui.log.LogBottomSheetFragment
import com.tether.app.utils.TetherToast
import com.tether.app.utils.showOnce
import kotlinx.coroutines.launch

class GroupFeedFragment : Fragment() {

    private var _binding: FragmentGroupFeedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GroupFeedViewModel by viewModels()

    private lateinit var membersAdapter: LeaderboardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Header from the navigation args right away; the live group doc refines it.
        binding.tvFeedGroupName.text = arguments?.getString("groupName") ?: ""
        binding.tvFeedGroupGoal.text = arguments?.getString("groupGoal") ?: ""
        binding.btnGroupInfo.visibility = View.GONE

        membersAdapter = LeaderboardAdapter { item ->
            TetherToast.show(requireContext(), "Nudge sent! ⚡")
            viewModel.sendNudge(item.uid)
        }
        binding.membersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.membersRecyclerView.adapter = membersAdapter

        binding.btnFeedBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnFeedMore.setOnClickListener {
            showGroupOptionsMenu()
        }

        binding.btnGroupInfo.setOnClickListener {
            showInviteCode()
        }

        binding.fabLogFeed.setOnClickListener {
            LogBottomSheetFragment.newInstance(viewModel.groupId)
                .showOnce(childFragmentManager, LogBottomSheetFragment.TAG)
        }

        binding.btnStartSession.setOnClickListener {
            if (TetherTimerService.isRunning) {
                TimerControlFragment.newInstance()
                    .showOnce(childFragmentManager, TimerControlFragment.TAG)
            } else {
                TimerModeDialogFragment.newInstance(viewModel.groupId)
                    .showOnce(childFragmentManager, TimerModeDialogFragment.TAG)
            }
        }

        // The control sheet reports the stopped session here; the note dialog
        // logs it to the group the session belongs to.
        childFragmentManager.setFragmentResultListener(
            TimerControlFragment.RESULT_TIMER_STOPPED, viewLifecycleOwner
        ) { _, result ->
            val focusSeconds = result.getLong(TimerControlFragment.RESULT_FOCUS_SECONDS, 0L)
            val sessionGroupId = result.getString(TimerControlFragment.RESULT_GROUP_ID)
                ?.takeIf { it.isNotEmpty() } ?: viewModel.groupId
            TimerNoteDialogFragment.newInstance(focusSeconds, sessionGroupId)
                .showOnce(childFragmentManager, TimerNoteDialogFragment.TAG)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.group.collect { group ->
                        if (group == null) return@collect
                        binding.tvFeedGroupName.text = group.name
                        val count = group.members.size
                        binding.tvFeedGroupGoal.text =
                            "${group.goalType} • $count member" + if (count != 1) "s" else ""
                        binding.btnGroupInfo.visibility =
                            if (group.inviteCode.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.memberStats.collect { stats ->
                        if (stats != null) membersAdapter.submitList(stats.toLeaderboardItems())
                    }
                }
                launch { viewModel.race.collect { renderRace(it) } }
                launch {
                    viewModel.leetCodeTotals.collect { totals ->
                        val standings = viewModel.race.value?.standings.orEmpty()
                        if (standings.isNotEmpty()) RaceStandingsView.render(binding.layoutRaceMembers, standings, totals)
                    }
                }
                launch {
                    TetherTimerService.activeGroupId.collect { active ->
                        updateSessionButton(isRunning = active != null)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is GroupFeedEvent.Message ->
                                TetherToast.show(requireContext(), event.text, event.isError)
                            GroupFeedEvent.LeftGroup -> {
                                TetherToast.show(requireContext(), "Done!")
                                findNavController().popBackStack(R.id.groupListFragment, false)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Track race ───────────────────────────────────────────────

    private fun renderRace(state: RaceCardState?) {
        if (state == null || !state.available) {
            binding.cardTrackRace.visibility = View.GONE
            return
        }
        binding.cardTrackRace.visibility = View.VISIBLE
        val track = state.track
        if (track == null) {
            binding.tvRaceTitle.text = "🏁 Track race"
            binding.tvRaceSubtitleCard.visibility = View.VISIBLE
            binding.tvRaceSubtitleCard.text = if (state.isCreator)
                "Pick a track and work through it together. LeetCode solves tick off automatically."
            else "No track yet. The group creator can start one."
            binding.btnRaceAction.visibility = if (state.isCreator) View.VISIBLE else View.GONE
            binding.btnRaceAction.text = "Start"
            binding.layoutRaceMembers.removeAllViews()
            binding.cardTrackRace.setOnClickListener(null)
            binding.cardTrackRace.isClickable = false
        } else {
            binding.tvRaceTitle.text = "🏁 ${track.name}"
            binding.tvRaceSubtitleCard.visibility = View.GONE
            binding.btnRaceAction.visibility = View.VISIBLE
            binding.btnRaceAction.text = "Open"
            RaceStandingsView.render(binding.layoutRaceMembers, state.standings, viewModel.leetCodeTotals.value)
            binding.cardTrackRace.setOnClickListener { openTrack(track.id) }
            if (state.isCreator) {
                binding.cardTrackRace.setOnLongClickListener { showTrackPicker(); true }
            }
        }
        binding.btnRaceAction.setOnClickListener {
            if (track == null) showTrackPicker() else openTrack(track.id)
        }
    }

    private fun openTrack(trackId: String) {
        findNavController().navigateSafe(
            R.id.action_feed_to_track,
            bundleOf("trackId" to trackId, "groupId" to viewModel.groupId)
        )
    }

    private fun showTrackPicker() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tracks = viewModel.tracksForGroup()
            if (_binding == null || tracks.isEmpty()) return@launch
            val labels = tracks.map { "${it.name}  ·  ${it.itemCount} items" }.toMutableList()
            val hasTrack = viewModel.race.value?.track != null
            if (hasTrack) labels.add("End race")
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(if (hasTrack) "Change track" else "Choose a track")
                .setItems(labels.toTypedArray()) { _, which ->
                    viewModel.setTrack(if (which < tracks.size) tracks[which].id else "")
                }
                .show()
        }
    }

    private fun updateSessionButton(isRunning: Boolean) {
        if (isRunning) {
            binding.btnStartSession.text = "Session Active ●"
            binding.btnStartSession.setChipBackgroundColorResource(R.color.colorSurface)
        } else {
            binding.btnStartSession.text = "Start Session"
            binding.btnStartSession.setChipBackgroundColorResource(R.color.colorAccent)
        }
    }

    private fun showInviteCode() {
        val code = viewModel.group.value?.inviteCode ?: return
        if (code.isEmpty()) return
        val popup = android.widget.PopupMenu(requireContext(), binding.btnGroupInfo)
        popup.menu.add("Invite Code: $code  (tap to copy)")
        popup.setOnMenuItemClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Tether invite code", code))
            TetherToast.show(requireContext(), "Invite code copied")
            true
        }
        popup.show()
    }

    private fun showGroupOptionsMenu() {
        // Wait for the group to load so the creator never sees "Leave".
        if (viewModel.group.value == null) return
        val groupName = binding.tvFeedGroupName.text.toString()
        if (viewModel.isCreator) {
            val canRace = viewModel.race.value?.available == true
            val items = if (canRace) arrayOf("Change track", "Delete Group") else arrayOf("Delete Group")
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(groupName)
                .setItems(items) { _, which ->
                    when (items[which]) {
                        "Change track" -> showTrackPicker()
                        else -> confirmDeleteGroup(groupName)
                    }
                }
                .show()
        } else {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(groupName)
                .setItems(arrayOf("Leave Group")) { _, _ -> confirmLeaveGroup(groupName) }
                .show()
        }
    }

    private fun confirmDeleteGroup(groupName: String) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete \"$groupName\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteGroup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLeaveGroup(groupName: String) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Leave Group")
            .setMessage("Are you sure you want to leave \"$groupName\"?")
            .setPositiveButton("Leave") { _, _ -> viewModel.leaveGroup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

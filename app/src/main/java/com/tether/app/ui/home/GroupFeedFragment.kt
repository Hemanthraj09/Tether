package com.tether.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import com.tether.app.data.model.Group
import com.tether.app.data.repository.TodayLog
import com.tether.app.domain.Proof
import com.tether.app.ui.common.SheetOption
import com.tether.app.ui.common.TetherDialogs
import com.tether.app.ui.common.TetherSheet
import com.tether.app.utils.Formatters
import com.tether.app.ui.log.LogBottomSheetFragment
import com.tether.app.utils.TetherToast
import com.tether.app.utils.showOnce
import kotlinx.coroutines.launch

class GroupFeedFragment : Fragment() {

    private var _binding: FragmentGroupFeedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GroupFeedViewModel by viewModels()

    private lateinit var membersAdapter: LeaderboardAdapter
    private lateinit var todayAdapter: TodayLogAdapter

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

        todayAdapter = TodayLogAdapter(
            onReact = { item, kind -> viewModel.react(item, kind) },
            bindPhoto = { item, imageView, status -> bindPhoto(item, imageView, status) }
        )
        binding.todayRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.todayRecyclerView.adapter = todayAdapter

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
                            "${group.goalType} • $count member" + (if (count != 1) "s" else "") +
                            (if (Proof.normalizeMode(group.proof) == Proof.MODE_REQUIRED) " • 📷 photo required" else "")
                        binding.btnGroupInfo.visibility =
                            if (group.inviteCode.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.memberStats.collect { stats ->
                        if (stats != null) membersAdapter.submitList(stats.toLeaderboardItems())
                    }
                }
                launch {
                    viewModel.todayLogs.collect { logs ->
                        if (logs == null) return@collect
                        todayAdapter.submitList(logs)
                        binding.tvTodayEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
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

    /** Loads a proof photo into a row; rows are recycled, so check the tag first. */
    private fun bindPhoto(item: TodayLog, imageView: ImageView, status: TextView) {
        val logId = item.log.id
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = viewModel.loadPhoto(logId)
            if (imageView.tag != logId) return@launch
            if (bitmap == null) {
                status.text = "📷 Photo no longer available"
                imageView.setOnClickListener(null)
            } else {
                status.text = ""
                imageView.setImageBitmap(bitmap)
                imageView.setOnClickListener {
                    val who = if (item.isMine) "You" else item.log.userName
                    TetherDialogs.photo(requireContext(), bitmap,
                        "$who · ${Formatters.formatHours(item.log.value)}\n${item.log.note}")
                }
            }
        }
    }

    /** Any group: the creator decides whether logs need a photo. */
    private fun showProofPicker() {
        val current = Proof.normalizeMode(viewModel.group.value?.proof)
        fun option(mode: String, label: String, description: String, icon: Int) =
            SheetOption(label, description, icon = icon, selected = mode == current) { viewModel.setProofMode(mode) }
        TetherSheet.show(
            requireContext(),
            title = "Photo proof",
            subtitle = "Should logs in this group come with a photo?",
            options = listOf(
                option(Proof.MODE_OFF, "Off", "No photos, just what you did", R.drawable.ic_close),
                option(Proof.MODE_OPTIONAL, "Optional", "Members can add a photo to any log", R.drawable.ic_image),
                option(Proof.MODE_REQUIRED, "Required",
                    "Every manual log needs a fresh camera photo. Focus sessions are exempt.", R.drawable.ic_camera)
            )
        )
    }

    /** Coding groups: the creator picks what the leaderboard ranks. */
    private fun showMetricPicker() {
        val current = viewModel.group.value?.metric ?: Group.METRIC_HOURS
        TetherSheet.show(
            requireContext(),
            title = "Rank the leaderboard by",
            options = listOf(
                SheetOption("Hours logged", "Time spent, from logs and focus sessions", icon = R.drawable.ic_flame,
                    selected = current == Group.METRIC_HOURS) { viewModel.setMetric(Group.METRIC_HOURS) },
                SheetOption("Problems solved", "Verified LeetCode solves; hours break ties", icon = R.drawable.ic_code,
                    selected = current == Group.METRIC_SOLVES) { viewModel.setMetric(Group.METRIC_SOLVES) }
            )
        )
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
        val group = viewModel.group.value ?: return
        val code = group.inviteCode.ifEmpty { return }
        TetherSheet.show(
            requireContext(),
            title = "Invite friends",
            subtitle = "Share this code. Up to 6 people can be in ${group.name}.",
            headline = code,
            options = listOf(
                SheetOption("Copy code", icon = R.drawable.ic_copy) {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Tether invite code", code))
                    TetherToast.show(requireContext(), "Invite code copied")
                },
                SheetOption("Share invite", "Send it on WhatsApp, Telegram, anywhere", icon = R.drawable.ic_share) {
                    val text = "Join my group \"${group.name}\" on Tether. Invite code: $code"
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    startActivity(Intent.createChooser(send, "Share invite"))
                }
            )
        )
    }

    private fun showGroupOptionsMenu() {
        // Wait for the group to load so the creator never sees "Leave".
        val group = viewModel.group.value ?: return
        val options = mutableListOf<SheetOption>()
        if (group.inviteCode.isNotEmpty()) {
            options += SheetOption("Invite friends", "Code ${group.inviteCode}", icon = R.drawable.ic_group) { showInviteCode() }
        }
        if (viewModel.isCreator) {
            options += SheetOption("Photo proof", proofLabel(group.proof), icon = R.drawable.ic_camera) { showProofPicker() }
            if (viewModel.isCodingGroup) {
                options += SheetOption("Rank leaderboard by",
                    if (group.metric == Group.METRIC_SOLVES) "Problems solved" else "Hours logged",
                    icon = R.drawable.ic_trophy) { showMetricPicker() }
            }
            options += SheetOption("Delete group", "Removes it for everyone", icon = R.drawable.ic_delete,
                destructive = true) { confirmDeleteGroup(group.name) }
        } else {
            options += SheetOption("Leave group", icon = R.drawable.ic_logout, destructive = true) { confirmLeaveGroup(group.name) }
        }
        TetherSheet.show(requireContext(), title = group.name, subtitle = binding.tvFeedGroupGoal.text.toString(),
            options = options)
    }

    private fun proofLabel(mode: String) = when (Proof.normalizeMode(mode)) {
        Proof.MODE_OFF -> "Off"
        Proof.MODE_REQUIRED -> "Required"
        else -> "Optional"
    }

    private fun confirmDeleteGroup(groupName: String) {
        TetherDialogs.confirm(
            requireContext(),
            title = "Delete \"$groupName\"?",
            message = "The group is removed for every member. Everyone keeps their own hours and heatmap. This can't be undone.",
            confirmText = "Delete",
            destructive = true
        ) { viewModel.deleteGroup() }
    }

    private fun confirmLeaveGroup(groupName: String) {
        TetherDialogs.confirm(
            requireContext(),
            title = "Leave \"$groupName\"?",
            message = "You can rejoin later with the invite code, if there's still room.",
            confirmText = "Leave",
            destructive = true
        ) { viewModel.leaveGroup() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

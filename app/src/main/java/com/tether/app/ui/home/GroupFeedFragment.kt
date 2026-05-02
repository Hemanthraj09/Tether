package com.tether.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tether.app.R
import com.tether.app.databinding.FragmentGroupFeedBinding
import com.tether.app.timer.TimerModeDialogFragment
import com.tether.app.timer.TimerNoteDialogFragment
import com.tether.app.ui.leaderboard.LeaderboardAdapter
import com.tether.app.ui.leaderboard.LeaderboardItem
import com.tether.app.ui.log.LogBottomSheetFragment
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GroupFeedFragment : Fragment() {

    private var _binding: FragmentGroupFeedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GroupFeedViewModel by viewModels()

    private var groupId: String = ""
    private var groupName: String = ""
    private var groupGoal: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupFeedBinding.inflate(inflater, container, false)
        groupId = arguments?.getString("groupId") ?: ""
        groupName = arguments?.getString("groupName") ?: ""
        groupGoal = arguments?.getString("groupGoal") ?: ""
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvFeedGroupName.text = groupName
        binding.tvFeedGroupGoal.text = groupGoal

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val groupDoc = com.google.firebase
                    .firestore.FirebaseFirestore.getInstance()
                    .collection("groups")
                    .document(groupId)
                    .get()
                    .await()

                val memberCount = (groupDoc.get("members")
                        as? List<*>)?.size ?: 1
                binding.tvFeedGroupGoal.text =
                    "$groupGoal • $memberCount member" +
                            if (memberCount != 1) "s" else ""
            } catch (e: Exception) {
                binding.tvFeedGroupGoal.text = groupGoal
            }
        }

        setupRecyclerViews()
        observeFeed()
        observeMemberStats()
        observeLogState()
        observeGroupAction()

        viewModel.startListeningToFeed(groupId)
        viewModel.checkIfCreator(groupId)

        binding.btnFeedBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.fabLogFeed.setOnClickListener {
            showLogBottomSheet()
        }

        binding.btnFeedMore.setOnClickListener {
            showGroupOptionsMenu()
        }

        binding.btnStartSession.setOnClickListener {
            // Check if timer is already running
            if (isServiceRunning(com.tether.app.timer.TetherTimerService::class.java)) {
                com.tether.app.timer.TimerControlFragment.newInstance().show(childFragmentManager, "TimerControl")
            } else {
                val dialog = TimerModeDialogFragment.newInstance(groupId)
                dialog.show(childFragmentManager, "TimerModeDialog")
            }
        }

        registerTimerReceiver()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private val timerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == com.tether.app.timer.TetherTimerService.ACTION_TIMER_FINISHED) {
                val focusSeconds = intent.getLongExtra(com.tether.app.timer.TetherTimerService.EXTRA_FOCUS_SECONDS, 0L)
                val finishedGroupId = intent.getStringExtra(com.tether.app.timer.TetherTimerService.EXTRA_GROUP_ID) ?: ""
                
                if (finishedGroupId == groupId) {
                    val dialog = TimerNoteDialogFragment.newInstance(focusSeconds, finishedGroupId)
                    dialog.show(childFragmentManager, "TimerNoteDialog")
                }
            }
        }
    }

    private fun registerTimerReceiver() {
        val filter = android.content.IntentFilter(com.tether.app.timer.TetherTimerService.ACTION_TIMER_FINISHED)
        androidx.core.content.ContextCompat.registerReceiver(
            requireContext(),
            timerReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun setupRecyclerViews() {
        binding.membersRecyclerView.layoutManager =
            LinearLayoutManager(requireContext())
        
        binding.feedRecyclerView.layoutManager =
            LinearLayoutManager(requireContext())
        binding.feedRecyclerView.adapter =
            FeedAdapter(emptyList())
    }

    private fun observeMemberStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.memberStats.collect { stats ->
                val items = stats.mapIndexed { index, entry ->
                    LeaderboardItem(
                        id = index + 1,
                        name = entry.name,
                        initials = entry.initials,
                        hours = entry.hours,
                        streak = entry.streak,
                        avatarColorHex = entry.avatarColorHex,
                        isCurrentUser = entry.isCurrentUser
                    )
                }
                
                val adapter = binding.membersRecyclerView.adapter as? LeaderboardAdapter
                if (adapter == null) {
                    binding.membersRecyclerView.adapter = LeaderboardAdapter(items)
                } else {
                    adapter.updateItems(items)
                }
            }
        }
    }

    private fun observeFeed() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.feedLogs.collect { logs ->
                if (logs.isEmpty()) {
                    binding.feedRecyclerView.visibility = View.GONE
                    binding.tvEmptyFeed.visibility = View.VISIBLE
                } else {
                    binding.feedRecyclerView.visibility = View.VISIBLE
                    binding.tvEmptyFeed.visibility = View.GONE
                    val feedItems = logs.map { log ->
                        FeedItem(
                            id = log.id.hashCode(),
                            name = log.userName,
                            initials = log.userInitials,
                            hours = log.value,
                            note = log.note,
                            timeAgo = "Today",
                            avatarColorHex = log.avatarColorHex
                        )
                    }
                    binding.feedRecyclerView.adapter = FeedAdapter(feedItems)
                }
            }
        }
    }

    private fun observeLogState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.logState.collect { state ->
                when (state) {
                    is LogUiState.Success -> {
                        TetherToast.show(
                            requireContext(),
                            "Logged successfully! 🔥")
                    }
                    is LogUiState.Error -> {
                        TetherToast.show(
                            requireContext(),
                            state.message,
                            isError = true)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeGroupAction() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.groupActionState.collect { state ->
                when (state) {
                    is GroupActionState.Success -> {
                        TetherToast.show(
                            requireContext(),
                            "Done!")
                        findNavController().popBackStack()
                    }
                    is GroupActionState.Error -> {
                        TetherToast.show(
                            requireContext(),
                            state.message,
                            isError = true)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showLogBottomSheet() {
        val bottomSheet = LogBottomSheetFragment.newInstance(groupId)
        bottomSheet.show(parentFragmentManager, "LogBottomSheet")
    }

    private fun showGroupOptionsMenu() {
        val isCreator = viewModel.isCreator.value

        if (isCreator) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(groupName)
                .setItems(arrayOf("Delete Group")) { _, which ->
                    when (which) {
                        0 -> confirmDeleteGroup()
                    }
                }
                .show()
        } else {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(groupName)
                .setItems(arrayOf("Leave Group")) { _, which ->
                    when (which) {
                        0 -> confirmLeaveGroup()
                    }
                }
                .show()
        }
    }

    private fun confirmDeleteGroup() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete \"$groupName\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteGroup(groupId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLeaveGroup() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Leave Group")
            .setMessage("Are you sure you want to leave \"$groupName\"?")
            .setPositiveButton("Leave") { _, _ ->
                viewModel.leaveGroup(groupId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(timerReceiver)
        } catch (e: Exception) {}
        _binding = null
    }
}

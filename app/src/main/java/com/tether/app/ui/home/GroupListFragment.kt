package com.tether.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.tether.app.R
import com.tether.app.data.model.Group
import com.tether.app.databinding.FragmentGroupListBinding
import com.tether.app.utils.NotificationStore
import com.tether.app.utils.TetherToast
import com.tether.app.utils.navigateSafe
import kotlinx.coroutines.launch

class GroupListFragment : Fragment() {

    private var _binding: FragmentGroupListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GroupListViewModel by viewModels()

    private lateinit var adapter: GroupCardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        adapter = GroupCardAdapter(
            onGroupClick = { group -> navigateToGroupFeed(group) },
            onGroupLongPress = { group -> showGroupOptionsFromList(group) }
        )
        binding.groupListRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.groupListRecyclerView.adapter = adapter

        binding.btnJoinCreateFromList.setOnClickListener {
            findNavController().navigateSafe(R.id.action_groupList_to_group)
        }

        binding.btnJoinCreateGroup.setOnClickListener {
            findNavController().navigateSafe(R.id.action_groupList_to_group)
        }

        binding.btnGroupsNotification.setOnClickListener {
            NotificationStore.markRead(requireContext())
            showNotificationsBottomSheet()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.groups.collect { groups -> renderGroups(groups) } }
                launch {
                    NotificationStore.unreadFlow(requireContext()).collect { hasUnread ->
                        binding.notifDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.messages.collect { (text, isError) ->
                        TetherToast.show(requireContext(), text, isError)
                    }
                }
            }
        }
    }

    private fun renderGroups(groups: List<Group>?) {
        if (groups == null) {
            // First load (usually only a few ms, served from cache).
            binding.groupListRecyclerView.visibility = View.GONE
            binding.layoutNoGroups.visibility = View.GONE
            return
        }
        if (groups.isEmpty()) {
            binding.groupListRecyclerView.visibility = View.GONE
            binding.layoutNoGroups.visibility = View.VISIBLE
            binding.tvGroupCount.text = "0 groups"
        } else {
            binding.groupListRecyclerView.visibility = View.VISIBLE
            binding.layoutNoGroups.visibility = View.GONE
            binding.tvGroupCount.text = "${groups.size} group" +
                    if (groups.size != 1) "s" else ""
        }
        adapter.submitList(groups)
    }

    private fun navigateToGroupFeed(group: Group) {
        val bundle = bundleOf(
            "groupId" to group.id,
            "groupName" to group.name,
            "groupGoal" to group.goalType
        )
        findNavController().navigateSafe(R.id.action_groupList_to_feed, bundle)
    }

    private fun showGroupOptionsFromList(group: Group) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val isCreator = group.createdBy == currentUid

        if (isCreator) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(group.name)
                .setItems(arrayOf("Delete Group")) { _, _ ->
                    confirmDeleteFromList(group)
                }
                .show()
        } else {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(group.name)
                .setItems(arrayOf("Leave Group")) { _, _ ->
                    confirmLeaveFromList(group)
                }
                .show()
        }
    }

    private fun confirmDeleteFromList(group: Group) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete \"${group.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteGroup(group) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLeaveFromList(group: Group) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Leave Group")
            .setMessage("Are you sure you want to leave \"${group.name}\"?")
            .setPositiveButton("Leave") { _, _ -> viewModel.leaveGroup(group) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNotificationsBottomSheet() {
        val notifications = NotificationStore.getNotifications(requireContext())
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_notifications, null)
        bottomSheet.setContentView(view)

        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications)
        val layoutEmpty = view.findViewById<android.view.View>(R.id.layoutNotifEmpty)
        val tvDate = view.findViewById<android.widget.TextView>(R.id.tvNotifDate)

        tvDate.text = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        if (notifications.isEmpty()) {
            recycler.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            recycler.adapter = NotificationAdapter(notifications)
        }

        bottomSheet.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

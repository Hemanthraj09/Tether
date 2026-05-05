package com.tether.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tether.app.R
import com.tether.app.data.model.Group
import com.tether.app.databinding.FragmentGroupListBinding
import com.tether.app.ui.group.GroupViewModel
import com.tether.app.ui.group.UserGroupsState
import com.tether.app.utils.NotificationStore
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.launch

class GroupListFragment : Fragment() {

    private var _binding: FragmentGroupListBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by viewModels()

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

        observeGroups()
        groupViewModel.loadUserGroups()
        setupNotificationBell()
        startGroupActivityListeners()

        binding.btnJoinCreateFromList.setOnClickListener {
            findNavController().navigate(R.id.action_groupList_to_group)
        }

        binding.btnJoinCreateGroup.setOnClickListener {
            findNavController().navigate(R.id.action_groupList_to_group)
        }
    }

    private fun observeGroups() {
        viewLifecycleOwner.lifecycleScope.launch {
            groupViewModel.userGroupsState.collect { state ->
                when (state) {
                    is UserGroupsState.Loading -> {
                        binding.groupListRecyclerView.visibility = View.GONE
                        binding.layoutNoGroups.visibility = View.GONE
                    }
                    is UserGroupsState.Success -> {
                        val groups = state.groups
                        if (groups.isEmpty()) {
                            binding.groupListRecyclerView.visibility = View.GONE
                            binding.layoutNoGroups.visibility = View.VISIBLE
                            binding.tvGroupCount.text = "0 groups"
                        } else {
                            binding.groupListRecyclerView.visibility = View.VISIBLE
                            binding.layoutNoGroups.visibility = View.GONE
                            binding.tvGroupCount.text = "${groups.size} group" +
                                    if (groups.size != 1) "s" else ""
                            setupRecyclerView(groups)
                        }
                    }
                    is UserGroupsState.Error -> {
                        binding.groupListRecyclerView.visibility = View.GONE
                        binding.layoutNoGroups.visibility = View.VISIBLE
                        binding.tvGroupCount.text = "0 groups"
                    }
                }
            }
        }
    }

    private fun setupRecyclerView(groups: List<Group>) {
        binding.groupListRecyclerView.layoutManager =
            LinearLayoutManager(requireContext())
        binding.groupListRecyclerView.adapter =
            GroupCardAdapter(
                groups,
                onGroupClick = { group ->
                    navigateToGroupFeed(group)
                },
                onGroupLongPress = { group ->
                    showGroupOptionsFromList(group)
                }
            )
    }

    private fun navigateToGroupFeed(group: Group) {
        val bundle = bundleOf(
            "groupId" to group.id,
            "groupName" to group.name,
            "groupGoal" to group.goalType
        )
        findNavController().navigate(
            R.id.action_groupList_to_feed,
            bundle
        )
    }

    private fun showGroupOptionsFromList(group: Group) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
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
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val repo = com.tether.app.data.repository.GroupManagementRepository()
                    val result = repo.deleteGroup(group.id)
                    if (result.isSuccess) {
                        TetherToast.show(requireContext(), "${group.name} deleted.")
                        groupViewModel.loadUserGroups()
                    } else {
                        TetherToast.show(
                            requireContext(),
                            "Failed to delete group",
                            isError = true
                        )
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLeaveFromList(group: Group) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Leave Group")
            .setMessage("Are you sure you want to leave \"${group.name}\"?")
            .setPositiveButton("Leave") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val repo = com.tether.app.data.repository.GroupManagementRepository()
                    val result = repo.leaveGroup(group.id)
                    if (result.isSuccess) {
                        TetherToast.show(requireContext(), "Left ${group.name}.")
                        groupViewModel.loadUserGroups()
                    } else {
                        TetherToast.show(
                            requireContext(),
                            "Failed to leave group",
                            isError = true
                        )
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupNotificationBell() {
        updateBellDot()
        binding.btnGroupsNotification.setOnClickListener {
            NotificationStore.markRead(requireContext())
            updateBellDot()
            showNotificationsBottomSheet()
        }
    }

    private fun updateBellDot() {
        val hasUnread = NotificationStore.hasUnread(requireContext())
        // Show/hide orange dot overlay on bell
        binding.notifDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
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

    private fun startGroupActivityListeners() {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        viewLifecycleOwner.lifecycleScope.launch {
            groupViewModel.userGroupsState.collect { state ->
                if (state is com.tether.app.ui.group.UserGroupsState.Success) {
                    state.groups.forEach { group ->
                        firestore.collection("logs")
                            .whereEqualTo("groupId", group.id)
                            .whereEqualTo("date", today)
                            .addSnapshotListener { snapshot, _ ->
                                snapshot?.documentChanges?.forEach { change ->
                                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                        val logUid = change.document.getString("userId") ?: ""
                                        if (logUid != currentUid) {
                                            val userName = change.document.getString("userName") ?: "Someone"
                                            val hours = change.document.getDouble("value") ?: 0.0
                                            val totalMins = (hours * 60).toInt()
                                            val h = totalMins / 60
                                            val m = totalMins % 60
                                            val hoursStr = when {
                                                h == 0 -> "${m}m"
                                                m == 0 -> "${h}h"
                                                else -> "${h}h ${m}m"
                                            }
                                            val message = "$userName logged $hoursStr in ${group.name}"
                                            NotificationStore.addNotification(requireContext(), message)
                                            activity?.runOnUiThread { updateBellDot() }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

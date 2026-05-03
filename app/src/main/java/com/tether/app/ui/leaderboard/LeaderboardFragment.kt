package com.tether.app.ui.leaderboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tether.app.R
import com.tether.app.databinding.FragmentLeaderboardBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LeaderboardViewModel by viewModels()
    private var currentGroupId: String = ""

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

        binding.leaderboardRecyclerView.layoutManager =
            LinearLayoutManager(requireContext())
        binding.leaderboardRecyclerView.adapter =
            LeaderboardAdapter(currentGroupId, emptyList()) { _, _ -> }

        binding.btnWeekly.background =
            ContextCompat.getDrawable(requireContext(),
                R.drawable.bg_toggle_active)
        binding.btnWeekly.setTextColor(
            ContextCompat.getColor(requireContext(),
                R.color.colorTextPrimary))

        binding.btnWeekly.setOnClickListener {
            binding.btnWeekly.background =
                ContextCompat.getDrawable(requireContext(),
                    R.drawable.bg_toggle_active)
            binding.btnWeekly.setTextColor(
                ContextCompat.getColor(requireContext(),
                    R.color.colorTextPrimary))
            binding.btnToday.background = null
            binding.btnToday.setTextColor(
                ContextCompat.getColor(requireContext(),
                    R.color.colorTextSecondary))
        }

        binding.btnToday.setOnClickListener {
            binding.btnToday.background =
                ContextCompat.getDrawable(requireContext(),
                    R.drawable.bg_toggle_active)
            binding.btnToday.setTextColor(
                ContextCompat.getColor(requireContext(),
                    R.color.colorTextPrimary))
            binding.btnWeekly.background = null
            binding.btnWeekly.setTextColor(
                ContextCompat.getColor(requireContext(),
                    R.color.colorTextSecondary))
        }

        binding.bottomNav.selectedItemId =
            R.id.nav_leaderboard

        observeLeaderboard()
        loadLeaderboardForUserGroup()

        binding.bottomNav.setOnItemSelectedListener {
            item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    findNavController().navigate(
                        R.id.groupListFragment,
                        null,
                        androidx.navigation.NavOptions
                            .Builder()
                            .setPopUpTo(R.id.groupListFragment,
                                true)
                            .setLaunchSingleTop(true)
                            .build()
                    )
                    true
                }
                R.id.nav_leaderboard -> true
                R.id.nav_profile -> {
                    findNavController().navigate(
                        R.id.action_leaderboard_to_profile,
                        null,
                        androidx.navigation.NavOptions
                            .Builder()
                            .setPopUpTo(R.id.groupListFragment,
                                false)
                            .setLaunchSingleTop(true)
                            .build()
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun loadLeaderboardForUserGroup() {
        val uid = com.google.firebase.auth
            .FirebaseAuth.getInstance()
            .currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userDoc = com.google.firebase
                    .firestore.FirebaseFirestore
                    .getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .await()

                @Suppress("UNCHECKED_CAST")
                val groupIds = userDoc
                    .get("groupIds") as? List<String>
                    ?: emptyList()

                if (groupIds.isNotEmpty()) {
                    currentGroupId = groupIds.first()
                    viewModel.loadLeaderboard(
                        currentGroupId)
                }
            } catch (e: Exception) {
                // keep showing existing data
            }
        }
    }

    private fun observeLeaderboard() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is LeaderboardUiState.Loading -> {
                        // keep showing existing list
                    }
                    is LeaderboardUiState.Success -> {
                        val items = state.entries
                            .mapIndexed { index, entry ->
                                LeaderboardItem(
                                    id = index + 1,
                                    name = entry.name,
                                    initials = entry.initials,
                                    hours = entry.hours,
                                    streak = entry.streak,
                                    avatarColorHex = entry.avatarColorHex,
                                    isCurrentUser = entry.isCurrentUser,
                                    uid = entry.uid,
                                    hasNudgedToday = false
                                )
                            }
                        val adapter = binding.leaderboardRecyclerView.adapter as? LeaderboardAdapter
                        if (adapter == null) {
                            binding.leaderboardRecyclerView.adapter = LeaderboardAdapter(currentGroupId, items) { _, _ -> }
                        } else {
                            adapter.updateItems(items)
                        }
                    }
                    is LeaderboardUiState.Empty -> {
                        val adapter = binding.leaderboardRecyclerView.adapter as? LeaderboardAdapter
                        if (adapter == null) {
                            binding.leaderboardRecyclerView.adapter = LeaderboardAdapter(currentGroupId, emptyList()) { _, _ -> }
                        } else {
                            adapter.updateItems(emptyList())
                        }
                    }
                    is LeaderboardUiState.Error -> {
                        // keep showing existing dummy data
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
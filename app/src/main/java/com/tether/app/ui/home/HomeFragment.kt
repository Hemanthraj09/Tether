package com.tether.app.ui.home

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tether.app.R
import com.tether.app.databinding.FragmentHomeBinding
import com.tether.app.ui.group.GroupViewModel
import com.tether.app.ui.group.UserGroupsState
import com.tether.app.utils.applyStatusBarPadding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by viewModels()
    private var currentGroupIndex = 0
    private var userGroups = listOf<com.tether.app.data.model.Group>()
    
    private val feedItems = listOf(
        FeedItem(1, "Sarah J.", "SJ", 3.5,
            "Finished the React course module! 🚀",
            "2h ago", "#3B82F6"),
        FeedItem(2, "Mike T.", "MT", 1.5,
            "Quick gym session before work.",
            "4h ago", "#22C55E"),
        FeedItem(3, "Alex C.", "AC", 4.0,
            "Deep work on the new side project.",
            "5h ago", "#A855F7"),
        FeedItem(4, "Emma W.", "EW", 2.0,
            "Reading group chapters 4-5.",
            "8h ago", "#EC4899")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.applyStatusBarPadding()
        setupHeader()
        
        binding.feedRecyclerView.layoutManager =
            LinearLayoutManager(requireContext())
        binding.feedRecyclerView.adapter = FeedAdapter(feedItems)
        binding.feedRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.bottom = 10
            }
        })

        binding.tvGroupName.text = ""
        binding.tvDot.visibility = View.GONE
        binding.tvDate.visibility = View.GONE

        binding.tvGroupName.setOnClickListener {
            if (userGroups.size <= 1) return@setOnClickListener
            showGroupSwitcherDialog()
        }

        observeUserGroups()
        groupViewModel.loadUserGroups()

        binding.btnJoinCreateGroup.setOnClickListener {
            findNavController().navigate(
                R.id.groupFragment,
                null,
                NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, false)
                    .setLaunchSingleTop(true)
                    .build()
            )
        }

        binding.btnGroups.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_group)
        }
    }

    private fun observeUserGroups() {
        viewLifecycleOwner.lifecycleScope.launch {
            groupViewModel.userGroupsState.collect {
                state ->
                when (state) {
                    is UserGroupsState.Loading -> {
                        binding.tvGroupName.text = ""
                        binding.tvDot.visibility =
                            View.GONE
                        binding.tvDate.visibility =
                            View.GONE
                    }
                    is UserGroupsState.Success -> {
                        userGroups = state.groups
                        val hasGroup =
                            state.groups.isNotEmpty()
                        showFeedOrEmptyState(hasGroup)

                        if (hasGroup) {
                            binding.tvDot.visibility =
                                View.VISIBLE
                            binding.tvDate.visibility =
                                View.VISIBLE
                            updateActiveGroup(0)
                        }
                    }
                    is UserGroupsState.Error -> {
                        showFeedOrEmptyState(false)
                        binding.tvGroupName.text = ""
                        binding.tvDot.visibility =
                            View.GONE
                        binding.tvDate.visibility =
                            View.GONE
                    }
                }
            }
        }
    }

    private fun updateActiveGroup(index: Int) {
        if (userGroups.isEmpty()) return
        currentGroupIndex = index
        val group = userGroups[index]
        binding.tvGroupName.text = group.name
    }

    private fun showGroupSwitcherDialog() {
        val groupNames = userGroups
            .map { it.name }
            .toTypedArray()

        android.app.AlertDialog.Builder(
            requireContext())
            .setTitle("Switch Group")
            .setItems(groupNames) { _, which ->
                updateActiveGroup(which)
            }
            .show()
    }

    private fun showFeedOrEmptyState(hasGroup: Boolean) {
        if (hasGroup) {
            binding.feedRecyclerView.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        } else {
            binding.feedRecyclerView.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        }
    }

    private fun setupHeader() {
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

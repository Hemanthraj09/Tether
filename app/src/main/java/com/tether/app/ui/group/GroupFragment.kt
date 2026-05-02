package com.tether.app.ui.group

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.tether.app.R
import com.tether.app.databinding.FragmentGroupBinding
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.launch

class GroupFragment : Fragment() {

    private var _binding: FragmentGroupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GroupViewModel by viewModels()
    private var selectedGoal = "Study"
    private var isSoloMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupBinding.inflate(
            inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        selectGoal("Study")
        observeGroupState()

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnGoalStudy.setOnClickListener {
            selectGoal("Study")
        }
        binding.btnGoalGym.setOnClickListener {
            selectGoal("Gym")
        }
        binding.btnGoalCoding.setOnClickListener {
            selectGoal("Coding")
        }
        binding.btnGoalOther.setOnClickListener {
            selectGoal("Other")
        }

        binding.btnJoinGroup.setOnClickListener {
            val code = binding.etInviteCode.text
                .toString().trim()
            if (code.isEmpty()) {
                binding.etInviteCode.error =
                    "Please enter an invite code"
                return@setOnClickListener
            }
            viewModel.joinGroup(code)
        }

        binding.btnCreateGroup.setOnClickListener {
            val name = binding.etGroupName.text
                .toString().trim()
            if (name.isEmpty()) {
                binding.etGroupName.error =
                    "Please enter a group name"
                return@setOnClickListener
            }
            viewModel.createGroup(
                name, selectedGoal, isSoloMode)
        }
    }

    private fun observeGroupState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.groupState.collect { state ->
                when (state) {
                    is GroupState.Loading -> {
                        binding.btnCreateGroup
                            .isEnabled = false
                        binding.btnJoinGroup
                            .isEnabled = false
                    }
                    is GroupState.Success -> {
                        binding.btnCreateGroup
                            .isEnabled = true
                        binding.btnJoinGroup
                            .isEnabled = true
                        TetherToast.show(
                            requireContext(),
                            "Welcome to " +
                            state.group.name + "! 🎉")
                        findNavController()
                            .popBackStack()
                    }
                    is GroupState.Error -> {
                        binding.btnCreateGroup
                            .isEnabled = true
                        binding.btnJoinGroup
                            .isEnabled = true
                        TetherToast.show(
                            requireContext(),
                            state.message,
                            isError = true)
                    }
                    is GroupState.Idle -> {
                        binding.btnCreateGroup
                            .isEnabled = true
                        binding.btnJoinGroup
                            .isEnabled = true
                    }
                }
            }
        }
    }

    private fun selectGoal(goal: String) {
        selectedGoal = goal
        setGoalButtonState(
            binding.btnGoalStudy,
            goal == "Study",
            R.drawable.ic_book)
        setGoalButtonState(
            binding.btnGoalGym,
            goal == "Gym",
            R.drawable.ic_fitness)
        setGoalButtonState(
            binding.btnGoalCoding,
            goal == "Coding",
            R.drawable.ic_code)
        setGoalButtonState(
            binding.btnGoalOther,
            goal == "Other",
            R.drawable.ic_sparkle)
    }

    private fun setGoalButtonState(
        container: ConstraintLayout,
        isSelected: Boolean,
        iconRes: Int
    ) {
        container.background = ContextCompat.getDrawable(
            requireContext(),
            if (isSelected) R.drawable.bg_goal_selected
            else R.drawable.bg_goal_unselected
        )
        val iconView =
            container.getChildAt(0) as? ImageView
        val textView =
            container.getChildAt(1) as? TextView
        val tint = if (isSelected)
            ContextCompat.getColor(requireContext(),
                R.color.colorAccent)
        else
            ContextCompat.getColor(requireContext(),
                R.color.colorTextSecondary)
        iconView?.imageTintList =
            ColorStateList.valueOf(tint)
        textView?.setTextColor(tint)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

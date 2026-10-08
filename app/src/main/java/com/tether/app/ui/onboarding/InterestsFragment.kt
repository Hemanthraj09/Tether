package com.tether.app.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tether.app.data.repository.ProfileRepository
import com.tether.app.databinding.FragmentInterestsBinding
import com.tether.app.databinding.ItemTargetRowBinding
import com.tether.app.domain.Interests
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.launch

/**
 * "What do you want to stay accountable for?" plus a weekly target per area.
 * Shown once after sign-in, editable from Profile ("Said vs. Did").
 */
class InterestsFragment : Fragment() {

    private var _binding: FragmentInterestsBinding? = null
    private val binding get() = _binding!!
    private val repository = ProfileRepository()

    /** area → weekly hours; kept for unticked areas so re-ticking restores the value. */
    private val targets = mutableMapOf<String, Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInterestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Interests.OPTIONS.forEach { addChip(binding.chipsInterests, it) }
        Interests.STAGES.forEach { addChip(binding.chipsStage, it) }
        binding.chipsInterests.setOnCheckedStateChangeListener { _, _ -> renderTargets() }

        // Pre-fill when editing from Profile.
        viewLifecycleOwner.lifecycleScope.launch {
            val plan = repository.loadPlan()
            if (_binding == null) return@launch
            targets.putAll(plan.targets)
            checkChips(binding.chipsInterests, plan.interests.orEmpty())
            checkChips(binding.chipsStage, listOfNotNull(plan.stage))
            renderTargets()
        }

        binding.btnInterestsContinue.setOnClickListener { save() }
        binding.tvInterestsSkip.setOnClickListener {
            // Remember that we asked, without choosing anything.
            viewLifecycleOwner.lifecycleScope.launch {
                repository.saveInterests(emptyList(), null)
                close()
            }
        }
    }

    /** One stepper row per ticked area, in the option order. */
    private fun renderTargets() {
        val selected = Interests.sanitize(selectedIds(binding.chipsInterests))
        binding.tvTargetsTitle.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        binding.targetsContainer.removeAllViews()
        selected.forEach { area ->
            val row = ItemTargetRowBinding.inflate(layoutInflater, binding.targetsContainer, true)
            row.tvTargetLabel.text = Interests.label(area)
            fun show() { row.tvTargetValue.text = "${targetFor(area)}h/wk" }
            row.btnTargetMinus.setOnClickListener {
                targets[area] = (targetFor(area) - 1).coerceAtLeast(1); show()
            }
            row.btnTargetPlus.setOnClickListener {
                targets[area] = (targetFor(area) + 1).coerceAtMost(Interests.MAX_TARGET_HOURS); show()
            }
            show()
        }
    }

    private fun targetFor(area: String) = targets[area] ?: Interests.DEFAULT_TARGET_HOURS

    private fun save() {
        binding.btnInterestsContinue.isEnabled = false
        val interests = Interests.sanitize(selectedIds(binding.chipsInterests))
        val stage = selectedIds(binding.chipsStage).firstOrNull()
        viewLifecycleOwner.lifecycleScope.launch {
            repository.saveInterests(interests, stage, targets)
                .onFailure { TetherToast.show(requireContext(), it.message ?: "Couldn't save", isError = true) }
            close()
        }
    }

    private fun close() {
        if (_binding != null) findNavController().popBackStack()
    }

    private fun addChip(group: ChipGroup, option: Interests.Option) {
        group.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
            text = option.label
            tag = option.id
            isCheckable = true
            setEnsureMinTouchTargetSize(true)
        })
    }

    private fun checkChips(group: ChipGroup, ids: List<String>) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            chip.isChecked = chip.tag in ids
        }
    }

    private fun selectedIds(group: ChipGroup): List<String> =
        (0 until group.childCount).map { group.getChildAt(it) as Chip }.filter { it.isChecked }.map { it.tag as String }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

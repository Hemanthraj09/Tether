package com.tether.app.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tether.app.R
import com.tether.app.databinding.LayoutLogBottomSheetBinding
import com.tether.app.ui.home.GroupFeedViewModel
import com.tether.app.utils.TetherToast
import java.util.Locale

class LogBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutLogBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var currentHours = 0
    private var currentMinutes = 0
    private var groupId: String = ""
    private val viewModel: GroupFeedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutLogBottomSheetBinding.inflate(inflater, container, false)
        groupId = arguments?.getString("groupId") ?: ""
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(
        requireContext(),
        R.style.BottomSheetDialogTheme
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        updateDisplay()

        binding.btnHourMinus.setOnClickListener {
            if (currentHours > 0) {
                currentHours--
                updateDisplay()
            }
        }

        binding.btnHourPlus.setOnClickListener {
            if (currentHours < 12) {
                currentHours++
                updateDisplay()
            }
        }

        binding.btnMinuteMinus.setOnClickListener {
            if (currentMinutes > 0) {
                currentMinutes -= 5
                updateDisplay()
            } else if (currentHours > 0) {
                currentHours--
                currentMinutes = 55
                updateDisplay()
            }
        }

        binding.btnMinutePlus.setOnClickListener {
            if (currentMinutes < 55) {
                currentMinutes += 5
                updateDisplay()
            } else {
                currentMinutes = 0
                if (currentHours < 12) currentHours++
                updateDisplay()
            }
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnLogIt.setOnClickListener {
            if (currentHours == 0 && currentMinutes == 0) {
                TetherToast.show(requireContext(), "Please log at least 5 minutes", isError = true)
                return@setOnClickListener
            }
            binding.btnLogIt.isEnabled = false
            val totalHours = currentHours + (currentMinutes / 60.0)
            val note = binding.etNote.text.toString().trim()
            val timeStr = when {
                currentHours == 0 -> "${currentMinutes}m"
                currentMinutes == 0 -> "${currentHours}h"
                else -> "${currentHours}h ${currentMinutes}m"
            }
            viewModel.writeLog(groupId, totalHours, note)
            TetherToast.show(requireContext(), "Logged $timeStr! Keep it up 🔥")
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val screenHeight = resources.displayMetrics.heightPixels
            behavior.peekHeight = (screenHeight * 0.85).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            it.layoutParams.height = (screenHeight * 0.85).toInt()
        }
    }

    private fun updateDisplay() {
        binding.tvHoursValue.text = currentHours.toString()
        binding.tvMinutesValue.text = String.format(Locale.getDefault(), "%02d", currentMinutes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(groupId: String): LogBottomSheetFragment {
            val fragment = LogBottomSheetFragment()
            fragment.arguments = Bundle().apply {
                putString("groupId", groupId)
            }
            return fragment
        }
    }
}

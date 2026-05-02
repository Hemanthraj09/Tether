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

class LogBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutLogBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var currentHours = 2.5
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

        updateHoursDisplay()

        binding.btnMinus.setOnClickListener {
            if (currentHours > 0.0) {
                currentHours = (currentHours - 0.5)
                updateHoursDisplay()
            }
        }

        binding.btnPlus.setOnClickListener {
            currentHours = (currentHours + 0.5)
            updateHoursDisplay()
        }

        binding.switchPhoto.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutPhotoUpload.visibility =
                if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnLogIt.setOnClickListener {
            if (currentHours == 0.0) {
                TetherToast.show(
                    requireContext(),
                    "Please log at least 0.5 hours",
                    isError = true
                )
                return@setOnClickListener
            }
            val note = binding.etNote.text.toString().trim()
            viewModel.writeLog(groupId, currentHours, note)
            TetherToast.show(
                requireContext(),
                "Logged ${currentHours}h! Keep it up 🔥"
            )
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

    private fun updateHoursDisplay() {
        binding.tvHoursValue.text = currentHours.toString()
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

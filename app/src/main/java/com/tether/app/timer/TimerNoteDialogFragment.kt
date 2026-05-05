package com.tether.app.timer

import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.tether.app.databinding.DialogTimerNoteBinding
import com.tether.app.ui.home.GroupFeedViewModel
import com.tether.app.utils.TetherToast

class TimerNoteDialogFragment : DialogFragment() {

    private var _binding: DialogTimerNoteBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: GroupFeedViewModel by activityViewModels()
    
    private var focusSeconds = 0L
    private var groupId = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogTimerNoteBinding.inflate(inflater, container, false)
        focusSeconds = arguments?.getLong("focusSeconds") ?: 0L
        groupId = arguments?.getString("groupId") ?: ""
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hours = focusSeconds / 3600.0
        val totalMinutes = (hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        val timeStr = when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
        binding.tvFocusedTime.text = "You focused for $timeStr"

        binding.btnLogIt.setOnClickListener {
            binding.btnLogIt.isEnabled = false
            val note = binding.etNote.text.toString().trim()
            viewModel.writeLog(groupId, hours, note)
            TetherToast.show(requireContext(), "Logged $timeStr! Keep it up 🔥")
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(focusSeconds: Long, groupId: String): TimerNoteDialogFragment {
            return TimerNoteDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong("focusSeconds", focusSeconds)
                    putString("groupId", groupId)
                }
            }
        }
    }
}

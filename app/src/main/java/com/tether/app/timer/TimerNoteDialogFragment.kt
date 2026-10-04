package com.tether.app.timer

import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.tether.app.databinding.DialogTimerNoteBinding
import com.tether.app.ui.home.GroupFeedViewModel
import com.tether.app.utils.Formatters
import com.tether.app.utils.TetherToast

class TimerNoteDialogFragment : DialogFragment() {

    private var _binding: DialogTimerNoteBinding? = null
    private val binding get() = _binding!!

    // Shared with the hosting GroupFeedFragment, so errors surface there.
    private val viewModel: GroupFeedViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private var focusSeconds = 0L
    private var groupId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // There is no "skip": tapping outside must not silently drop the session.
        isCancelable = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogTimerNoteBinding.inflate(inflater, container, false)
        focusSeconds = arguments?.getLong("focusSeconds") ?: 0L
        groupId = arguments?.getString("groupId") ?: ""
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Under a minute there's nothing meaningful to log (it showed as "0m").
        // Over 24h is a timer left running by mistake (and rejected by the server rules).
        if (focusSeconds < 60 || focusSeconds > MAX_LOG_SECONDS) {
            binding.tvFocusedTime.text =
                if (focusSeconds < 60) "Session too short to log"
                else "Session over 24h — looks like the timer was left running"
            binding.etNote.visibility = View.GONE
            binding.btnLogIt.text = "Close"
            binding.btnLogIt.setOnClickListener { dismiss() }
            return
        }

        val hours = focusSeconds / 3600.0
        val timeStr = Formatters.formatHours(hours)
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
        const val TAG = "TimerNoteDialog"
        private const val MAX_LOG_SECONDS = 24 * 3600L

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

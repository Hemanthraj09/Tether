package com.tether.app.timer

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.tether.app.databinding.DialogTimerModeBinding

class TimerModeDialogFragment : DialogFragment() {

    private var _binding: DialogTimerModeBinding? = null
    private val binding get() = _binding!!
    private var groupId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogTimerModeBinding.inflate(inflater, container, false)
        groupId = arguments?.getString("groupId") ?: ""
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNormal.setOnClickListener {
            startTimer(TetherTimerService.TimerMode.STOPWATCH)
            dismiss()
        }

        binding.btnPomodoro.setOnClickListener {
            binding.layoutModes.visibility = View.GONE
            binding.layoutPomoConfigs.visibility = View.VISIBLE
        }

        binding.btnPomo25.setOnClickListener {
            startTimer(TetherTimerService.TimerMode.POMODORO, 25, 5)
            dismiss()
        }

        binding.btnPomo50.setOnClickListener {
            startTimer(TetherTimerService.TimerMode.POMODORO, 50, 10)
            dismiss()
        }
    }

    private fun startTimer(mode: TetherTimerService.TimerMode, focusMins: Int = 0, breakMins: Int = 0) {
        val intent = Intent(requireContext(), TetherTimerService::class.java).apply {
            putExtra(TetherTimerService.EXTRA_GROUP_ID, groupId)
            putExtra(TetherTimerService.EXTRA_MODE, mode.name)
            if (mode == TetherTimerService.TimerMode.POMODORO) {
                putExtra(TetherTimerService.EXTRA_POMO_FOCUS, focusMins)
                putExtra(TetherTimerService.EXTRA_POMO_BREAK, breakMins)
            }
        }
        requireContext().startForegroundService(intent)

        // Show control fragment
        TimerControlFragment.newInstance().show(parentFragmentManager, "TimerControl")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(groupId: String): TimerModeDialogFragment {
            return TimerModeDialogFragment().apply {
                arguments = Bundle().apply { putString("groupId", groupId) }
            }
        }
    }
}

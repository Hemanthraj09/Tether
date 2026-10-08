package com.tether.app.timer

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.tether.app.databinding.DialogTimerModeBinding
import com.tether.app.utils.showOnce

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
            startTimer(TimerMode.STOPWATCH)
        }

        binding.btnPomodoro.setOnClickListener { showPomodoroOptions(true) }
        binding.btnPomoBack.setOnClickListener { showPomodoroOptions(false) }

        binding.btnPomo25.setOnClickListener {
            startTimer(TimerMode.POMODORO, 25, 5)
        }

        binding.btnPomo50.setOnClickListener {
            startTimer(TimerMode.POMODORO, 50, 10)
        }
    }

    private fun showPomodoroOptions(show: Boolean) {
        binding.layoutModes.visibility = if (show) View.GONE else View.VISIBLE
        binding.layoutPomoConfigs.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvModeTitle.text = if (show) "Pick your rhythm" else "How do you want to work?"
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

    private fun startTimer(mode: TimerMode, focusMins: Int = 0, breakMins: Int = 0) {
        val intent = Intent(requireContext(), TetherTimerService::class.java).apply {
            putExtra(TetherTimerService.EXTRA_GROUP_ID, groupId)
            putExtra(TetherTimerService.EXTRA_MODE, mode.name)
            if (mode == TimerMode.POMODORO) {
                putExtra(TetherTimerService.EXTRA_POMO_FOCUS, focusMins)
                putExtra(TetherTimerService.EXTRA_POMO_BREAK, breakMins)
            }
        }
        requireContext().startForegroundService(intent)

        // Show the control sheet in the same fragment manager (the group feed's),
        // so its "stopped" result reaches the group feed.
        TimerControlFragment.newInstance().showOnce(parentFragmentManager, TimerControlFragment.TAG)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TimerModeDialog"

        fun newInstance(groupId: String): TimerModeDialogFragment {
            return TimerModeDialogFragment().apply {
                arguments = Bundle().apply { putString("groupId", groupId) }
            }
        }
    }
}

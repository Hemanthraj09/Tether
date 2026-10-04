package com.tether.app.timer

import android.content.*
import android.os.*
import android.view.*
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tether.app.databinding.FragmentTimerControlBinding
import kotlinx.coroutines.launch

class TimerControlFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTimerControlBinding? = null
    private val binding get() = _binding!!

    private var timerService: TetherTimerService? = null
    private var isBound = false
    private var sawActiveSession = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            // Frequent, cheap refreshes keep the seconds ticking smoothly;
            // views are only touched when the text actually changes.
            handler.postDelayed(this, 250)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TetherTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
            if (_binding != null) binding.btnStop.isEnabled = true
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            timerService = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Enabled once the service is connected, so a stop never loses the time.
        binding.btnStop.isEnabled = timerService != null

        binding.btnStop.setOnClickListener {
            val service = timerService ?: return@setOnClickListener
            if (!TetherTimerService.isRunning) {
                dismiss()
                return@setOnClickListener
            }
            binding.btnStop.isEnabled = false
            val groupId = service.groupId
            val focusSeconds = service.stopTimer()
            parentFragmentManager.setFragmentResult(
                RESULT_TIMER_STOPPED,
                bundleOf(RESULT_FOCUS_SECONDS to focusSeconds, RESULT_GROUP_ID to groupId)
            )
            dismiss()
        }

        binding.btnBreak5.setOnClickListener {
            timerService?.startBreak(5)
            updateUI()
        }

        binding.btnBreak10.setOnClickListener {
            timerService?.startBreak(10)
            updateUI()
        }

        // Close this sheet if the session ends from somewhere else.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TetherTimerService.activeGroupId.collect { active ->
                    if (active != null) sawActiveSession = true
                    else if (sawActiveSession) dismissAllowingStateLoss()
                }
            }
        }
    }

    private fun updateUI() {
        val b = _binding ?: return
        val service = timerService ?: return

        val time = service.formatTime(service.currentSeconds)
        if (b.tvTimerDisplay.text.toString() != time) b.tvTimerDisplay.text = time

        val phaseLabel = when (service.currentPhase) {
            TimerPhase.FOCUSING -> "Focusing"
            TimerPhase.BREAK -> "Break"
        }
        if (b.tvPhaseLabel.text.toString() != phaseLabel) b.tvPhaseLabel.text = phaseLabel

        val showBreaks = service.mode == TimerMode.STOPWATCH &&
                service.currentPhase == TimerPhase.FOCUSING
        val visibility = if (showBreaks) View.VISIBLE else View.GONE
        if (b.layoutBreakOptions.visibility != visibility) b.layoutBreakOptions.visibility = visibility
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), TetherTimerService::class.java).also { intent ->
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateRunnable)
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }
        timerService = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TimerControl"
        const val RESULT_TIMER_STOPPED = "timer_stopped"
        const val RESULT_FOCUS_SECONDS = "focusSeconds"
        const val RESULT_GROUP_ID = "groupId"

        fun newInstance(): TimerControlFragment = TimerControlFragment()
    }
}

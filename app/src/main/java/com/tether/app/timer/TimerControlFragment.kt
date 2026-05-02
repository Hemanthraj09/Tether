package com.tether.app.timer

import android.content.*
import android.os.*
import android.view.*
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tether.app.databinding.FragmentTimerControlBinding

class TimerControlFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTimerControlBinding? = null
    private val binding get() = _binding!!
    
    private var timerService: TetherTimerService? = null
    private var isBound = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 1000)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TetherTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
            updateUI()
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

        binding.btnStop.setOnClickListener {
            timerService?.stopTimer()
            dismiss()
        }

        binding.btnBreak5.setOnClickListener {
            timerService?.startBreak(5)
        }

        binding.btnBreak10.setOnClickListener {
            timerService?.startBreak(10)
        }
    }

    private fun updateUI() {
        timerService?.let { service ->
            binding.tvTimerDisplay.text = service.formatTime(service.currentSeconds)
            
            val phaseLabel = when (service.currentPhase) {
                TetherTimerService.Phase.FOCUSING -> "Focusing"
                TetherTimerService.Phase.BREAK -> "Break"
            }
            binding.tvPhaseLabel.text = phaseLabel

            if (service.mode == TetherTimerService.TimerMode.STOPWATCH) {
                binding.layoutBreakOptions.visibility = 
                    if (service.currentPhase == TetherTimerService.Phase.FOCUSING) View.VISIBLE else View.GONE
            } else {
                binding.layoutBreakOptions.visibility = View.GONE
            }
        }
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): TimerControlFragment = TimerControlFragment()
    }
}

package com.tether.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tether.app.MainActivity
import com.tether.app.R
import com.tether.app.databinding.FragmentProfileBinding
import com.tether.app.utils.Formatters
import com.tether.app.utils.navigateSafe
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    private var renderedHeatmap: Map<String, Double>? = null
    private var renderedYear = 0
    private var scrolledToToday = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        render(viewModel.state.value)

        binding.btnAbout.setOnClickListener {
            findNavController().navigateSafe(R.id.action_profile_to_about)
        }
        binding.btnFaq.setOnClickListener {
            findNavController().navigateSafe(R.id.action_profile_to_faq)
        }

        binding.btnLogout.setOnClickListener {
            (activity as? MainActivity)?.logout()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ProfileUiState) {
        binding.tvProfileName.text = state.name
        binding.tvProfileEmail.text = state.email
        binding.tvProfileInitials.text = state.initials
        binding.tvStreakCount.text = state.streak.toString()
        binding.tvTotalHours.text = Formatters.formatHours(state.todayHours)
        binding.tvGroupCount.text = state.groupCount.toString()
        binding.tvHeatmapSubtitle.text = state.year.toString()

        // Only redraw the heatmap when its data actually changed.
        if (state.hoursByDate != renderedHeatmap || state.year != renderedYear) {
            renderedHeatmap = state.hoursByDate
            renderedYear = state.year
            binding.heatmapView.setData(state.year, state.hoursByDate)
        }

        // Start the heatmap scrolled to the current week instead of January.
        if (!scrolledToToday) {
            scrolledToToday = true
            binding.heatmapScroll.post {
                val b = _binding ?: return@post
                val x = b.heatmapView.todayColumnX()
                if (x >= 0) b.heatmapScroll.scrollTo((x - b.heatmapScroll.width / 2).coerceAtLeast(0), 0)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        renderedHeatmap = null
        scrolledToToday = false
    }
}

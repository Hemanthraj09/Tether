package com.tether.app.ui.tracks

import android.content.Intent
import android.net.Uri
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.tether.app.databinding.FragmentTrackBinding
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.launch

class TrackFragment : Fragment() {

    private var _binding: FragmentTrackBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrackViewModel by viewModels()

    private lateinit var adapter: TrackAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TrackAdapter(
            onOpen = { row -> row.item.link?.let(::openUrl) ?: TetherToast.show(requireContext(), "No link for this one") },
            onToggle = { row -> viewModel.toggle(row) }
        )
        binding.rvTrackItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrackItems.adapter = adapter
        binding.rvTrackItems.setHasFixedSize(true)

        binding.btnTrackBack.setOnClickListener { findNavController().popBackStack() }
        binding.chipHideDone.setOnCheckedChangeListener { _, checked -> viewModel.setHideCompleted(checked) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        if (state == null) return@collect
                        binding.progressTrackLoading.visibility = View.GONE
                        val track = state.track
                        binding.tvTrackName.text = track.name
                        binding.tvTrackAuthor.text = "Curated by ${track.author} · View original"
                        binding.tvTrackAuthor.setOnClickListener { openUrl(track.sourceUrl) }
                        binding.tvTrackProgress.text = "${state.myDone} / ${track.itemCount}"
                        binding.progressTrack.setProgressCompat(
                            if (track.itemCount == 0) 0 else state.myDone * 1000 / track.itemCount, true)
                        binding.tvTrackHint.text = if (track.verifier == "leetcode")
                            "Solved it on LeetCode? It ticks off automatically (green ✓). Tap the circle to tick other items by hand."
                        else "Tap the circle to tick items off."
                        binding.layoutTrackRace.visibility = if (state.standings.isEmpty()) View.GONE else View.VISIBLE
                        if (state.standings.isNotEmpty()) {
                            RaceStandingsView.render(binding.layoutTrackRace, state.standings, viewModel.leetCodeTotals.value)
                        }
                        adapter.submitList(state.rows)
                    }
                }
                launch {
                    viewModel.leetCodeTotals.collect { totals ->
                        val standings = viewModel.state.value?.standings.orEmpty()
                        if (standings.isNotEmpty()) RaceStandingsView.render(binding.layoutTrackRace, standings, totals)
                    }
                }
                launch {
                    viewModel.messages.collect { TetherToast.show(requireContext(), it) }
                }
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            TetherToast.show(requireContext(), "No app can open this link", isError = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

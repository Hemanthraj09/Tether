package com.tether.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tether.app.MainActivity
import com.tether.app.R
import com.tether.app.data.tracks.TrackRepository
import com.tether.app.databinding.FragmentProfileBinding
import com.tether.app.utils.TetherToast
import com.tether.app.utils.Formatters
import com.tether.app.utils.navigateSafe
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private val accounts: ConnectedAccountsViewModel by viewModels()

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

        binding.btnTracks.setOnClickListener { showTrackPicker() }
        binding.btnLeetCodeAction.setOnClickListener { onLeetCodeAction() }
        binding.rowLeetCode.setOnClickListener { onLeetCodeAction() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { accounts.leetCode.collect { renderLeetCode(it) } }
                launch {
                    accounts.messages.collect { (text, isError) ->
                        TetherToast.show(requireContext(), text, isError)
                    }
                }
            }
        }
    }

    // ── Connected accounts: LeetCode ─────────────────────────────

    private fun renderLeetCode(state: LeetCodeCardState) {
        when (state) {
            LeetCodeCardState.Loading -> {
                binding.tvLeetCodeStats.text = "Loading…"
                binding.tvLeetCodeDetail.visibility = View.GONE
                binding.btnLeetCodeAction.visibility = View.GONE
            }
            LeetCodeCardState.NotConnected -> {
                binding.tvLeetCodeTitle.text = "LeetCode"
                binding.tvLeetCodeStats.text = "Sync solved problems automatically"
                binding.tvLeetCodeDetail.visibility = View.GONE
                binding.btnLeetCodeAction.visibility = View.VISIBLE
                binding.btnLeetCodeAction.text = "Connect"
            }
            is LeetCodeCardState.Connected -> {
                binding.tvLeetCodeTitle.text = "LeetCode · @${state.username}"
                binding.btnLeetCodeAction.visibility = View.VISIBLE
                binding.btnLeetCodeAction.text = "Manage"
                val p = state.profile
                binding.tvLeetCodeStats.text = if (p == null) "Fetching stats…"
                    else "${p.solvedTotal} solved · ${p.easy} E · ${p.medium} M · ${p.hard} H" +
                        if (p.streak > 0) " · 🔥 ${p.streak}" else ""
                val strongest = p?.topics?.take(3)?.joinToString(" · ") { it.name }
                val syncLine = when {
                    state.syncError != null -> "Sync paused: ${state.syncError}"
                    state.lastSyncAt > 0 -> "Synced " + DateUtils.getRelativeTimeSpanString(
                        state.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                    else -> "Waiting for first sync…"
                }
                binding.tvLeetCodeDetail.visibility = View.VISIBLE
                binding.tvLeetCodeDetail.text =
                    if (strongest.isNullOrEmpty()) syncLine else "Strongest: $strongest\n$syncLine"
            }
        }
    }

    private fun onLeetCodeAction() {
        when (val state = accounts.leetCode.value) {
            LeetCodeCardState.NotConnected -> showConnectDialog()
            is LeetCodeCardState.Connected -> showManageDialog(state.username)
            LeetCodeCardState.Loading -> Unit
        }
    }

    private fun showConnectDialog() {
        val input = EditText(requireContext()).apply {
            hint = "LeetCode username or profile link"
            isSingleLine = true
            setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextPrimary))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextSecondary))
        }
        val container = FrameLayout(requireContext()).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Connect LeetCode")
            .setMessage("Problems you solve on LeetCode (on any device) will sync here automatically. Only public profile data is read.")
            .setView(container)
            .setPositiveButton("Connect") { _, _ -> accounts.connect(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageDialog(username: String) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("LeetCode · @$username")
            .setItems(arrayOf("Sync now", "Open LeetCode profile", "Disconnect")) { _, which ->
                when (which) {
                    0 -> accounts.syncNow()
                    1 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://leetcode.com/u/$username/")))
                    2 -> confirmDisconnect()
                }
            }
            .show()
    }

    private fun confirmDisconnect() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Disconnect LeetCode?")
            .setMessage("New solves will stop syncing. Problems already ticked stay ticked.")
            .setPositiveButton("Disconnect") { _, _ -> accounts.disconnect() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Tracks ───────────────────────────────────────────────────

    private fun showTrackPicker() {
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = TrackRepository(requireContext())
            val tracks = TrackRepository.ALL_IDS.mapNotNull { repo.get(it) }
            if (_binding == null || tracks.isEmpty()) return@launch
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Tracks")
                .setItems(tracks.map { "${it.name}  ·  ${it.itemCount} items" }.toTypedArray()) { _, which ->
                    findNavController().navigateSafe(
                        R.id.action_profile_to_track, bundleOf("trackId" to tracks[which].id))
                }
                .show()
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

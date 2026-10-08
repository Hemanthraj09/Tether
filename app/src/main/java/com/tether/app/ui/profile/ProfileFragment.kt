package com.tether.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.firebase.auth.FirebaseAuth
import com.tether.app.data.repository.AccountRepository
import com.tether.app.data.repository.AuthRepository
import com.tether.app.domain.Interests
import com.tether.app.databinding.FragmentProfileBinding
import com.tether.app.ui.common.TetherDialogs
import com.tether.app.databinding.ItemSaidRowBinding
import com.tether.app.domain.SaidVsDid
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
    /** Heatmap mode: hours logged (default) or verified LeetCode solves. */
    private var showSolves = false
    private var lastState: ProfileUiState? = null

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

        binding.btnEditFocus.setOnClickListener {
            findNavController().navigateSafe(R.id.action_profile_to_interests)
        }
        binding.btnDeleteAccount.setOnClickListener { confirmDeleteAccount() }
        binding.tvHeatmapToggle.setOnClickListener {
            showSolves = !showSolves
            renderedHeatmap = null           // force a redraw in the new mode
            lastState?.let { render(it) }
        }
        renderEmailVerification()
        binding.btnLeetCodeAction.setOnClickListener { onLeetCodeAction() }
        binding.rowLeetCode.setOnClickListener { onLeetCodeAction() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch {
                    accounts.leetCode.collect { state ->
                        renderLeetCode(state)
                        // The LeetCode heatmap is offered once an account is connected.
                        val connected = state is LeetCodeCardState.Connected
                        binding.tvHeatmapToggle.visibility = if (connected) View.VISIBLE else View.GONE
                        if (!connected && showSolves) {
                            showSolves = false
                            renderedHeatmap = null
                            lastState?.let { render(it) }
                        }
                    }
                }
                launch { viewModel.saidVsDid.collect { renderSaidVsDid(it) } }
                launch {
                    accounts.messages.collect { (text, isError) ->
                        TetherToast.show(requireContext(), text, isError)
                    }
                }
            }
        }
    }

    // ── Said vs. Did ─────────────────────────────────────────────

    private fun renderSaidVsDid(state: SaidVsDidState?) {
        if (state == null) return
        val container = binding.saidRows
        container.removeAllViews()
        if (state.rows.isEmpty()) {
            binding.tvSaidSubtitle.text =
                "Pick what you want to stay accountable for and set a weekly target for each."
            binding.btnEditFocus.text = "Set up"
            return
        }
        binding.tvSaidSubtitle.text = "This week · resets Sunday"
        binding.btnEditFocus.text = "Edit"
        val ctx = requireContext()
        state.rows.forEach { row ->
            val item = ItemSaidRowBinding.inflate(layoutInflater, container, true)
            item.tvAreaLabel.text = Interests.label(row.area)
            item.tvAreaValue.text =
                "${Formatters.formatHours(row.doneHours)} / ${Formatters.formatHours(row.targetHours)}"
            item.progressArea.progress = (row.fraction * 1000).toInt()
            val (text, color) = when (row.status) {
                SaidVsDid.Status.DONE -> "✓ Target hit" to COLOR_GOOD
                SaidVsDid.Status.ON_TRACK -> "On track" to COLOR_GOOD
                SaidVsDid.Status.BEHIND ->
                    "Behind pace: ${Formatters.formatHours(row.behindBy)} to catch up" to
                        ContextCompat.getColor(ctx, R.color.colorAccent)
                SaidVsDid.Status.NOT_STARTED ->
                    "You said this matters. Nothing logged yet this week." to COLOR_BAD
                SaidVsDid.Status.NO_GROUP ->
                    "No ${Interests.goalForArea(row.area)} group yet. Create one (solo works) so your hours count." to
                        ContextCompat.getColor(ctx, R.color.colorTextSecondary)
            }
            item.tvAreaStatus.text = text
            item.tvAreaStatus.setTextColor(color)
        }
    }

    // ── Connected accounts: LeetCode ─────────────────────────────

    private fun renderLeetCode(state: LeetCodeCardState) {
        when (state) {
            LeetCodeCardState.Loading -> {
                binding.tvLeetCodeStats.text = "Loading…"
                binding.btnLeetCodeAction.visibility = View.GONE
                binding.ivLeetCodeChevron.visibility = View.GONE
            }
            LeetCodeCardState.NotConnected -> {
                binding.tvLeetCodeTitle.text = "LeetCode"
                binding.tvLeetCodeStats.text = "Sync solved problems automatically"
                binding.btnLeetCodeAction.visibility = View.VISIBLE
                binding.btnLeetCodeAction.text = "Connect"
                binding.ivLeetCodeChevron.visibility = View.GONE
            }
            is LeetCodeCardState.Connected -> {
                // A one-line summary; everything else lives on the LeetCode page.
                binding.tvLeetCodeTitle.text = "LeetCode · @${state.username}" +
                    if (state.ownershipVerified) "  ✓" else ""
                binding.btnLeetCodeAction.visibility = View.GONE
                binding.ivLeetCodeChevron.visibility = View.VISIBLE
                val solved = state.profile?.let { "${it.solvedTotal} solved" }
                val status = when {
                    state.syncError != null -> "sync paused"
                    !state.ownershipVerified -> "tap to verify"
                    state.lastSyncAt > 0 -> "synced " + DateUtils.getRelativeTimeSpanString(
                        state.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                    else -> "syncing…"
                }
                binding.tvLeetCodeStats.text = listOfNotNull(solved, status).joinToString(" · ")
            }
        }
    }

    private fun onLeetCodeAction() {
        when (accounts.leetCode.value) {
            LeetCodeCardState.NotConnected -> showConnectDialog()
            is LeetCodeCardState.Connected -> findNavController().navigateSafe(R.id.action_profile_to_leetcode)
            LeetCodeCardState.Loading -> Unit
        }
    }

    private fun showConnectDialog() {
        TetherDialogs.input(
            requireContext(),
            title = "Connect LeetCode",
            message = "Problems you solve on LeetCode, on any device, sync here automatically. Only public profile data is read.",
            hint = "Username or profile link",
            confirmText = "Connect"
        ) { accounts.connect(it) }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            TetherToast.show(requireContext(), "No app can open this link", isError = true)
        }
    }

    // ── Account ──────────────────────────────────────────────────

    private fun renderEmailVerification() {
        val user = FirebaseAuth.getInstance().currentUser
        val isPasswordAccount = user?.providerData?.any { it.providerId == "password" } == true
        binding.tvEmailVerify.visibility =
            if (isPasswordAccount && user?.isEmailVerified == false) View.VISIBLE else View.GONE
        binding.tvEmailVerify.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                AuthRepository().resendEmailVerification()
                    .onSuccess { TetherToast.show(requireContext(), "Verification link sent to ${user?.email}") }
                    .onFailure { TetherToast.show(requireContext(), it.message ?: "Couldn't send", isError = true) }
            }
        }
    }

    private fun confirmDeleteAccount() {
        TetherDialogs.confirm(
            requireContext(),
            title = "Delete your account?",
            message = "This permanently deletes your profile, logs, photos, LeetCode history and login. " +
                "Groups you created are handed to the next member (or deleted if you're alone). This can't be undone.",
            confirmText = "Delete forever",
            destructive = true
        ) { deleteAccount() }
    }

    private fun deleteAccount() {
        binding.btnDeleteAccount.isEnabled = false
        binding.btnDeleteAccount.text = "Deleting…"
        viewLifecycleOwner.lifecycleScope.launch {
            AccountRepository().deleteAccount()
                .onSuccess {
                    TetherToast.show(requireContext(), "Your account has been deleted")
                    (activity as? MainActivity)?.logout()
                }
                .onFailure {
                    if (_binding == null) return@onFailure
                    binding.btnDeleteAccount.isEnabled = true
                    binding.btnDeleteAccount.text = "Delete account"
                    TetherToast.show(requireContext(), it.message ?: "Couldn't delete your account", isError = true)
                }
        }
    }


    private fun render(state: ProfileUiState) {
        lastState = state
        binding.tvProfileName.text = state.name
        binding.tvProfileEmail.text = state.email
        binding.tvProfileInitials.text = state.initials
        binding.tvStreakCount.text = state.streak.toString()
        binding.tvTotalHours.text = Formatters.formatHours(state.todayHours)
        binding.tvGroupCount.text = state.groupCount.toString()
        binding.tvHeatmapSubtitle.text =
            if (showSolves) "${state.year} · LeetCode problems solved" else "${state.year} · hours logged"
        binding.tvHeatmapToggle.text = if (showSolves) "LeetCode ⇄" else "Hours ⇄"

        // Only redraw the heatmap when its data actually changed.
        // One square per day: hours, or problems solved (1 level per problem).
        val heatmapData = if (showSolves) state.solvesByDate.mapValues { it.value.toDouble() } else state.hoursByDate
        if (heatmapData != renderedHeatmap || state.year != renderedYear) {
            renderedHeatmap = heatmapData
            renderedYear = state.year
            binding.heatmapView.setData(state.year, heatmapData)
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

    companion object {
        private val COLOR_GOOD = android.graphics.Color.parseColor("#4CAF50")
        private val COLOR_BAD = android.graphics.Color.parseColor("#EF4444")
    }
}

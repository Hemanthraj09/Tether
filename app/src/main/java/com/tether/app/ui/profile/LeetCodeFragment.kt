package com.tether.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.tether.app.R
import com.tether.app.databinding.ContentVerifyCodeBinding
import com.tether.app.databinding.FragmentLeetcodeBinding
import com.tether.app.ui.common.TetherDialogs
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.launch

/**
 * Connected account detail page: everything about the LeetCode connection
 * (stats, topics, sync state, ownership) and its actions, instead of
 * cramming them into the profile row.
 */
class LeetCodeFragment : Fragment() {

    private var _binding: FragmentLeetcodeBinding? = null
    private val binding get() = _binding!!
    private val accounts: ConnectedAccountsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLeetcodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnSyncNow.setOnClickListener { accounts.syncNow() }
        binding.btnDisconnect.setOnClickListener { confirmDisconnect() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    accounts.leetCode.collect { state ->
                        when (state) {
                            LeetCodeCardState.Loading -> Unit
                            // Disconnected (here or elsewhere): nothing to show.
                            LeetCodeCardState.NotConnected ->
                                if (findNavController().currentDestination?.id == R.id.leetCodeFragment) {
                                    findNavController().popBackStack()
                                }
                            is LeetCodeCardState.Connected -> render(state)
                        }
                    }
                }
                launch {
                    accounts.messages.collect { (text, isError) -> TetherToast.show(requireContext(), text, isError) }
                }
            }
        }
    }

    private fun render(state: LeetCodeCardState.Connected) {
        val username = state.username
        binding.tvHandle.text = "@$username"
        binding.btnOpenProfile.setOnClickListener { openUrl("https://leetcode.com/u/$username/") }

        val verified = state.ownershipVerified
        binding.tvVerifiedBadge.text = if (verified) "✓ Verified owner" else "Not verified"
        binding.tvVerifiedBadge.setTextColor(
            if (verified) Color.parseColor("#4CAF50") else ContextCompat.getColor(requireContext(), R.color.colorTextSecondary))
        binding.tvOwnership.text = if (verified)
            "Your Tether code is in your LeetCode bio, so friends see a ✓ next to your solves. Keep it there."
        else "Prove this account is yours: add a short code to your LeetCode bio. Friends then see a ✓ next to your solves."
        binding.btnVerify.text = if (verified) "Check again" else "Verify ownership"
        binding.btnVerify.setOnClickListener {
            if (verified) accounts.checkOwnership(username) else showVerifyDialog(username)
        }

        val p = state.profile
        binding.tvSolvedTotal.text = p?.solvedTotal?.toString() ?: "–"
        binding.tvEasy.text = p?.easy?.toString() ?: "–"
        binding.tvMedium.text = p?.medium?.toString() ?: "–"
        binding.tvHard.text = p?.hard?.toString() ?: "–"
        val streakParts = listOfNotNull(
            p?.streak?.takeIf { it > 0 }?.let { "🔥 $it-day LeetCode streak" },
            p?.activeDays?.takeIf { it > 0 }?.let { "$it active days" },
            p?.ranking?.takeIf { it > 0 }?.let { "rank ${"%,d".format(it)}" }
        )
        binding.tvStreakLine.text = streakParts.joinToString(" · ")
        binding.tvStreakLine.visibility = if (streakParts.isEmpty()) View.GONE else View.VISIBLE

        val topics = p?.topics.orEmpty().take(6)
        binding.cardTopics.visibility = if (topics.isEmpty()) View.GONE else View.VISIBLE
        binding.chipsTopics.removeAllViews()
        topics.forEach { topic ->
            binding.chipsTopics.addView(Chip(requireContext()).apply {
                text = "${topic.name} · ${topic.solved}"
                isClickable = false
                setChipBackgroundColorResource(R.color.colorElevated)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextPrimary))
                chipStrokeWidth = 0f
            })
        }

        binding.tvSyncStatus.text = when {
            state.syncError != null -> "Sync paused: ${state.syncError}"
            state.lastSyncAt > 0 -> "Synced " + DateUtils.getRelativeTimeSpanString(
                state.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) +
                " · runs every few hours in the background"
            else -> "Waiting for the first sync…"
        }
        binding.tvSyncStatus.setTextColor(
            if (state.syncError != null) ContextCompat.getColor(requireContext(), R.color.colorAccent)
            else ContextCompat.getColor(requireContext(), R.color.colorTextSecondary))
    }

    /**
     * Ownership proof: put a code derived from your Tether account into your public
     * LeetCode bio. Anyone can check it, so nobody can pass off your handle as theirs.
     */
    private fun showVerifyDialog(username: String) {
        val code = accounts.ownershipCode ?: return
        val content = ContentVerifyCodeBinding.inflate(layoutInflater)
        content.tvVerifyCode.text = code
        content.tvVerifyCode.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Tether code", code))
            TetherToast.show(requireContext(), "Copied $code")
        }
        content.tvOpenLeetCode.setOnClickListener { openUrl("https://leetcode.com/profile/") }
        TetherDialogs.confirm(
            requireContext(),
            title = "Verify it's your LeetCode",
            message = "Add this code anywhere in your LeetCode profile Summary (Edit Profile), then tap Check. " +
                "Keep it there so friends see you as verified.",
            confirmText = "Check",
            cancelText = "Later",
            content = content.root
        ) { accounts.checkOwnership(username) }
    }

    private fun confirmDisconnect() {
        TetherDialogs.confirm(
            requireContext(),
            title = "Disconnect LeetCode?",
            message = "New solves will stop syncing. Solves already synced stay on your heatmap.",
            confirmText = "Disconnect",
            destructive = true
        ) { accounts.disconnect() }
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

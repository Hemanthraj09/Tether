package com.tether.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.tether.app.R
import com.tether.app.databinding.FragmentFaqBinding

class FaqFragment : Fragment() {
    private var _binding: FragmentFaqBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFaqBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        val faqs = listOf(
            "How do I create a group?" to "Tap the + button on the home screen, enter a group name and pick a goal type. Share the invite code with your friends.",
            "How do I invite friends?" to "After creating a group, tap the ⓘ icon on the group feed to see your invite code. Share it with friends and they can join from the home screen.",
            "How does the streak work?" to "Log at least some hours every day to keep your streak alive. Missing a day resets it to 0. Streaks are per group, not global.",
            "What does the nudge do?" to "Tap someone's avatar on the leaderboard or group feed to send them a nudge notification. You can only nudge each person once per day.",
            "How does the timer work?" to "Tap 'Start Session' on the group feed. Choose Stopwatch for open-ended sessions or Pomodoro for 25/5 or 50/10 focus/break blocks. The timer keeps running in the background; stop it when you're done and only your focus time is logged.",
            "Why did my hours reset?" to "Hours reset every day at midnight. The leaderboard shows today's hours by default. Switch to 'This Week' to see your weekly total, and tap the group name to switch groups.",
            "How does LeetCode syncing work?" to "Connect your LeetCode username under Profile → Connected accounts. Problems you solve on LeetCode, on any device, sync automatically every few hours and whenever you open Tether. In Coding groups, a verified solve keeps your streak alive for the day, every member shows problems solved today, and the group creator can rank the leaderboard by problems instead of hours. Only public profile data is read.",
            "What does the ✓ next to my LeetCode mean?" to "It means you proved the account is yours: Profile → Connected accounts → Manage → Verify ownership gives you a short code to add to your LeetCode bio. Friends' phones check it, and solves that LeetCode's public data contradicts are shown as unconfirmed.",
            "How do I delete my account?" to "Profile → Delete account (at the bottom). Your profile, logs, LeetCode history and login are permanently deleted. Groups you created are handed to the next member, or deleted if you're the only one. For security, you may need to log in again first.",
            "Why do I have to say what I did?" to "Hours alone don't mean much. A short line (\"Solved 3 graph problems\") tells your group what the time went into, and it shows in the group's Today list and in everyone's bell.",
            "How does photo proof work?" to "The group's creator picks it under ⋮ → Photo proof: Off, Optional, or Required. In Required groups every manual log needs a fresh camera photo (no gallery). Focus-timer sessions don't need one, since the timer measured the time. Photos are shrunk on your phone (about 60 KB, location data removed), only your group can see them, and they disappear after the day.",
            "What do ✓ and 🤨 mean?" to "In a group's Today list you can back a friend's log with ✓ or question it with 🤨. It doesn't change anyone's hours; it's how your group keeps each other honest. You can't react to your own logs.",
            "What is Said vs. Did?" to "On your profile: the focus areas you picked, each with a weekly target, against the hours you actually logged this week in groups with that goal. Edit the areas and targets any time. Hours in groups with the goal \"Other\" don't count toward an area.",
            "Can I be in multiple groups?" to "Yes! You can create or join multiple groups, each with different goals and friends.",
            "How do I leave or delete a group?" to "Long press a group card on the home screen. If you created the group you can delete it; otherwise you can leave it. Deleting a group removes it for all members. Maximum 6 members per group."
        )

        val container = binding.faqInner
        faqs.forEachIndexed { index, (question, answer) ->
            val itemLayout = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val questionView = android.widget.TextView(requireContext()).apply {
                text = question
                textSize = 16f
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.colorTextPrimary))
                setPadding(0, 16, 0, 16)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val answerView = android.widget.TextView(requireContext()).apply {
                text = answer
                textSize = 15f
                setLineSpacing(0f, 1.5f)
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.colorTextSecondary))
                visibility = View.GONE
                setPadding(0, 0, 0, 16)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val divider = View(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.colorBorder))
            }
            questionView.setOnClickListener {
                answerView.visibility = if (answerView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            itemLayout.addView(questionView)
            itemLayout.addView(answerView)
            if (index < faqs.size - 1) itemLayout.addView(divider)
            container.addView(itemLayout)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

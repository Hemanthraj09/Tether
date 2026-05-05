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
            "How does the timer work?" to "Tap 'Start Session' on the group feed. Choose Stopwatch for open-ended sessions or Pomodoro for 25-minute focus blocks with 5-minute breaks. The timer runs in the background.",
            "Why did my hours reset?" to "Hours reset every day at midnight. The leaderboard shows today's hours by default. Switch to 'This Week' to see your weekly total.",
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

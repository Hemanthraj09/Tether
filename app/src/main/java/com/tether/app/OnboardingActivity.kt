package com.tether.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.viewpager2.widget.ViewPager2
import com.tether.app.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        val slides = listOf(
            OnboardingSlideFragment.newInstance(
                "Welcome to Tether",
                "Accountability, built for your circle. Track your grind and stay ahead of your friends.",
                R.drawable.ic_tether_logo
            ),
            OnboardingSlideFragment.newInstance(
                "Log Your Progress",
                "Log study, gym, coding — anything. Add hours, minutes, and an optional note. Every entry feeds your group in real time.",
                R.drawable.ic_add
            ),
            OnboardingSlideFragment.newInstance(
                "Groups & Invite Codes",
                "Create a group and share the 6-character invite code with your friends. Max 6 members per group — tight circles only. Creators can delete, members can leave.",
                R.drawable.ic_group
            ),
            OnboardingSlideFragment.newInstance(
                "Nudge Your Friends",
                "Tap anyone's avatar on the leaderboard to send them a nudge notification. One nudge per person per day — use it wisely.",
                R.drawable.ic_notifications
            ),
            OnboardingSlideFragment.newInstance(
                "Focus Timer",
                "Stopwatch mode tracks real work time. Pomodoro mode runs 25-min focus blocks and auto-logs your session when done. Runs in the background with a persistent notification.",
                R.drawable.ic_flame // Using ic_flame as ic_timer replacement if not found
            ),
            OnboardingSlideFragment.newInstance(
                "Heatmap & Leaderboard",
                "Your profile shows a full-year heatmap of your activity and per-group streaks. The leaderboard resets daily — show up every day to stay on top.",
                R.drawable.ic_trophy // Using ic_trophy as ic_bar_chart replacement
            )
        )

        binding.viewPager.adapter = OnboardingAdapter(this, slides)
        binding.viewPager.setPageTransformer(DepthPageTransformer())
        
        setupDotIndicator(slides.size)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateButtons(position, slides.size)
            }
        })
    }

    private fun setupDotIndicator(count: Int) {
        for (i in 0 until count) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(8.dpToPx(), 8.dpToPx())
            params.setMargins(4.dpToPx(), 0, 4.dpToPx(), 0)
            dot.layoutParams = params
            dot.setBackgroundResource(R.drawable.dot_unselected)
            binding.dotIndicator.addView(dot)
        }
        updateDots(0)
    }

    private fun updateDots(selectedPosition: Int) {
        binding.dotIndicator.children.forEachIndexed { index, view ->
            val isSelected = index == selectedPosition
            val targetWidth = if (isSelected) 24.dpToPx() else 8.dpToPx()
            val currentWidth = view.layoutParams.width

            if (currentWidth != targetWidth) {
                ValueAnimator.ofInt(currentWidth, targetWidth).apply {
                    duration = 300
                    addUpdateListener { animator ->
                        view.layoutParams.width = animator.animatedValue as Int
                        view.requestLayout()
                    }
                    start()
                }
            }
            view.setBackgroundResource(if (isSelected) R.drawable.dot_selected else R.drawable.dot_unselected)
        }
    }

    private fun updateButtons(position: Int, total: Int) {
        val isLastPage = position == total - 1
        binding.btnNext.text = if (isLastPage) "Get Started" else "Next"
        binding.tvSkip.visibility = if (isLastPage) View.GONE else View.VISIBLE

        if (isLastPage) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }
    }

    private fun startPulseAnimation() {
        if (pulseAnimator == null) {
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.btnNext,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.05f, 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.05f, 1f)
            ).apply {
                duration = 800
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
        }
        pulseAnimator?.start()
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        binding.btnNext.scaleX = 1f
        binding.btnNext.scaleY = 1f
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < (binding.viewPager.adapter?.itemCount ?: 0) - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }

        binding.tvSkip.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        stopPulseAnimation()
        val prefs = getSharedPreferences("tether_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    class DepthPageTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val pageWidth = page.width
            when {
                position < -1 -> page.alpha = 0f
                position <= 0 -> {
                    page.alpha = 1f
                    page.translationX = 0f
                    page.scaleX = 1f
                    page.scaleY = 1f
                }
                position <= 1 -> {
                    page.alpha = 1 - position
                    page.translationX = pageWidth * -position
                    val scale = 0.85f + (1 - 0.85f) * (1 - Math.abs(position))
                    page.scaleX = scale
                    page.scaleY = scale
                }
                else -> page.alpha = 0f
            }
        }
    }
}
package com.tether.app

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tether.app.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        playAnimation()
    }

    /**
     * Same sequence as before (logo bounce → wordmark → tagline), but the steps
     * overlap, so the splash takes ~1.1s instead of ~2s on every launch.
     */
    private fun playAnimation() {
        binding.ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(450)
            .setStartDelay(80)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        binding.tvAppName.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setStartDelay(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.tvTagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(480)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.root.postDelayed({ launchNext() }, 300)
            }
            .start()
    }

    private fun launchNext() {
        if (launched || isFinishing) return
        launched = true
        val onboardingDone = getSharedPreferences("tether_prefs", MODE_PRIVATE)
            .getBoolean("onboarding_complete", false)
        val next = if (onboardingDone) MainActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, next))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

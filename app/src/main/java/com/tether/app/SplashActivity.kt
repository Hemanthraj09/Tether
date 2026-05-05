package com.tether.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tether.app.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

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

    private fun playAnimation() {
        // Step 1: Logo scales up and fades in
        binding.ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setStartDelay(200)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .withEndAction {
                // Step 2: Wordmark slides up and fades in
                binding.tvAppName.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction {
                        // Step 3: Tagline fades in
                        binding.tvTagline.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(350)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .withEndAction {
                                // Step 4: Hold for a moment then launch MainActivity
                                binding.root.postDelayed({
                                    startActivity(
                                        Intent(this, MainActivity::class.java)
                                    )
                                    overridePendingTransition(
                                        android.R.anim.fade_in,
                                        android.R.anim.fade_out
                                    )
                                    finish()
                                }, 600)
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }
}

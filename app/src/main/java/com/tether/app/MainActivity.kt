package com.tether.app

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.tether.app.data.repository.AuthRepository
import com.tether.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowDecor()
        setupNavController()
        setupBackPress()
    }

    private fun setupWindowDecor() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(
            window, binding.root)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    private fun setupNavController() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment)
                as NavHostFragment
        navController = navHostFragment.navController

        val authRepository = AuthRepository()
        if (authRepository.isLoggedIn) {
            navController.navigate(
                R.id.groupListFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.authFragment, true)
                    .setLaunchSingleTop(true)
                    .build()
            )
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentDest = navController.currentDestination?.id
                    when (currentDest) {
                        R.id.groupListFragment -> finish()
                        R.id.leaderboardFragment,
                        R.id.profileFragment,
                        R.id.groupFragment -> {
                            navController.navigate(
                                R.id.groupListFragment,
                                null,
                                androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.groupListFragment, true)
                                    .setLaunchSingleTop(true)
                                    .build()
                            )
                        }
                        else -> {
                            navController.popBackStack()
                        }
                    }
                }
            }
        )
    }
}

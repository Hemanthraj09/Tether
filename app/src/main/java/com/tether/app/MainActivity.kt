package com.tether.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.tether.app.data.leetcode.LeetCodeConfig
import com.tether.app.data.leetcode.LeetCodeSyncState
import com.tether.app.data.repository.AuthRepository
import com.tether.app.sync.LeetCodeSyncWorker
import com.tether.app.databinding.ActivityMainBinding
import com.tether.app.timer.TetherTimerService
import com.tether.app.utils.RealtimeWatcher

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var watcherAcquired = false
    private var signedInServicesStarted = false

    private val requestPermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // permissions granted or denied — no action needed, system handles it
    }

    private val tabDestinations = mapOf(
        R.id.groupListFragment to R.id.nav_home,
        R.id.leaderboardFragment to R.id.nav_leaderboard,
        R.id.profileFragment to R.id.nav_profile
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("tether_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_complete", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // The root layout already paints the background; skip the window's
        // identical full-screen layer (less overdraw on every frame).
        window.setBackgroundDrawable(null)

        setupWindowDecor()
        setupNavController()
        RealtimeWatcher.acquire(this)
        watcherAcquired = true
        TetherTimerService.restoreIfNeeded(this)
        requestAppPermissions()

        if (savedInstanceState == null) startSignedInServices()

        if (savedInstanceState == null) {
            handleNavigateIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigateIntent(intent)
    }

    /** Opens a group from a timer/nudge notification tap. */
    private fun handleNavigateIntent(intent: Intent?) {
        val groupId = intent?.getStringExtra(EXTRA_NAVIGATE_TO_GROUP) ?: return
        intent.removeExtra(EXTRA_NAVIGATE_TO_GROUP)
        if (groupId.isEmpty() || !AuthRepository().isLoggedIn) return

        // Already looking at that group: nothing to do.
        val current = navController.currentBackStackEntry
        if (current?.destination?.id == R.id.groupFeedFragment &&
            current.arguments?.getString("groupId") == groupId
        ) return

        // The group screen loads its name/goal itself, so navigate immediately.
        navController.navigate(
            R.id.groupFeedFragment,
            bundleOf("groupId" to groupId, "groupName" to "", "groupGoal" to ""),
            NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.slide_out_left)
                .setPopEnterAnim(R.anim.slide_in_left)
                .setPopExitAnim(R.anim.slide_out_right)
                .build()
        )
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

        // Start directly on the right screen: no Auth screen flash for
        // signed-in users. (After a recreation the saved back stack is restored.)
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            if (AuthRepository().isLoggedIn) R.id.groupListFragment else R.id.authFragment
        )
        navController.setGraph(graph, null)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Return to the existing home screen (keeps its state).
                    if (!navController.popBackStack(R.id.groupListFragment, false, true)) {
                        navController.navigate(R.id.groupListFragment)
                    }
                    true
                }
                R.id.nav_leaderboard -> {
                    navigateToTab(R.id.leaderboardFragment)
                    true
                }
                R.id.nav_profile -> {
                    navigateToTab(R.id.profileFragment)
                    true
                }
                else -> false
            }
        }
        // Tapping the current tab again does nothing (no reload).
        binding.bottomNav.setOnItemReselectedListener { }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val tabItem = tabDestinations[destination.id]
            binding.bottomNav.visibility = if (tabItem != null) View.VISIBLE else View.GONE
            // Keep the highlighted tab in sync (e.g. after pressing back).
            // Setting isChecked does not trigger the selection listener.
            if (tabItem != null) binding.bottomNav.menu.findItem(tabItem)?.isChecked = true
            // Covers logging in during this session.
            if (destination.id == R.id.groupListFragment) startSignedInServices()
        }
    }

    /** Remote Config + LeetCode background sync, once per activity, only when signed in. */
    private fun startSignedInServices() {
        if (signedInServicesStarted || !AuthRepository().isLoggedIn) return
        signedInServicesStarted = true
        LeetCodeConfig.init()
        LeetCodeSyncWorker.schedulePeriodic(this)
        LeetCodeSyncWorker.syncNow(this)
    }

    /**
     * Tabs keep their state: switching away saves the tab's back stack and
     * switching back restores it (no reloads, no flicker).
     */
    private fun navigateToTab(destinationId: Int) {
        navController.navigate(
            destinationId,
            null,
            NavOptions.Builder()
                .setPopUpTo(R.id.groupListFragment, false, true)
                .setRestoreState(true)
                .setLaunchSingleTop(true)
                .setEnterAnim(R.anim.fade_in)
                .setExitAnim(R.anim.fade_out)
                .setPopEnterAnim(R.anim.fade_in)
                .setPopExitAnim(R.anim.fade_out)
                .build()
        )
    }

    /** Signs out and restarts the activity with a clean back stack. */
    fun logout() {
        LeetCodeSyncWorker.cancelAll(this)
        LeetCodeSyncState.reset(this)
        AuthRepository().logout()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionsLauncher.launch(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (watcherAcquired) RealtimeWatcher.release()
    }

    companion object {
        const val EXTRA_NAVIGATE_TO_GROUP = "navigateToGroupId"
    }
}

package com.tether.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.tether.app.data.repository.AuthRepository
import com.tether.app.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var nudgeListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowDecor()
        setupNavController()
        setupBackPress()
        startNudgeListener()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTimerNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        handleTimerNotificationIntent(intent)
    }

    private fun handleTimerNotificationIntent(intent: Intent?) {
        val groupId = intent?.getStringExtra("navigateToGroupId")
            ?: return
        if (groupId.isEmpty()) return
        // Clear the extra so it doesn't re-trigger on rotation
        intent.removeExtra("navigateToGroupId")

        // Fetch group details and navigate to GroupFeedFragment
        lifecycleScope.launch {
            try {
                val groupDoc = com.google.firebase.firestore
                    .FirebaseFirestore.getInstance()
                    .collection("groups")
                    .document(groupId)
                    .get()
                    .await()
                val groupName = groupDoc.getString("name") ?: ""
                val goalType = groupDoc.getString("goalType") ?: ""
                val bundle = android.os.Bundle().apply {
                    putString("groupId", groupId)
                    putString("groupName", groupName)
                    putString("groupGoal", goalType)
                }
                findNavController(R.id.navHostFragment)
                    .navigate(R.id.groupFeedFragment, bundle)
            } catch (e: Exception) {}
        }
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

    private fun startNudgeListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        nudgeListener = FirebaseFirestore.getInstance()
            .collectionGroup("nudges")
            .whereEqualTo("nudgedUid", uid)
            .whereGreaterThan("timestamp",
                System.currentTimeMillis() - 5000)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type ==
                        com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val nudgerName = change.document
                            .getString("nudgerName") ?: "Someone"
                        showNudgeNotification(nudgerName)
                    }
                }
            }
    }

    private fun showNudgeNotification(nudgerName: String) {
        val channelId = "nudge_channel"
        val notificationManager = getSystemService(
            android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Nudges",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat
            .Builder(this, channelId)
            .setContentTitle("⚡ You got nudged!")
            .setContentText("$nudgerName nudged you! Time to get back on track.")
            .setSmallIcon(R.drawable.ic_flame)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(
            System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        nudgeListener?.remove()
    }
}

package com.tether.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.tether.app.data.repository.LeetCodeRepository
import java.util.concurrent.TimeUnit

/**
 * Background LeetCode sync. Solving happens on a laptop; this pulls the result
 * from LeetCode's servers to the phone, so nothing has to be logged by hand.
 *
 * Always reports success to WorkManager: retry pacing is handled by our own
 * exponential backoff (LeetCodeSyncState), so failures never stack up.
 */
class LeetCodeSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        val force = inputData.getBoolean(KEY_FORCE, false)
        val outcome = LeetCodeRepository(applicationContext).sync(force)
        android.util.Log.d("LeetCodeSync", "sync outcome: $outcome")
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "leetcode-sync-periodic"
        private const val ONE_TIME_NAME = "leetcode-sync-now"
        private const val KEY_FORCE = "force"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Every 3 hours while connected; survives app restarts and reboots. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<LeetCodeSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One sync as soon as there's a network ([force] = manual "Sync now", skips backoff). */
        fun syncNow(context: Context, force: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<LeetCodeSyncWorker>()
                .setConstraints(networkConstraint)
                .setInputData(workDataOf(KEY_FORCE to force))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_NAME)
        }
    }
}

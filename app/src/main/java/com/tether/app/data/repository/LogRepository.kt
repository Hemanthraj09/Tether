package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tether.app.data.UserCache
import com.tether.app.data.model.Log
import com.tether.app.utils.DateKeys
import com.tether.app.utils.Formatters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class LogRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    /**
     * Writes a log and updates daily/weekly stats, total hours and the
     * per-group streak in ONE atomic batch.
     *
     * Firestore applies the batch to the local cache immediately, so the
     * leaderboard/feed listeners update without waiting for the server.
     */
    suspend fun writeLog(
        groupId: String,
        hours: Double,
        note: String
    ): Result<Unit> {
        return try {
            val uid = currentUid
            if (uid.isEmpty()) return Result.failure(Exception("Not signed in"))
            if (groupId.isEmpty()) return Result.failure(Exception("No group selected"))

            val today = DateKeys.today()
            val weekKey = DateKeys.weekKey()

            val statsRef = firestore.collection("groupStats").document(groupId)
            val streakRef = statsRef.collection("streaks").document(uid)

            // Both reads run in parallel (the name is usually cached in memory).
            val (userName, streakDoc) = coroutineScope {
                val name = async { UserCache.currentUserName() }
                // Offline with nothing cached (e.g. the very first log) must not
                // block the log itself; treat it as "no streak yet".
                val streak = async {
                    try { streakRef.get().await() } catch (e: Exception) { null }
                }
                name.await() to streak.await()
            }

            val lastLogDate = streakDoc?.getString("lastLogDate") ?: ""
            val currentStreak = streakDoc?.getLong("currentStreak")?.toInt() ?: 0
            val longestStreak = streakDoc?.getLong("longestStreak")?.toInt() ?: 0

            val newStreak = when (lastLogDate) {
                today -> currentStreak
                DateKeys.previousDay(today) -> currentStreak + 1
                else -> 1
            }

            val logRef = firestore.collection("logs").document()
            val log = Log(
                id = logRef.id,
                userId = uid,
                groupId = groupId,
                userName = userName,
                userInitials = Formatters.initials(userName),
                avatarColorHex = Formatters.avatarColor(uid),
                date = today,
                value = hours,
                note = note,
                createdAt = System.currentTimeMillis()
            )

            val increment = FieldValue.increment(hours)
            firestore.batch().apply {
                set(logRef, log)
                set(statsRef.collection("daily").document(today),
                    mapOf(uid to increment), SetOptions.merge())
                set(statsRef.collection("weekly").document(weekKey),
                    mapOf(uid to increment), SetOptions.merge())
                set(firestore.collection("users").document(uid),
                    mapOf("totalHours" to increment), SetOptions.merge())
                set(streakRef, mapOf(
                    "lastLogDate" to today,
                    "currentStreak" to newStreak,
                    "longestStreak" to maxOf(newStreak, longestStreak)
                ), SetOptions.merge())
            }.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

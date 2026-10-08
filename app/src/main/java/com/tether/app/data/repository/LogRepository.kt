package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tether.app.data.UserCache
import com.tether.app.data.model.Log
import com.tether.app.domain.Proof
import com.tether.app.domain.StreakCalculator
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
     * Writes a log (plus its proof photo, if any) and updates daily/weekly
     * stats, total hours and the per-group streak in ONE atomic batch.
     *
     * Firestore applies the batch to the local cache immediately, so the
     * leaderboard/feed listeners update without waiting for the server.
     */
    suspend fun writeLog(
        groupId: String,
        hours: Double,
        note: String,
        source: String = Proof.SOURCE_MANUAL,
        photo: ByteArray? = null
    ): Result<Unit> {
        return try {
            val uid = currentUid
            if (uid.isEmpty()) return Result.failure(Exception("Not signed in"))
            if (groupId.isEmpty()) return Result.failure(Exception("No group selected"))
            if (!Proof.isNoteValid(note)) return Result.failure(Exception("Add a short note on what you did"))
            if (photo != null && photo.size > Proof.MAX_BYTES) return Result.failure(Exception("That photo is too large"))

            val today = DateKeys.today()
            val weekKey = DateKeys.weekKey()

            val statsRef = firestore.collection("groupStats").document(groupId)
            val streakRef = statsRef.collection("streaks").document(uid)

            val dailyRef = statsRef.collection("daily").document(today)

            // Reads run in parallel (the name is usually cached in memory).
            val (userName, streakDoc, dailyDoc) = coroutineScope {
                val name = async { UserCache.currentUserName() }
                // Offline with nothing cached (e.g. the very first log) must not
                // block the log itself; treat it as "no streak yet".
                val streak = async { runCatching { streakRef.get().await() }.getOrNull() }
                val daily = async { runCatching { dailyRef.get().await() }.getOrNull() }
                Triple(name.await(), streak.await(), daily.await())
            }

            // Friendly version of the server-side cap (the rules reject > 24h/day).
            val loggedToday = (dailyDoc?.get(uid) as? Number)?.toDouble() ?: 0.0
            if (loggedToday + hours > MAX_HOURS_PER_DAY + 0.0001) {
                val left = (MAX_HOURS_PER_DAY - loggedToday).coerceAtLeast(0.0)
                return Result.failure(Exception(
                    "That would put you over 24h today (you can log ${Formatters.formatHours(left)} more)."))
            }

            val previous = streakDoc?.takeIf { it.exists() }?.let {
                StreakCalculator.Streak(
                    current = it.getLong("currentStreak")?.toInt() ?: 0,
                    longest = it.getLong("longestStreak")?.toInt() ?: 0,
                    lastLogDate = it.getString("lastLogDate") ?: ""
                )
            }
            val newStreak = StreakCalculator.afterLog(previous, today)

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
                note = note.trim(),
                createdAt = System.currentTimeMillis(),
                source = source,
                hasPhoto = photo != null
            )

            // Every stats increase cites the log that justifies it ("log_<uid>"); the
            // security rules check they match, so leaderboard hours can't be inflated.
            val increment = FieldValue.increment(hours)
            val statsUpdate = mapOf(uid to increment, "log_$uid" to logRef.id)
            firestore.batch().apply {
                set(logRef, log)
                // Proof photo: same batch, so the rules can tie it to this new log.
                if (photo != null) {
                    set(statsRef.collection("proofs").document(logRef.id), mapOf(
                        "uid" to uid,
                        "date" to today,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "image" to Blob.fromBytes(photo)
                    ))
                }
                set(dailyRef, statsUpdate, SetOptions.merge())
                set(statsRef.collection("weekly").document(weekKey), statsUpdate, SetOptions.merge())
                set(firestore.collection("users").document(uid),
                    mapOf("totalHours" to increment), SetOptions.merge())
                // Personal heatmap: one small doc per year instead of reading every log.
                set(firestore.collection("users").document(uid).collection("heatmap").document(today.substring(0, 4)),
                    mapOf(today to increment), SetOptions.merge())
                set(streakRef, mapOf(
                    "lastLogDate" to newStreak.lastLogDate,
                    "currentStreak" to newStreak.current,
                    "longestStreak" to newStreak.longest
                ), SetOptions.merge())
            }.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val MAX_HOURS_PER_DAY = 24.0
    }
}

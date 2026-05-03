package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tether.app.data.model.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LogRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    private fun getTodayString(): String {
        val sdf = SimpleDateFormat(
            "yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getCurrentWeekKey(): String {
        val cal = Calendar.getInstance()
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        return "$year-W$week"
    }

    suspend fun writeLog(
        groupId: String,
        hours: Double,
        note: String
    ): Result<Unit> {
        return try {
            val userDoc = firestore
                .collection("users")
                .document(currentUid)
                .get()
                .await()

            val userName = userDoc
                .getString("name") ?: "Unknown"
            val initials = userName
                .split(" ")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .take(2)
                .joinToString("")
                .uppercase()

            val avatarColors = listOf(
                "#3B82F6", "#22C55E", "#A855F7",
                "#EC4899", "#EAB308", "#EF4444",
                "#F97316", "#06B6D4"
            )
            val avatarColor = avatarColors[
                currentUid.hashCode()
                    .and(0x7fffffff)
                    .rem(avatarColors.size)]

            val today = getTodayString()
            val weekKey = getCurrentWeekKey()

            val logRef = firestore
                .collection("logs")
                .document()

            val log = Log(
                id = logRef.id,
                userId = currentUid,
                groupId = groupId,
                userName = userName,
                userInitials = initials,
                avatarColorHex = avatarColor,
                date = today,
                value = hours,
                note = note,
                createdAt = System.currentTimeMillis()
            )

            logRef.set(log).await()

            val statsRef = firestore
                .collection("groupStats")
                .document(groupId)

            statsRef.collection("daily")
                .document(today)
                .set(mapOf(currentUid to
                    com.google.firebase.firestore
                        .FieldValue.increment(hours)),
                    com.google.firebase.firestore
                        .SetOptions.merge())
                .await()

            statsRef.collection("weekly")
                .document(weekKey)
                .set(mapOf(currentUid to
                    com.google.firebase.firestore
                        .FieldValue.increment(hours)),
                    com.google.firebase.firestore
                        .SetOptions.merge())
                .await()

            firestore.collection("users")
                .document(currentUid)
                .update("totalHours",
                    com.google.firebase.firestore
                        .FieldValue.increment(hours))
                .await()

            val streakRef = firestore
                .collection("groupStats")
                .document(groupId)
                .collection("streaks")
                .document(currentUid)

            val streakDoc = streakRef.get().await()
            val lastLogDate = streakDoc.getString("lastLogDate") ?: ""
            val currentStreak = streakDoc.getLong("currentStreak")?.toInt() ?: 0
            val longestStreak = streakDoc.getLong("longestStreak")?.toInt() ?: 0

            val newStreak = when {
                lastLogDate == today -> currentStreak
                lastLogDate == getPreviousDay(today) -> currentStreak + 1
                else -> 1
            }

            val newLongestStreak = maxOf(newStreak, longestStreak)

            streakRef.set(mapOf(
                "lastLogDate" to today,
                "currentStreak" to newStreak,
                "longestStreak" to newLongestStreak
            ), com.google.firebase.firestore.SetOptions.merge()).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTodayLogsForGroup(
        groupId: String
    ): Flow<List<Log>> = callbackFlow {
        val today = getTodayString()
        val listener = firestore
            .collection("logs")
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("date", today)
            .orderBy("createdAt",
                Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val logs = snapshot?.documents
                    ?.mapNotNull {
                        it.toObject(Log::class.java)
                    } ?: emptyList()
                trySend(logs)
            }
        awaitClose { listener.remove() }
    }

    private fun getPreviousDay(dateString: String):
            String {
        val sdf = SimpleDateFormat(
            "yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateString)
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_MONTH, -1)
        return sdf.format(cal.time)
    }
}

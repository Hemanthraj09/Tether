package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NudgeRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    private fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    suspend fun sendNudge(groupId: String, nudgedUid: String): Result<Unit> {
        return try {
            val today = getTodayString()
            val nudgeKey = "${currentUid}_${nudgedUid}"

            // Check 24hr cooldown
            val existing = firestore
                .collection("nudges")
                .document(groupId)
                .collection(today)
                .document(nudgeKey)
                .get().await()

            if (existing.exists()) {
                return Result.failure(Exception("Already nudged today"))
            }

            // Get nudger name
            val nudgerDoc = firestore.collection("users")
                .document(currentUid).get().await()
            val nudgerName = nudgerDoc.getString("name") ?: "Someone"

            // Write nudge document — nudged user's device will pick this up
            firestore.collection("nudges")
                .document(groupId)
                .collection(today)
                .document(nudgeKey)
                .set(mapOf(
                    "nudgerUid" to currentUid,
                    "nudgerName" to nudgerName,
                    "nudgedUid" to nudgedUid,
                    "groupId" to groupId,
                    "timestamp" to System.currentTimeMillis()
                )).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasNudgedToday(groupId: String, nudgedUid: String): Boolean {
        return try {
            val today = getTodayString()
            val nudgeKey = "${currentUid}_${nudgedUid}"
            val doc = firestore.collection("nudges")
                .document(groupId)
                .collection(today)
                .document(nudgeKey)
                .get().await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }
}

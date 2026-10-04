package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.UserCache
import com.tether.app.utils.DateKeys
import kotlinx.coroutines.tasks.await

/**
 * Nudges live at groupStats/{groupId}/nudges/{date}_{nudgerUid}_{nudgedUid}.
 *
 * - Covered by the existing groupStats security rule (no console changes).
 * - The deterministic id makes "one nudge per person per day" automatic.
 * - Recipients listen per group (see RealtimeWatcher), which needs no
 *   composite index and no collection-group rule.
 */
class NudgeRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun sendNudge(groupId: String, nudgedUid: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception("Not signed in"))
            val today = DateKeys.today()
            val nudgerName = UserCache.currentUserName()

            nudgesCollection(groupId)
                .document("${today}_${uid}_$nudgedUid")
                .set(mapOf(
                    "nudgerUid" to uid,
                    "nudgerName" to nudgerName,
                    "nudgedUid" to nudgedUid,
                    "groupId" to groupId,
                    "date" to today,
                    "timestamp" to System.currentTimeMillis()
                )).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun nudgesCollection(groupId: String) =
        firestore.collection("groupStats").document(groupId).collection("nudges")
}

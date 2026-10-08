package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Group
import kotlinx.coroutines.tasks.await

class GroupManagementRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    /** Deletes the group and its invite code (creator only, enforced by rules). */
    suspend fun deleteGroup(
        groupId: String
    ): Result<Unit> {
        return try {
            val groupRef = firestore.collection("groups").document(groupId)
            val inviteCode = groupRef.get().await().getString("inviteCode").orEmpty()
            firestore.batch().apply {
                delete(groupRef)
                if (inviteCode.isNotEmpty()) delete(firestore.collection("inviteCodes").document(inviteCode))
            }.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(
        groupId: String
    ): Result<Unit> {
        return try {
            firestore.collection("groups").document(groupId)
                .update("members", FieldValue.arrayRemove(currentUid))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The creator leaves and passes the group to the next member
     * (used when deleting an account, so the group isn't orphaned).
     */
    suspend fun handOverAndLeave(group: Group): Result<Unit> {
        return try {
            val remaining = group.members.filter { it != currentUid }
            val newOwner = remaining.firstOrNull() ?: return deleteGroup(group.id)
            firestore.collection("groups").document(group.id)
                .update(mapOf("members" to remaining, "createdBy" to newOwner))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Creator chooses what a Coding group's leaderboard ranks ("hours" or "solves"). */
    suspend fun setMetric(groupId: String, metric: String): Result<Unit> {
        return try {
            firestore.collection("groups").document(groupId)
                .update("metric", metric)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Photo proof on logs: off / optional / required. Creator only (security rules). */
    suspend fun setProofMode(groupId: String, mode: String): Result<Unit> = runCatching {
        firestore.collection("groups").document(groupId).update("proof", mode).await()
        Unit
    }
}

package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class GroupManagementRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    /**
     * Deletes the group and removes it from the creator's own group list.
     *
     * Other members' user docs can't be edited by this user (security rules),
     * which previously made deletion fail half-way. Their apps now drop the
     * deleted group's id automatically (GroupRepository.observeUserGroups).
     */
    suspend fun deleteGroup(
        groupId: String
    ): Result<Unit> {
        return try {
            firestore.batch().apply {
                delete(firestore.collection("groups").document(groupId))
                update(firestore.collection("users").document(currentUid),
                    "groupIds", FieldValue.arrayRemove(groupId))
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
            firestore.batch().apply {
                update(firestore.collection("groups").document(groupId),
                    "members", FieldValue.arrayRemove(currentUid))
                update(firestore.collection("users").document(currentUid),
                    "groupIds", FieldValue.arrayRemove(groupId))
            }.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Group
import kotlinx.coroutines.tasks.await

class GroupManagementRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun deleteGroup(
        groupId: String
    ): Result<Unit> {
        return try {
            val groupDoc = firestore
                .collection("groups")
                .document(groupId)
                .get()
                .await()

            val group = groupDoc
                .toObject(Group::class.java)
                ?: return Result.failure(
                    Exception("Group not found"))

            group.members.forEach { uid ->
                firestore.collection("users")
                    .document(uid)
                    .update("groupIds",
                        com.google.firebase.firestore
                            .FieldValue
                            .arrayRemove(groupId))
                    .await()
            }

            firestore.collection("groups")
                .document(groupId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(
        groupId: String
    ): Result<Unit> {
        return try {
            firestore.collection("groups")
                .document(groupId)
                .update("members",
                    com.google.firebase.firestore
                        .FieldValue
                        .arrayRemove(currentUid))
                .await()

            firestore.collection("users")
                .document(currentUid)
                .update("groupIds",
                    com.google.firebase.firestore
                        .FieldValue
                        .arrayRemove(groupId))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isGroupCreator(
        groupId: String
    ): Boolean {
        return try {
            val groupDoc = firestore
                .collection("groups")
                .document(groupId)
                .get()
                .await()
            val createdBy = groupDoc
                .getString("createdBy") ?: ""
            createdBy == currentUid
        } catch (e: Exception) {
            false
        }
    }
}

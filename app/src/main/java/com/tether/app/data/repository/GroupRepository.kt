package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Group
import com.tether.app.data.model.Log
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun createGroup(
        name: String,
        goalType: String,
        isSolo: Boolean
    ): Result<Group> {
        return try {
            val inviteCode = if (isSolo) ""
                else generateInviteCode()

            val groupRef = firestore
                .collection("groups")
                .document()

            val group = Group(
                id = groupRef.id,
                name = name,
                goalType = goalType,
                members = listOf(currentUid),
                inviteCode = inviteCode,
                createdBy = currentUid,
                isSolo = isSolo,
                createdAt = System.currentTimeMillis()
            )

            groupRef.set(group).await()

            firestore.collection("users")
                .document(currentUid)
                .update("groupIds",
                    com.google.firebase.firestore
                        .FieldValue.arrayUnion(groupRef.id))
                .await()

            writeSystemLog(groupRef.id,
                if (isSolo) "Started a solo journey! 🚀"
                else "Created the group! 🚀")

            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroup(
        inviteCode: String
    ): Result<Group> {
        return try {
            val querySnapshot = firestore
                .collection("groups")
                .whereEqualTo("inviteCode",
                    inviteCode.uppercase().trim())
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.failure(
                    Exception("Invalid invite code. " +
                        "Please check and try again."))
            }

            val groupDoc = querySnapshot.documents[0]
            val group = groupDoc.toObject(Group::class.java)
                ?: return Result.failure(
                    Exception("Group not found."))

            if (currentUid in group.members) {
                return Result.failure(
                    Exception("You are already " +
                        "in this group."))
            }

            firestore.collection("groups")
                .document(group.id)
                .update("members",
                    com.google.firebase.firestore
                        .FieldValue.arrayUnion(currentUid))
                .await()

            firestore.collection("users")
                .document(currentUid)
                .update("groupIds",
                    com.google.firebase.firestore
                        .FieldValue.arrayUnion(group.id))
                .await()

            writeSystemLog(group.id, "Joined the group! 👋")

            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserGroups(): Result<List<Group>> {
        return try {
            val userDoc = firestore
                .collection("users")
                .document(currentUid)
                .get()
                .await()

            @Suppress("UNCHECKED_CAST")
            val groupIds = userDoc
                .get("groupIds") as? List<String>
                ?: emptyList()

            if (groupIds.isEmpty()) {
                return Result.success(emptyList())
            }

            val groups = groupIds.map { gid ->
                firestore.collection("groups")
                    .document(gid)
                    .get()
                    .await()
                    .toObject(Group::class.java)
                    ?: Group()
            }.filter { it.id.isNotEmpty() }

            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }

    private suspend fun writeSystemLog(
        groupId: String,
        note: String
    ) {
        try {
            val userDoc = firestore
                .collection("users")
                .document(currentUid)
                .get()
                .await()

            val name = userDoc.getString("name") ?: "New User"
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2).joinToString("").uppercase()

            val avatarColors = listOf(
                "#3B82F6", "#22C55E", "#A855F7",
                "#EC4899", "#EAB308", "#EF4444",
                "#F97316", "#06B6D4"
            )
            val avatarColor = avatarColors[
                currentUid.hashCode().and(0x7fffffff)
                    .rem(avatarColors.size)]

            val logRef = firestore
                .collection("logs")
                .document()

            val sdf = SimpleDateFormat(
                "yyyy-MM-dd", Locale.getDefault())
            val today = sdf.format(Date())

            val log = Log(
                id = logRef.id,
                userId = currentUid,
                groupId = groupId,
                userName = name,
                userInitials = initials,
                avatarColorHex = avatarColor,
                date = today,
                value = 0.0,
                note = note,
                createdAt = System.currentTimeMillis()
            )
            logRef.set(log).await()
        } catch (e: Exception) {
            // silent fail for system logs
        }
    }
}

package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tether.app.data.UserCache
import com.tether.app.data.model.Group
import com.tether.app.data.model.Log
import com.tether.app.data.snapshotFlow
import com.tether.app.domain.InviteCodes
import com.tether.app.utils.DateKeys
import com.tether.app.utils.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

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
            val uid = currentUid
            val inviteCode = if (isSolo) "" else generateUniqueInviteCode()
            val groupRef = firestore.collection("groups").document()

            val group = Group(
                id = groupRef.id,
                name = name,
                goalType = goalType,
                members = listOf(uid),
                inviteCode = inviteCode,
                createdBy = uid,
                isSolo = isSolo,
                createdAt = System.currentTimeMillis()
            )

            // Group + membership written atomically.
            firestore.batch().apply {
                set(groupRef, group)
                set(firestore.collection("users").document(uid),
                    mapOf("groupIds" to FieldValue.arrayUnion(groupRef.id)),
                    SetOptions.merge())
            }.commit().await()

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
            val uid = currentUid
            // Accept codes typed with spaces/dashes (the hint shows "X7Y9-Z2").
            val code = InviteCodes.normalize(inviteCode)
            if (code.isEmpty()) {
                return Result.failure(Exception("Please enter an invite code"))
            }

            val querySnapshot = firestore
                .collection("groups")
                .whereEqualTo("inviteCode", code)
                .limit(1)
                .get()
                .await()

            val groupRef = querySnapshot.documents.firstOrNull()?.reference
                ?: return Result.failure(
                    Exception("Invalid invite code. Please check and try again."))

            // Transaction: the 6-member cap can't be exceeded by two people
            // joining at the same moment.
            var errorMessage: String? = null
            val joined = firestore.runTransaction { tx ->
                val group = tx.get(groupRef).toObject(Group::class.java)
                when {
                    group == null -> {
                        errorMessage = "Group not found."
                        null
                    }
                    uid in group.members -> {
                        errorMessage = "You are already in this group."
                        null
                    }
                    group.members.size >= MAX_MEMBERS -> {
                        errorMessage = "This group is full. Maximum 6 members allowed."
                        null
                    }
                    else -> {
                        tx.update(groupRef, "members", FieldValue.arrayUnion(uid))
                        tx.set(firestore.collection("users").document(uid),
                            mapOf("groupIds" to FieldValue.arrayUnion(groupRef.id)),
                            SetOptions.merge())
                        group
                    }
                }
            }.await()

            if (joined == null) {
                return Result.failure(Exception(errorMessage ?: "Failed to join group"))
            }

            writeSystemLog(joined.id, "Joined the group! 👋")
            Result.success(joined)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Live list of the signed-in user's groups, in the order they joined.
     * Emits cached data first, so the home screen shows up instantly.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUserGroups(): Flow<List<Group>> {
        val uid = currentUid
        if (uid.isEmpty()) return flowOf(emptyList())

        val userRef = firestore.collection("users").document(uid)
        return userRef.snapshotFlow()
            .map { snapshot ->
                @Suppress("UNCHECKED_CAST")
                (snapshot?.get("groupIds") as? List<String>)?.distinct() ?: emptyList()
            }
            .distinctUntilChanged()
            .flatMapLatest { groupIds ->
                if (groupIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val chunkFlows = groupIds.chunked(10).map { chunk ->
                        firestore.collection("groups")
                            .whereIn(FieldPath.documentId(), chunk)
                            .snapshotFlow()
                            .map { snapshot ->
                                if (snapshot != null && !snapshot.metadata.isFromCache) {
                                    removeDeletedGroupIds(chunk, snapshot.documents.map { it.id })
                                }
                                snapshot?.documents
                                    ?.mapNotNull { it.toObject(Group::class.java) }
                                    ?: emptyList()
                            }
                    }
                    combine(chunkFlows) { chunks ->
                        val byId = chunks.flatMap { it }.associateBy { it.id }
                        groupIds.mapNotNull { byId[it] }
                    }
                }
            }
            .distinctUntilChanged()
    }

    /**
     * When a group is deleted, other members still have its id in their
     * groupIds (security rules only let a user edit their own doc), so each
     * user removes dangling ids from their own document.
     */
    private fun removeDeletedGroupIds(requested: List<String>, found: List<String>) {
        val missing = requested - found.toSet()
        if (missing.isEmpty()) return
        firestore.collection("users").document(currentUid)
            .update("groupIds", FieldValue.arrayRemove(*missing.toTypedArray()))
    }

    private suspend fun generateUniqueInviteCode(): String {
        repeat(5) {
            val code = InviteCodes.generate()
            val taken = try {
                !firestore.collection("groups")
                    .whereEqualTo("inviteCode", code)
                    .limit(1)
                    .get().await()
                    .isEmpty
            } catch (e: Exception) {
                false
            }
            if (!taken) return code
        }
        return InviteCodes.generate()
    }

    private suspend fun writeSystemLog(
        groupId: String,
        note: String
    ) {
        try {
            val uid = currentUid
            val name = UserCache.currentUserName()
            val logRef = firestore.collection("logs").document()

            val log = Log(
                id = logRef.id,
                userId = uid,
                groupId = groupId,
                userName = name,
                userInitials = Formatters.initials(name),
                avatarColorHex = Formatters.avatarColor(uid),
                date = DateKeys.today(),
                value = 0.0,
                note = note,
                createdAt = System.currentTimeMillis()
            )
            logRef.set(log).await()
        } catch (e: Exception) {
            // silent fail for system logs
        }
    }

    companion object {
        const val MAX_MEMBERS = 6
    }
}

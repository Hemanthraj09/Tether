package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tether.app.data.UserCache
import com.tether.app.data.model.Group
import com.tether.app.data.model.Log
import com.tether.app.data.snapshotFlow
import com.tether.app.domain.InviteCodes
import com.tether.app.utils.DateKeys
import com.tether.app.utils.Formatters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Groups and invite codes.
 *
 * Security model (see firestore.rules): groups can't be listed or searched.
 * You see a group because you're in its members list, or because you fetched
 * inviteCodes/{code} with a code someone gave you.
 */
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

            // Group and its invite code are written atomically.
            firestore.batch().apply {
                set(groupRef, group)
                if (inviteCode.isNotEmpty()) {
                    set(inviteCodeRef(inviteCode), mapOf("groupId" to groupRef.id, "createdBy" to uid))
                }
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

            // The only way to find a group: fetch the one code you were given.
            val codeDoc = inviteCodeRef(code).get().await()
            val groupId = codeDoc.getString("groupId")
                ?: return Result.failure(
                    Exception("Invalid invite code. Please check and try again."))
            val groupRef = firestore.collection("groups").document(groupId)

            // Transaction: the 6-member cap can't be exceeded by two people
            // joining at the same moment (also enforced by the security rules).
            var errorMessage: String? = null
            val joined = firestore.runTransaction { tx ->
                val group = tx.get(groupRef).toObject(Group::class.java)
                when {
                    group == null -> {
                        errorMessage = "This group no longer exists."
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
     * Live list of the signed-in user's groups (oldest first). One query on the
     * members array; emits cached data first, so the home screen shows up instantly.
     */
    fun observeUserGroups(): Flow<List<Group>> {
        val uid = currentUid
        if (uid.isEmpty()) return flowOf(emptyList())
        return firestore.collection("groups")
            .whereArrayContains("members", uid)
            .snapshotFlow()
            .map { snapshot ->
                snapshot?.documents
                    ?.mapNotNull { it.toObject(Group::class.java) }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
            }
            .distinctUntilChanged()
    }

    /** One-shot version of [observeUserGroups] (migrations, account deletion). */
    suspend fun currentGroups(): List<Group> {
        val uid = currentUid
        if (uid.isEmpty()) return emptyList()
        return firestore.collection("groups")
            .whereArrayContains("members", uid)
            .get().await()
            .documents.mapNotNull { it.toObject(Group::class.java) }
    }

    /**
     * Groups created before v1.3 have no inviteCodes/{code} document yet. Any
     * member may create it (the rules check it matches the group), so codes
     * keep working after the upgrade.
     */
    suspend fun backfillInviteCodes(groups: List<Group>) {
        for (group in groups) {
            if (group.inviteCode.isEmpty()) continue
            val ref = inviteCodeRef(group.inviteCode)
            val exists = runCatching { ref.get().await().exists() }.getOrDefault(true)
            if (!exists) {
                runCatching {
                    ref.set(mapOf("groupId" to group.id, "createdBy" to group.createdBy)).await()
                }
            }
        }
    }

    private fun inviteCodeRef(code: String) =
        firestore.collection("inviteCodes").document(code)

    private suspend fun generateUniqueInviteCode(): String {
        repeat(5) {
            val code = InviteCodes.generate()
            val taken = runCatching { inviteCodeRef(code).get().await().exists() }.getOrDefault(false)
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
                createdAt = System.currentTimeMillis(),
                source = "system"
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

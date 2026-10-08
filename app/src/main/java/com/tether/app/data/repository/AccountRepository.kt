package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Permanent account deletion (a Play Store requirement).
 *
 * Order matters: groups first (hand over or delete, so nothing is orphaned),
 * then personal data, then the Firebase Auth account. Firebase only deletes
 * an account after a recent sign-in, so that's checked *before* touching any data.
 */
class AccountRepository {

    class RecentLoginRequired : Exception(
        "For your security, log out and log back in, then delete your account within 5 minutes.")

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
        if (System.currentTimeMillis() - lastSignIn > RECENT_LOGIN_MS) throw RecentLoginRequired()
        val uid = user.uid

        // 1. Groups: first remove your photos and reactions (only members can),
        //    then creators hand over to the next member (or delete if alone);
        //    members simply leave.
        val management = GroupManagementRepository()
        for (group in GroupRepository().currentGroups()) {
            ProofRepository().deleteAllMine(group.id)
            TodayRepository().deleteMyReactions(group.id)
            val result = if (group.createdBy == uid) management.handOverAndLeave(group)
                         else management.leaveGroup(group.id)
            result.getOrThrow()
        }

        // 2. Personal data: logs, completions (+ index), heatmap, profile.
        firestore.collection("logs").whereEqualTo("userId", uid).get().await()
            .documents.chunked(400).forEach { chunk ->
                firestore.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
            }
        CompletionRepository().deleteAllMine()
        val userRef = firestore.collection("users").document(uid)
        userRef.collection("heatmap").get().await().documents.forEach { it.reference.delete().await() }
        userRef.delete().await()

        // 3. The login itself.
        user.delete().await()
        Unit
    }

    companion object {
        private const val RECENT_LOGIN_MS = 5 * 60_000L
    }
}

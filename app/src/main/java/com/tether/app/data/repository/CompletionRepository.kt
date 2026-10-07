package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.UserCache
import com.tether.app.data.snapshotFlow
import com.tether.app.data.tracks.TrackItem
import com.tether.app.domain.Completion
import com.tether.app.domain.CompletionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Completed track items: users/{uid}/completions/{key}.
 * Verified entries come from LeetCode sync; manual ticks are marked "self".
 */
class CompletionRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("completions")

    fun observe(uid: String): Flow<Map<String, Completion>> =
        collection(uid).snapshotFlow().map { snapshot ->
            snapshot?.documents?.mapNotNull { it.toCompletion() }?.associateBy { it.key } ?: emptyMap()
        }

    /** Completed keys for several users at once (group races). */
    fun observeKeys(uids: List<String>): Flow<Map<String, Set<String>>> {
        if (uids.isEmpty()) return flowOf(emptyMap())
        val flows = uids.map { uid -> observe(uid).map { uid to it.keys } }
        return combine(flows) { pairs -> pairs.toMap() }
    }

    suspend fun markDone(item: TrackItem) {
        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "key" to item.key,
            "title" to item.title,
            "source" to CompletionSource.SELF.id,
            "completedAt" to now,
            "syncedAt" to now,
            "userName" to UserCache.currentUserName()
        )
        item.slug?.let { data["slug"] = it }
        collection(uid).document(item.key).set(data).await()
    }

    /** Only manual ticks can be undone; verified completions stay. */
    suspend fun unmark(key: String) {
        val uid = auth.currentUser?.uid ?: return
        collection(uid).document(key).delete().await()
    }

    companion object {
        fun DocumentSnapshot.toCompletion(): Completion? {
            val source = CompletionSource.from(getString("source")) ?: return null
            return Completion(
                key = getString("key") ?: id,
                title = getString("title") ?: "",
                slug = getString("slug"),
                source = source,
                completedAt = getLong("completedAt") ?: 0L
            )
        }
    }
}

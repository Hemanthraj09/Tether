package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.tether.app.data.snapshotFlow
import com.tether.app.domain.Completion
import com.tether.app.domain.CompletionEntry
import com.tether.app.domain.CompletionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Verified LeetCode solves (and older manual ticks).
 *
 *  - users/{uid}/completions/{key}: one document per item (history, activity feed)
 *  - users/{uid}/meta/completionIndex: { items: { key: { s, t } } }, a one-document
 *    index of the same data, read by leaderboards and the profile heatmap
 *    (1 read per member instead of hundreds, which keeps the free Spark quota safe).
 */
class CompletionRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("completions")

    private fun indexRef(uid: String) =
        firestore.collection("users").document(uid).collection("meta").document(INDEX_DOC)

    /** key → (source, completedAt) for one user, from the index document. */
    fun observe(uid: String): Flow<Map<String, CompletionEntry>> =
        indexRef(uid).snapshotFlow().map { parseIndex(it) }

    /** Indexes for several users at once (group members). */
    fun observeMany(uids: List<String>): Flow<Map<String, Map<String, CompletionEntry>>> {
        if (uids.isEmpty()) return flowOf(emptyMap())
        val flows = uids.map { uid -> observe(uid).map { uid to it } }
        return combine(flows) { pairs -> pairs.toMap() }
    }

    /** Adds index entries inside an existing batch (used by LeetCode sync). */
    fun addToIndex(batch: WriteBatch, uid: String, key: String, source: CompletionSource, completedAt: Long) {
        batch.set(indexRef(uid),
            mapOf("items" to mapOf(key to mapOf("s" to source.id, "t" to completedAt))),
            SetOptions.merge())
    }

    /**
     * Merges every per-item document into the index (pre-v1.3 data). Merge, not
     * overwrite, so it's safe even if a sync already started filling the index.
     */
    suspend fun ensureIndex() {
        val uid = auth.currentUser?.uid ?: return
        val items = collection(uid).get().await().documents
            .mapNotNull { it.toCompletion() }
            .associate { c -> c.key to mapOf("s" to c.source.id, "t" to c.completedAt) }
        if (items.isEmpty()) return
        indexRef(uid).set(mapOf("items" to items), SetOptions.merge()).await()
    }

    /** Deletes all of the user's completions and the index (account deletion). */
    suspend fun deleteAllMine() {
        val uid = auth.currentUser?.uid ?: return
        collection(uid).get().await().documents.chunked(400).forEach { chunk ->
            firestore.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
        }
        indexRef(uid).delete().await()
    }

    companion object {
        const val INDEX_DOC = "completionIndex"

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

        fun parseIndex(snapshot: DocumentSnapshot?): Map<String, CompletionEntry> {
            val items = snapshot?.get("items") as? Map<*, *> ?: return emptyMap()
            return items.mapNotNull { (key, value) ->
                val entry = value as? Map<*, *> ?: return@mapNotNull null
                val source = CompletionSource.from(entry["s"] as? String) ?: return@mapNotNull null
                val time = (entry["t"] as? Number)?.toLong() ?: 0L
                (key as? String)?.let { it to CompletionEntry(source, time) }
            }.toMap()
        }
    }
}

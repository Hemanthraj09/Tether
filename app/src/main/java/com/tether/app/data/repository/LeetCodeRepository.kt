package com.tether.app.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.UserCache
import com.tether.app.data.leetcode.LeetCodeApi
import com.tether.app.data.leetcode.LeetCodeConfig
import com.tether.app.data.leetcode.LeetCodeException
import com.tether.app.data.leetcode.LeetCodeProfile
import com.tether.app.data.leetcode.LeetCodeSyncState
import com.tether.app.data.repository.CompletionRepository.Companion.toCompletion
import com.tether.app.data.snapshotFlow
import com.tether.app.domain.LeetCodeUsernames
import com.tether.app.domain.SolveMerger
import com.tether.app.domain.SyncBackoff
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * LeetCode as a connected account: link a username, read live public stats,
 * and sync recent solves into completions.
 *
 * Uniqueness: leetcodeUsernames/{lowercase} = { uid, username } claims a handle,
 * so two Tether accounts can't connect the same LeetCode profile.
 */
class LeetCodeRepository(context: Context) {

    sealed class SyncOutcome {
        object NotLinked : SyncOutcome()
        object Disabled : SyncOutcome()
        data class BackedOff(val until: Long) : SyncOutcome()
        data class Synced(val newCompletions: Int) : SyncOutcome()
        data class Failed(val message: String) : SyncOutcome()
    }

    private val appContext = context.applicationContext
    private val api = LeetCodeApi(appContext)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val uid: String? get() = auth.currentUser?.uid

    fun observeUsername(userId: String? = uid): Flow<String?> {
        if (userId == null) return flowOf(null)
        return firestore.collection("users").document(userId).snapshotFlow()
            .map { it?.getString(FIELD_USERNAME)?.takeIf { name -> name.isNotBlank() } }
            .distinctUntilChanged()
    }

    suspend fun profile(username: String, forceRefresh: Boolean = false): Result<LeetCodeProfile> =
        runCatching { api.profile(username, forceRefresh) }

    /**
     * Live solved totals for several members (uid → total), fetched in parallel straight
     * from LeetCode. Members whose fetch fails are simply left out.
     */
    suspend fun totals(handles: List<Pair<String, String>>): Map<String, Int> = coroutineScope {
        handles.map { (memberUid, username) ->
            async { runCatching { memberUid to api.profile(username).solvedTotal }.getOrNull() }
        }.awaitAll().filterNotNull().toMap()
    }

    /** Validates the handle against LeetCode, claims it, and saves it on the user. */
    suspend fun link(input: String): Result<String> = runCatching {
        val me = uid ?: error("Not signed in")
        val requested = LeetCodeUsernames.normalize(input)
            ?: throw IllegalArgumentException("That doesn't look like a LeetCode username")

        val canonical = api.profile(requested, forceRefresh = true).username
        val userRef = firestore.collection("users").document(me)
        val current = userRef.get().await().getString(FIELD_USERNAME)
        if (current.equals(canonical, ignoreCase = true)) return@runCatching canonical

        val claimRef = claimRef(canonical)
        val claim = claimRef.get().await()
        if (claim.exists() && claim.getString("uid") != me) {
            throw IllegalStateException("@$canonical is already connected to another Tether account")
        }

        firestore.batch().apply {
            if (!claim.exists()) set(claimRef, mapOf("uid" to me, "username" to canonical))
            update(userRef, FIELD_USERNAME, canonical)
            if (!current.isNullOrBlank()) delete(claimRef(current))
        }.commit().await()

        LeetCodeSyncState.reset(appContext)
        canonical
    }

    suspend fun unlink(): Result<Unit> = runCatching {
        val me = uid ?: error("Not signed in")
        val userRef = firestore.collection("users").document(me)
        val current = userRef.get().await().getString(FIELD_USERNAME) ?: return@runCatching
        firestore.batch().apply {
            update(userRef, FIELD_USERNAME, FieldValue.delete())
            delete(claimRef(current))
        }.commit().await()
        LeetCodeSyncState.reset(appContext)
    }

    /**
     * Pulls recent accepted solves and records new ones as verified completions.
     * Respects the remote kill switch and exponential backoff ([force] skips backoff
     * for a manual "Sync now").
     */
    suspend fun sync(force: Boolean = false): SyncOutcome {
        val me = uid ?: return SyncOutcome.NotLinked
        if (!LeetCodeConfig.syncEnabled) return SyncOutcome.Disabled

        val now = System.currentTimeMillis()
        val state = LeetCodeSyncState.current(appContext)
        if (!force && !SyncBackoff.isAllowed(now, state.nextAllowedAt)) {
            return SyncOutcome.BackedOff(state.nextAllowedAt)
        }

        return try {
            val username = firestore.collection("users").document(me).get().await()
                .getString(FIELD_USERNAME) ?: return SyncOutcome.NotLinked

            val recent = api.recentSolves(username)
            val completions = firestore.collection("users").document(me).collection("completions")

            val slugs = recent.map { it.slug }.distinct()
            val existing = if (slugs.isEmpty()) emptyMap() else
                slugs.chunked(30).flatMap { chunk ->
                    completions.whereIn(FieldPath.documentId(), chunk).get().await().documents
                }.mapNotNull { it.toCompletion() }.associateBy { it.key }

            val writes = SolveMerger.merge(existing, recent)
            if (writes.isNotEmpty()) {
                val userName = UserCache.currentUserName()
                firestore.batch().apply {
                    writes.forEach { c ->
                        val data = hashMapOf<String, Any>(
                            "key" to c.key,
                            "title" to c.title,
                            "source" to c.source.id,
                            "completedAt" to c.completedAt,
                            "syncedAt" to now,
                            "userName" to userName
                        )
                        c.slug?.let { data["slug"] = it }
                        set(completions.document(c.key), data)
                    }
                }.commit().await()
            }

            LeetCodeSyncState.recordSuccess(appContext, now)
            SyncOutcome.Synced(writes.size)
        } catch (e: LeetCodeException) {
            LeetCodeSyncState.recordFailure(appContext, now, e.message ?: "LeetCode error")
            SyncOutcome.Failed(e.message ?: "LeetCode error")
        } catch (e: IOException) {
            LeetCodeSyncState.recordFailure(appContext, now, "Network error")
            SyncOutcome.Failed("Network error")
        }
    }

    private fun claimRef(username: String) =
        firestore.collection("leetcodeUsernames").document(username.lowercase())

    companion object {
        const val FIELD_USERNAME = "leetcodeUsername"
    }
}

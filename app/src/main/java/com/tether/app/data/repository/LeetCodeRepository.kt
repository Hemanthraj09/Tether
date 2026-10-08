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
import com.tether.app.domain.CompletionEntry
import com.tether.app.domain.CompletionSource
import com.tether.app.domain.LeetCodeUsernames
import com.tether.app.domain.LeetCodeVerification
import com.tether.app.domain.SolveMerger
import com.tether.app.domain.StreakCalculator
import com.tether.app.utils.DateKeys
import com.google.firebase.firestore.SetOptions
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
 * LeetCode as a connected account: connect a username, read live public stats,
 * prove ownership, and sync recent solves into completions.
 *
 * Trust model without a server: ownership is proven by a code in the public
 * LeetCode bio, and claimed solves are cross-checked against LeetCode's public
 * data by whoever is viewing them (see [LeetCodeVerification]).
 */
class LeetCodeRepository(context: Context) {

    sealed class SyncOutcome {
        object NotLinked : SyncOutcome()
        object Disabled : SyncOutcome()
        data class BackedOff(val until: Long) : SyncOutcome()
        data class Synced(val newCompletions: Int) : SyncOutcome()
        data class Failed(val message: String) : SyncOutcome()
    }

    /** What a viewer can confirm about a member's LeetCode data. */
    data class MemberCheck(
        val solvedTotal: Int,
        val ownershipVerified: Boolean,
        /** Claimed verified completions that LeetCode's public data contradicts. */
        val unconfirmedKeys: Set<String>
    )

    private val appContext = context.applicationContext
    private val api = LeetCodeApi(appContext)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val completions = CompletionRepository()

    private val uid: String? get() = auth.currentUser?.uid

    fun observeUsername(userId: String? = uid): Flow<String?> {
        if (userId == null) return flowOf(null)
        return firestore.collection("users").document(userId).snapshotFlow()
            .map { it?.getString(FIELD_USERNAME)?.takeIf { name -> name.isNotBlank() } }
            .distinctUntilChanged()
    }

    suspend fun profile(username: String, forceRefresh: Boolean = false): Result<LeetCodeProfile> =
        runCatching { api.profile(username, forceRefresh) }

    /** The code this user puts in their LeetCode bio to prove the handle is theirs. */
    fun myOwnershipCode(): String? = uid?.let { LeetCodeVerification.ownershipCode(it) }

    /** Checks the live bio for this user's code. */
    suspend fun checkOwnership(username: String): Result<Boolean> = runCatching {
        val me = uid ?: error("Not signed in")
        LeetCodeVerification.ownsAccount(api.profile(username, forceRefresh = true).aboutMe, me)
    }

    /**
     * Viewer-side checks for group members (uid → handle, uid → their index):
     * live totals, ownership, and claims LeetCode contradicts. Fetched in
     * parallel from LeetCode, cached 10 min; failures leave the member out.
     */
    suspend fun checkMembers(
        handles: Map<String, String>,
        indexes: Map<String, Map<String, CompletionEntry>>
    ): Map<String, MemberCheck> = coroutineScope {
        handles.map { (memberUid, username) ->
            async {
                runCatching {
                    val profile = api.profile(username)
                    val recent = api.recentSolves(username)
                    val claims = indexes[memberUid].orEmpty()
                        .filterValues { it.source == CompletionSource.LEETCODE }
                        .mapValues { it.value.completedAt }
                    memberUid to MemberCheck(
                        solvedTotal = profile.solvedTotal,
                        ownershipVerified = LeetCodeVerification.ownsAccount(profile.aboutMe, memberUid),
                        unconfirmedKeys = LeetCodeVerification.unconfirmedClaims(claims, recent, profile.solvedTotal)
                    )
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().toMap()
    }

    /** Validates the handle against LeetCode and saves it on the user. */
    suspend fun link(input: String): Result<String> = runCatching {
        val me = uid ?: error("Not signed in")
        val requested = LeetCodeUsernames.normalize(input)
            ?: throw IllegalArgumentException("That doesn't look like a LeetCode username")
        val canonical = api.profile(requested, forceRefresh = true).username
        firestore.collection("users").document(me).update(FIELD_USERNAME, canonical).await()
        LeetCodeSyncState.reset(appContext)
        canonical
    }

    suspend fun unlink(): Result<Unit> = runCatching {
        val me = uid ?: error("Not signed in")
        firestore.collection("users").document(me).update(FIELD_USERNAME, FieldValue.delete()).await()
        LeetCodeSyncState.reset(appContext)
    }

    /**
     * Pulls recent accepted solves and records new ones as verified completions
     * (per-item docs + the index, in one batch). Respects the remote kill switch
     * and exponential backoff ([force] skips backoff for a manual "Sync now").
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

            val recent = api.recentSolves(username, forceRefresh = true)
            val completionDocs = firestore.collection("users").document(me).collection("completions")

            val slugs = recent.map { it.slug }.distinct()
            val existing = if (slugs.isEmpty()) emptyMap() else
                slugs.chunked(30).flatMap { chunk ->
                    completionDocs.whereIn(FieldPath.documentId(), chunk).get().await().documents
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
                        set(completionDocs.document(c.key), data)
                        completions.addToIndex(this, me, c.key, c.source, c.completedAt)
                    }
                }.commit().await()
            }

            // A verified solve today counts as showing up: keep the streak alive in
            // every Coding group, with no hours to log.
            if (writes.any { it.completedAt >= DateKeys.startOfTodayMillis() }) {
                runCatching { creditStreaks(me) }
                    .onFailure { android.util.Log.w("LeetCodeSync", "streak credit failed", it) }
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

    /** Same streak rules as logging hours (StreakCalculator; validated by the security rules). */
    private suspend fun creditStreaks(me: String) {
        val today = DateKeys.today()
        val codingGroups = GroupRepository().currentGroups()
            .filter { CodingStatsRepository.isCodingGoal(it.goalType) }
        if (codingGroups.isEmpty()) return
        val batch = firestore.batch()
        for (group in codingGroups) {
            val ref = firestore.collection("groupStats").document(group.id).collection("streaks").document(me)
            val doc = runCatching { ref.get().await() }.getOrNull()
            val previous = doc?.takeIf { it.exists() }?.let {
                StreakCalculator.Streak(
                    current = it.getLong("currentStreak")?.toInt() ?: 0,
                    longest = it.getLong("longestStreak")?.toInt() ?: 0,
                    lastLogDate = it.getString("lastLogDate") ?: ""
                )
            }
            if (previous?.lastLogDate == today) continue // already counted today
            val next = StreakCalculator.afterLog(previous, today)
            batch.set(ref, mapOf(
                "lastLogDate" to next.lastLogDate,
                "currentStreak" to next.current,
                "longestStreak" to next.longest
            ), SetOptions.merge())
        }
        batch.commit().await()
    }

    companion object {
        const val FIELD_USERNAME = "leetcodeUsername"
    }
}

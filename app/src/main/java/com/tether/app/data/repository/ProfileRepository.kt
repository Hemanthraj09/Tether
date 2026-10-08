package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.snapshotFlow
import com.tether.app.domain.Interests
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** What the user said matters: focus areas, weekly targets (hours) and stage. */
data class FocusPlan(
    /** null = never asked; empty = skipped. */
    val interests: List<String>?,
    val targets: Map<String, Int>,
    val stage: String?
)

/** Focus areas ("interests"), weekly targets and stage on the user's public profile. */
class ProfileRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    fun observePlan(): Flow<FocusPlan> {
        val me = uid ?: return flowOf(FocusPlan(null, emptyMap(), null))
        return firestore.collection("users").document(me).snapshotFlow()
            .map { doc -> doc.toPlan() }
            .distinctUntilChanged()
    }

    /** null = never asked; empty list = skipped. */
    fun observeInterests(): Flow<List<String>?> = observePlan().map { it.interests }.distinctUntilChanged()

    suspend fun loadPlan(): FocusPlan {
        val me = uid ?: return FocusPlan(null, emptyMap(), null)
        return runCatching { firestore.collection("users").document(me).get().await().toPlan() }
            .getOrDefault(FocusPlan(null, emptyMap(), null))
    }

    suspend fun loadInterests(): Pair<List<String>?, String?> = loadPlan().let { it.interests to it.stage }

    suspend fun saveInterests(
        interests: List<String>,
        stage: String?,
        targets: Map<String, Int> = emptyMap()
    ): Result<Unit> = runCatching {
        val me = uid ?: error("Not signed in")
        firestore.collection("users").document(me).update(
            mapOf(
                "interests" to interests,
                "targets" to Interests.sanitizeTargets(interests, targets),
                "stage" to (stage ?: FieldValue.delete())
            )
        ).await()
        Unit
    }

    private fun DocumentSnapshot?.toPlan(): FocusPlan {
        val interests = (this?.get("interests") as? List<*>)?.filterIsInstance<String>()
        val targets = (this?.get("targets") as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            (k as? String)?.let { key -> (v as? Number)?.toInt()?.let { key to it } }
        }.toMap()
        return FocusPlan(interests, targets, this?.getString("stage"))
    }
}

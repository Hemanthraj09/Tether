package com.tether.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * In-memory cache of the signed-in user's display name.
 * Avoids a Firestore read on every log / group action.
 */
object UserCache {

    private var cachedUid: String? = null
    private var cachedName: String? = null

    suspend fun currentUserName(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: return "Unknown"
        if (cachedUid == user.uid) cachedName?.let { return it }

        val name = try {
            FirebaseFirestore.getInstance()
                .collection("users").document(user.uid)
                .get().await()
                .getString("name")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@")
        ?: "Unknown"

        cachedUid = user.uid
        cachedName = name
        return name
    }

    fun clear() {
        cachedUid = null
        cachedName = null
    }
}

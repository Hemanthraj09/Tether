package com.tether.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tether.app.data.model.User
import com.tether.app.data.repository.CompletionRepository
import com.tether.app.data.repository.GroupRepository
import com.tether.app.utils.DateKeys
import kotlinx.coroutines.tasks.await

/**
 * One-time, client-side data upgrades (there is no server to run them).
 * Each step is idempotent and merge-based, so it's safe to retry and safe to
 * race with a sync or a new log. Runs once per user per device; on failure it
 * simply retries on the next launch.
 */
object Migrations {

    private const val PREFS = "tether_migrations"
    private const val CURRENT = "v1.3.0"

    suspend fun runIfNeeded(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "${CURRENT}_${user.uid}"
        if (prefs.getBoolean(key, false)) return

        try {
            val firestore = FirebaseFirestore.getInstance()
            val userRef = firestore.collection("users").document(user.uid)

            // 1. Public profile: make sure it exists and drop private fields
            //    (email, group list) that older versions stored there.
            val profile = userRef.get().await()
            if (!profile.exists()) {
                val name = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@") ?: "User"
                userRef.set(User(uid = user.uid, name = name)).await()
            } else if (profile.contains("email") || profile.contains("groupIds")) {
                userRef.update(mapOf("email" to FieldValue.delete(), "groupIds" to FieldValue.delete())).await()
            }

            // 2. Invite codes now live in inviteCodes/{code}; create missing ones.
            val groups = GroupRepository()
            groups.backfillInviteCodes(groups.currentGroups())

            // 3. One-document completion index (members read 1 doc each).
            CompletionRepository().ensureIndex()

            // 4. This year's heatmap document, rebuilt from the user's own logs.
            val year = DateKeys.today().substring(0, 4)
            val totals = HashMap<String, Double>()
            firestore.collection("logs").whereEqualTo("userId", user.uid).get().await()
                .documents.forEach { doc ->
                    val date = doc.getString("date") ?: return@forEach
                    if (!date.startsWith(year)) return@forEach
                    totals[date] = (totals[date] ?: 0.0) + (doc.getDouble("value") ?: 0.0)
                }
            if (totals.isNotEmpty()) {
                userRef.collection("heatmap").document(year).set(totals, SetOptions.merge()).await()
            }

            prefs.edit().putBoolean(key, true).apply()
        } catch (e: Exception) {
            android.util.Log.w("Migrations", "Migration incomplete, will retry next launch", e)
        }
    }
}

package com.tether.app.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.utils.DateKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Proof photos, stored free on the Spark plan: a ~60 KB JPEG in a Firestore
 * document (groupStats/{g}/proofs/{logId}) instead of Cloud Storage (which
 * needs the Blaze plan).
 *
 * - Separate from the log, so feed listeners never download image bytes.
 * - Fetched one at a time, only when a log with a photo is on screen.
 * - Same-day only: the security rules stop serving a photo 24 h after it was
 *   taken, the app shows today's only, and each user's app deletes their own
 *   older photos.
 */
class ProofRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val uid: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    /** Decoded photo, or null if it's gone (expired, deleted, offline). */
    suspend fun load(groupId: String, logId: String): Bitmap? {
        cache.get(logId)?.let { return it }
        val bytes = runCatching {
            proofsRef(groupId).document(logId).get().await().getBlob("image")?.toBytes()
        }.getOrNull() ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: return null
        cache.put(logId, bitmap)
        return bitmap
    }

    /** Deletes your photos from earlier days in this group (cheap: usually finds none). */
    suspend fun deleteMyOldProofs(groupId: String) {
        val me = uid.ifEmpty { return }
        val today = DateKeys.today()
        runCatching {
            proofsRef(groupId).whereEqualTo("uid", me).get().await().documents
                .filter { it.getString("date") != today }
                .forEach { it.reference.delete().await() }
        }
    }

    /** Account deletion: remove all your photos in a group (call while still a member). */
    suspend fun deleteAllMine(groupId: String) {
        val me = uid.ifEmpty { return }
        proofsRef(groupId).whereEqualTo("uid", me).get().await()
            .documents.forEach { it.reference.delete().await() }
    }

    private fun proofsRef(groupId: String) =
        firestore.collection("groupStats").document(groupId).collection("proofs")

    companion object {
        /** Decoded photos (~2–3 MB each at 720 px), shared across screens. */
        private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        /** On logout, so the next account never sees the previous one's photos. */
        fun clearCache() = cache.evictAll()
    }
}

package com.tether.app.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/*
 * Snapshot listeners as Flows.
 *
 * Firestore listeners deliver the locally cached data first and then the
 * server data, so screens render instantly instead of waiting for a network
 * round trip. Errors (e.g. a missing permission) emit null instead of closing
 * the flow, so one failing source can never block a whole screen.
 */

fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            android.util.Log.w("TetherFirestore", "Listener failed for $path", error)
            trySend(null)
        } else {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

fun Query.snapshotFlow(): Flow<QuerySnapshot?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            android.util.Log.w("TetherFirestore", "Query listener failed", error)
            trySend(null)
        } else {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/** Reads a numeric field regardless of whether it was stored as Long or Double. */
fun DocumentSnapshot.number(field: String): Double =
    (get(field) as? Number)?.toDouble() ?: 0.0

/** All numeric fields of a stats document (uid → hours). */
fun DocumentSnapshot?.hoursByUid(): Map<String, Double> =
    this?.data
        ?.mapNotNull { (key, value) -> (value as? Number)?.let { key to it.toDouble() } }
        ?.toMap()
        ?: emptyMap()

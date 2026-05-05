package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.User
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    suspend fun login(
        email: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth
                .signInWithEmailAndPassword(email, password)
                .await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signup(
        name: String,
        email: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth
                .createUserWithEmailAndPassword(email, password)
                .await()
            val user = result.user!!
            
            val profileUpdates = com.google.firebase.auth
                .UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            user.updateProfile(profileUpdates).await()

            createUserDocument(user.uid, name, email)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createUserDocument(
        uid: String,
        name: String,
        email: String
    ) {
        val user = User(
            uid = uid,
            name = name,
            email = email
        )
        firestore
            .collection("users")
            .document(uid)
            .set(user)
            .await()
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun googleSignIn(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount): Result<Unit> {
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw Exception("Sign-in failed")

            // Create user doc if new user
            val userDoc = firestore.collection("users").document(user.uid).get().await()
            if (!userDoc.exists()) {
                val name = account.displayName ?: account.email?.substringBefore("@") ?: "User"
                firestore.collection("users").document(user.uid).set(
                    mapOf(
                        "uid" to user.uid,
                        "name" to name,
                        "email" to (account.email ?: ""),
                        "groupIds" to emptyList<String>(),
                        "totalHours" to 0.0
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

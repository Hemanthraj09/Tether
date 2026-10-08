package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.UserCache
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

            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name).build()
            ).await()

            createUserDocument(user.uid, name)
            // Not blocking: the account works right away, the email confirms ownership.
            runCatching { user.sendEmailVerification().await() }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The public profile only holds what other members need (name, totals).
     * The email stays in Firebase Auth; group membership lives on the groups.
     */
    private suspend fun createUserDocument(uid: String, name: String) {
        firestore.collection("users").document(uid)
            .set(User(uid = uid, name = name))
            .await()
    }

    fun logout() {
        auth.signOut()
        UserCache.clear()
        ProofRepository.clearCache()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resendEmailVerification(): Result<Unit> = runCatching {
        auth.currentUser?.sendEmailVerification()?.await()
        Unit
    }

    suspend fun googleSignIn(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw Exception("Sign-in failed")

            val userDoc = firestore.collection("users").document(user.uid).get().await()
            if (!userDoc.exists()) {
                val name = account.displayName ?: account.email?.substringBefore("@") ?: "User"
                createUserDocument(user.uid, name)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.jbr.shortsforge.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirebaseAuth"

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = runCatching {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Sign in successful: ${result.user?.email}")
            result.user!!
        } catch (e: FirebaseAuthException) {
            Log.e(TAG, "Sign in failed — code=${e.errorCode} msg=${e.message}")
            throw Exception(friendlyMessage(e.errorCode))
        }
    }

    suspend fun register(email: String, password: String): Result<FirebaseUser> = runCatching {
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Register successful: ${result.user?.email}")
            result.user!!
        } catch (e: FirebaseAuthException) {
            Log.e(TAG, "Register failed — code=${e.errorCode} msg=${e.message}")
            throw Exception(friendlyMessage(e.errorCode))
        }
    }

    fun signOut() = auth.signOut()

    private fun friendlyMessage(errorCode: String) = when (errorCode) {
        "ERROR_INVALID_EMAIL"            -> "Invalid email address."
        "ERROR_WRONG_PASSWORD"           -> "Incorrect password."
        "ERROR_USER_NOT_FOUND"           -> "No account found with this email."
        "ERROR_EMAIL_ALREADY_IN_USE"     -> "This email is already registered. Try signing in."
        "ERROR_WEAK_PASSWORD"            -> "Password must be at least 6 characters."
        "ERROR_TOO_MANY_REQUESTS"        -> "Too many attempts. Please try again later."
        "ERROR_NETWORK_REQUEST_FAILED"   -> "No internet connection."
        else                             -> "Authentication failed ($errorCode)."
    }
}

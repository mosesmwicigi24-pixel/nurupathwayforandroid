// Firebase Email/Password authentication — an "add-alongside" layer next to the
// existing custom-backend JWT auth (it does NOT replace it). Every entry point is
// guarded by isConfigured(): until google-services.json is present and the
// google-services plugin is applied, FirebaseApp has no default instance, so these
// calls short-circuit instead of crashing. Once configured, they use the real
// FirebaseAuth (project 777897756817). See FIREBASE_SETUP.md.
package org.nuruplace.member.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thin wrapper over FirebaseAuth for Email/Password sign-in, register + sign-out. */
object FirebaseAuthService {

    /** True only when a default FirebaseApp exists (google-services.json wired in). */
    fun isConfigured(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private val auth: FirebaseAuth? get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    val currentUser: FirebaseUser? get() = auth?.currentUser

    /** Create an Email/Password account. Throws on failure (weak password, taken, …). */
    suspend fun register(email: String, password: String): FirebaseUser {
        val a = auth ?: error("Firebase is not configured yet.")
        return await(a.createUserWithEmailAndPassword(email.trim(), password))
    }

    /** Sign in with Email/Password. Throws on bad credentials. */
    suspend fun signIn(email: String, password: String): FirebaseUser {
        val a = auth ?: error("Firebase is not configured yet.")
        return await(a.signInWithEmailAndPassword(email.trim(), password))
    }

    fun signOut() { auth?.signOut() }

    /** Bridge a Firebase AuthResult Task to a coroutine. */
    private suspend fun await(task: com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult>): FirebaseUser =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { res ->
                val u = res.user
                if (u != null) cont.resume(u) else cont.resumeWithException(IllegalStateException("No user returned"))
            }.addOnFailureListener { cont.resumeWithException(it) }
        }
}

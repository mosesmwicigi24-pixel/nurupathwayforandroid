// Firestore data access — the "add-alongside" store for the specific new,
// Firebase-owned collections (NOT the curriculum/pathway/giving data, which stays
// on the custom backend). Generic helpers keyed by the signed-in Firebase user;
// callers pass a collection + a plain Map and get back Maps. Model concrete
// collections on top of this once we decide what lives in Firestore.
//
// Guarded like FirebaseAuthService: no-ops (empty/false) until Firebase is
// configured. See FIREBASE_SETUP.md.
package org.nuruplace.member.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirestoreRepo {

    private val db: FirebaseFirestore? get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    /** The current Firebase uid, or null when signed out / unconfigured. */
    private val uid: String? get() = FirebaseAuthService.currentUser?.uid

    /** Upsert a document under `<collection>/<docId>`. Returns false if unconfigured. */
    suspend fun set(collection: String, docId: String, data: Map<String, Any?>): Boolean {
        val d = db ?: return false
        return runCatching {
            await(d.collection(collection).document(docId).set(data))
            true
        }.getOrDefault(false)
    }

    /** Read a single document, or null. */
    suspend fun get(collection: String, docId: String): Map<String, Any?>? {
        val d = db ?: return null
        return runCatching { awaitDoc(d.collection(collection).document(docId).get()) }.getOrNull()
    }

    /** List documents in a collection scoped to the signed-in user (`owner_uid == uid`). */
    suspend fun listMine(collection: String, ownerField: String = "owner_uid"): List<Map<String, Any?>> {
        val d = db ?: return emptyList()
        val u = uid ?: return emptyList()
        return runCatching {
            awaitQuery(d.collection(collection).whereEqualTo(ownerField, u).get())
        }.getOrDefault(emptyList())
    }

    // --- Task → coroutine bridges ---
    private suspend fun await(task: com.google.android.gms.tasks.Task<Void>) =
        suspendCancellableCoroutine<Unit> { c ->
            task.addOnSuccessListener { c.resume(Unit) }.addOnFailureListener { c.resumeWithException(it) }
        }

    private suspend fun awaitDoc(task: com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot>): Map<String, Any?>? =
        suspendCancellableCoroutine { c ->
            task.addOnSuccessListener { snap -> c.resume(if (snap.exists()) snap.data else null) }
                .addOnFailureListener { c.resumeWithException(it) }
        }

    private suspend fun awaitQuery(task: com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>): List<Map<String, Any?>> =
        suspendCancellableCoroutine { c ->
            task.addOnSuccessListener { qs -> c.resume(qs.documents.mapNotNull { it.data }) }
                .addOnFailureListener { c.resumeWithException(it) }
        }
}

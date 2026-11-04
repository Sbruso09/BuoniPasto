package com.mario.buonipasto.data

import com.google.firebase.auth.FirebaseAuth
import com.mario.buonipasto.R
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Errore di accesso: il testo arriva dalle risorse, nella lingua dell'app. */
class LoginException(val res: Int, val arg: String? = null) : Exception()

/** The latest items from the server, plus the reason if the last update failed. */
data class Feed<T>(val items: List<T>, val error: String? = null)

class Repository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun members(code: String) =
        db.collection("groups").document(code).collection("members")

    private fun movements(code: String) =
        db.collection("groups").document(code).collection("movements")

    /** Joins the group as a new person, or enters as the person who already has this name. */
    suspend fun login(rawCode: String, rawName: String, voucherCents: Long?): Session {
        val code = normalizeGroupCode(rawCode)
        val name = rawName.trim()
        val memberId = normalizeMemberId(name)
        if (code.length < 6) throw LoginException(R.string.login_err_codice)
        if (memberId.isEmpty()) throw LoginException(R.string.login_err_nome)

        if (auth.currentUser == null) auth.signInAnonymously().await()

        val existing = members(code).get(Source.SERVER).await().documents
        val mine = existing.firstOrNull { it.id == memberId }
        if (mine != null) {
            if (voucherCents != null) {
                members(code).document(memberId).update("voucherCents", voucherCents).await()
            }
            return Session(code, memberId, mine.getString("name") ?: name)
        }

        if (existing.size >= 2) {
            val names = existing.joinToString(" · ") { it.getString("name") ?: it.id }
            throw LoginException(R.string.login_err_completo, names)
        }
        if (voucherCents == null || voucherCents <= 0) {
            throw LoginException(R.string.login_err_buono)
        }

        members(code).document(memberId)
            .set(mapOf("name" to name, "voucherCents" to voucherCents))
            .await()
        return Session(code, memberId, name)
    }

    fun observeMembers(code: String): Flow<Feed<Member>> = callbackFlow {
        val registration = members(code).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Feed(emptyList(), error.localizedMessage ?: ""))
            } else if (snapshot != null) {
                trySend(Feed(snapshot.documents.mapNotNull { toMember(it) }.sortedBy { it.id }))
            }
        }
        awaitClose { registration.remove() }
    }

    fun observeMovements(code: String): Flow<Feed<Movement>> = callbackFlow {
        val registration = movements(code).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Feed(emptyList(), error.localizedMessage ?: ""))
            } else if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { toMovement(it) }
                    .sortedWith(compareByDescending<Movement> { it.dateMillis }.thenByDescending { it.createdAt })
                trySend(Feed(items))
            }
        }
        awaitClose { registration.remove() }
    }

    // Writes are not awaited: Firestore applies them locally at once and sends them when online.
    fun add(code: String, movement: Movement, onError: (String) -> Unit) {
        movements(code).document()
            .set(
                mapOf(
                    "type" to movement.type.name,
                    "payerId" to movement.payerId,
                    "payerName" to movement.payerName,
                    "receiverName" to movement.receiverName,
                    "vouchers" to movement.vouchers,
                    "voucherCents" to movement.voucherCents,
                    "extraCents" to movement.extraCents,
                    "split" to movement.split.name,
                    "note" to movement.note,
                    "dateMillis" to movement.dateMillis,
                    "createdAt" to movement.createdAt,
                    "createdBy" to movement.createdBy,
                ),
            )
            .addOnFailureListener { onError(it.localizedMessage ?: "Salvataggio non riuscito") }
    }

    fun delete(code: String, movementId: String, onError: (String) -> Unit) {
        movements(code).document(movementId).delete()
            .addOnFailureListener { onError(it.localizedMessage ?: "Eliminazione non riuscita") }
    }

    fun updateVoucher(code: String, memberId: String, voucherCents: Long, onError: (String) -> Unit) {
        members(code).document(memberId).update("voucherCents", voucherCents)
            .addOnFailureListener { onError(it.localizedMessage ?: "Salvataggio non riuscito") }
    }

    private fun toMember(doc: DocumentSnapshot): Member? {
        val name = doc.getString("name") ?: return null
        return Member(doc.id, name, doc.getLong("voucherCents") ?: 0L)
    }

    private fun toMovement(doc: DocumentSnapshot): Movement? {
        val payerId = doc.getString("payerId") ?: return null
        return Movement(
            id = doc.id,
            type = runCatching { MovementType.valueOf(doc.getString("type").orEmpty()) }
                .getOrDefault(MovementType.EXPENSE),
            payerId = payerId,
            payerName = doc.getString("payerName").orEmpty(),
            receiverName = doc.getString("receiverName").orEmpty(),
            vouchers = (doc.getLong("vouchers") ?: 0L).toInt(),
            voucherCents = doc.getLong("voucherCents") ?: 0L,
            extraCents = doc.getLong("extraCents") ?: 0L,
            split = runCatching { Split.valueOf(doc.getString("split").orEmpty()) }
                .getOrDefault(Split.HALF),
            note = doc.getString("note").orEmpty(),
            dateMillis = doc.getLong("dateMillis") ?: 0L,
            createdAt = doc.getLong("createdAt") ?: 0L,
            createdBy = doc.getString("createdBy").orEmpty(),
        )
    }
}

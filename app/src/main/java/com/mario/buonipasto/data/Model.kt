package com.mario.buonipasto.data

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

enum class Split { HALF, PAYER_ONLY, OTHER_ONLY }

enum class MovementType { EXPENSE, SETTLEMENT }

data class Member(
    val id: String,
    val name: String,
    val voucherCents: Long,
)

data class Movement(
    val id: String,
    val type: MovementType,
    val payerId: String,
    val payerName: String,
    val receiverName: String,
    val vouchers: Int,
    val voucherCents: Long,
    val extraCents: Long,
    val split: Split,
    val note: String,
    val dateMillis: Long,
    val createdAt: Long,
    val createdBy: String,
) {
    val totalCents: Long get() = vouchers * voucherCents + extraCents
}

data class Session(
    val groupCode: String,
    val memberId: String,
    val name: String,
)

/**
 * What the other person owes [memberId], in cents. Negative means [memberId] owes the other person.
 * The sum runs in half-cents so that splitting an odd amount never loses a cent along the way.
 */
fun netCents(movements: List<Movement>, memberId: String): Long {
    var halfCents = 0L
    for (movement in movements) {
        val credit = when {
            movement.type == MovementType.SETTLEMENT -> movement.totalCents * 2
            movement.split == Split.HALF -> movement.totalCents
            movement.split == Split.OTHER_ONLY -> movement.totalCents * 2
            else -> 0L
        }
        halfCents += if (movement.payerId == memberId) credit else -credit
    }
    return halfCents / 2
}

/** Accepts "12,50", "12.50" and "12". Returns null for anything else or for negative amounts. */
fun parseCents(input: String): Long? {
    val amount = input.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (amount < BigDecimal.ZERO) return null
    return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

fun formatEuro(cents: Long): String {
    val value = abs(cents)
    val sign = if (cents < 0) "-" else ""
    return "$sign${value / 100},${(value % 100).toString().padStart(2, '0')} €"
}

/** Input text for an editable amount field: 800 becomes "8,00". */
fun centsToInput(cents: Long): String =
    "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

fun normalizeMemberId(name: String): String =
    name.trim().lowercase()
        .replace(Regex("\\s+"), "-")
        .filter { it.isLetterOrDigit() || it == '-' }

fun normalizeGroupCode(code: String): String =
    code.trim().uppercase().filter { it.isLetterOrDigit() }

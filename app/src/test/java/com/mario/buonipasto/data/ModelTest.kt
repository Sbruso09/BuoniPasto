package com.mario.buonipasto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelTest {
    private fun expense(payer: String, vouchers: Int, value: Long, extra: Long = 0, split: Split = Split.HALF) =
        Movement("", MovementType.EXPENSE, payer, payer, "", vouchers, value, extra, split, "", 0, 0, payer)

    private fun settlement(payer: String, amount: Long) =
        Movement("", MovementType.SETTLEMENT, payer, payer, "", 0, 0, amount, Split.OTHER_ONLY, "", 0, 0, payer)

    @Test
    fun halfExpenseCreditsPayerWithHalf() {
        val movements = listOf(expense("a", 3, 800))
        assertEquals(1200L, netCents(movements, "a"))
        assertEquals(-1200L, netCents(movements, "b"))
    }

    @Test
    fun differentVoucherValuesOffsetEachOther() {
        val movements = listOf(expense("a", 3, 800), expense("b", 4, 700))
        assertEquals(-200L, netCents(movements, "a"))
        assertEquals(200L, netCents(movements, "b"))
    }

    @Test
    fun extraPaymentCountsTowardsTotal() {
        assertEquals(650L, netCents(listOf(expense("a", 1, 800, extra = 500)), "a"))
    }

    @Test
    fun personalExpenseCreatesNoDebt() {
        assertEquals(0L, netCents(listOf(expense("a", 2, 800, split = Split.PAYER_ONLY)), "a"))
    }

    @Test
    fun expenseForOtherCreditsFullAmount() {
        assertEquals(1600L, netCents(listOf(expense("a", 2, 800, split = Split.OTHER_ONLY)), "a"))
    }

    @Test
    fun settlementBringsBalanceToZero() {
        val movements = listOf(expense("a", 3, 800), settlement("b", 1200))
        assertEquals(0L, netCents(movements, "a"))
        assertEquals(0L, netCents(movements, "b"))
    }

    @Test
    fun twoOddHalvesDoNotLoseACent() {
        val movements = listOf(expense("a", 1, 755), expense("a", 1, 755))
        assertEquals(755L, netCents(movements, "a"))
    }

    @Test
    fun parsesItalianAndDotAmounts() {
        assertEquals(1250L, parseCents("12,50"))
        assertEquals(1250L, parseCents("12.50"))
        assertEquals(800L, parseCents(" 8 "))
        assertNull(parseCents("abc"))
        assertNull(parseCents("-1"))
    }

    @Test
    fun formatsEuro() {
        assertEquals("12,05 €", formatEuro(1205))
        assertEquals("0,00 €", formatEuro(0))
        assertEquals("-3,40 €", formatEuro(-340))
    }

    @Test
    fun normalizesIdentifiers() {
        assertEquals("mario-rossi", normalizeMemberId("  Mario  Rossi "))
        assertEquals("AB12CD", normalizeGroupCode(" ab-12 cd "))
    }
}

package com.iancompany.voiceledger

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthLedgerTest {
    private val month = YearMonth.of(2026, 11)
    @Test fun sumsUseLongAndCategoryTotalsMatch() {
        val entries = (1..4).map { Expense("id$it", month.atDay(it), ExpenseCategory.FUEL, 999_999_999) } +
            Expense("parking", month.atDay(7), ExpenseCategory.PARKING, 3000)
        val ledger = MonthLedger(month, entries)
        assertEquals(4_000_002_996L, ledger.totalWon)
        assertEquals(3_999_999_996L, ledger.byCategory[ExpenseCategory.FUEL])
        assertEquals(3000L, ledger.byCategory[ExpenseCategory.PARKING])
    }
    @Test fun emptyMonthIsZero() { assertEquals(0L, MonthLedger(month, emptyList()).totalWon) }
    @Test(expected = IllegalArgumentException::class) fun anotherMonthCannotContaminateTotal() {
        MonthLedger(month, listOf(Expense("other", LocalDate.of(2026, 12, 1), ExpenseCategory.FUEL, 1)))
    }
    @Test(expected = IllegalArgumentException::class) fun zeroCannotBecomeAStoredExpense() {
        Expense("zero", month.atDay(1), ExpenseCategory.OTHER, 0)
    }
}

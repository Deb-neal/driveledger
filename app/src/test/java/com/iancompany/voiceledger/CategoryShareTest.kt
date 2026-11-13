package com.iancompany.voiceledger

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CategoryShareTest {
    private val date = LocalDate.of(2026, 11, 7)
    private fun entry(id: Int, category: ExpenseCategory, amount: Long) = Expense(id.toString(), date, category, amount)
    private fun ledger(entries: List<Expense>) = MonthLedger(YearMonth.from(date), entries)

    @Test fun emptyMonthHasNoInventedSlices() {
        assertTrue(ledger(emptyList()).categoryShares().isEmpty())
    }

    @Test fun chartUsesTheEntireMonthNotOnlyTwentyRecentRows() {
        val entries = (0..24).map { entry(it, ExpenseCategory.FUEL, 1000) } + entry(25, ExpenseCategory.PARKING, 5000)
        val shares = ledger(entries).categoryShares()
        assertEquals(listOf(ExpenseCategory.FUEL, ExpenseCategory.PARKING), shares.map { it.category })
        assertEquals(listOf(25000L, 5000L), shares.map { it.amountWon })
        assertEquals(5.0 / 6.0, shares.first().fraction, 1e-12)
        assertEquals(1.0, shares.sumOf { it.fraction }, 1e-12)
    }

    @Test fun eightMaximumAmountsRemainPositiveAndHaveStableTieOrder() {
        val shares = ledger(ExpenseCategory.entries.mapIndexed { index, cat -> entry(index, cat, Expense.MAX_AMOUNT_WON) }).categoryShares()
        assertEquals(ExpenseCategory.entries.toList(), shares.map { it.category })
        assertEquals(7999999992L, shares.sumOf { it.amountWon })
        shares.forEach { assertEquals(.125, it.fraction, 1e-12) }
    }

    @Test fun oneCategoryFillsTheRingAndTinyCostsAreNotInflated() {
        assertEquals(1.0, ledger(listOf(entry(0, ExpenseCategory.TAX, 1))).categoryShares().single().fraction, 0.0)
        val shares = ledger(listOf(entry(0, ExpenseCategory.FUEL, Expense.MAX_AMOUNT_WON), entry(1, ExpenseCategory.PARKING, 1))).categoryShares()
        assertEquals(1e-9, shares.last().fraction, 1e-15)
    }
}

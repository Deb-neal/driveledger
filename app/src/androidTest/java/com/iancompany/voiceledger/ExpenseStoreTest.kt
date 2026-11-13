package com.iancompany.voiceledger

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class ExpenseStoreTest {
    @Test fun persistenceDuplicateProtectionEditsAndMonthBoundaries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ledger-test-${UUID.randomUUID()}.db"
        val november = YearMonth.of(2026, 11)
        val entry = Expense("one", november.atDay(7), ExpenseCategory.PARKING, 3000)
        try {
            ExpenseStore(context, name).use { db ->
                db.insert(entry)
                db.insert(entry) // retry of the same confirmation is idempotent
                assertEquals(1, db.month(november).entries.size)
                try { db.insert(entry.copy(amountWon = 7000)); fail("same token changed amount") }
                catch (_: IllegalStateException) { }
                db.insert(Expense("october", LocalDate.of(2026, 10, 31), ExpenseCategory.FUEL, 70_000))
                db.insert(Expense("nov30", LocalDate.of(2026, 11, 30), ExpenseCategory.FUEL, 20_000))
                db.insert(Expense("december", LocalDate.of(2026, 12, 1), ExpenseCategory.FUEL, 80_000))
                assertEquals(23_000L, db.month(november).totalWon)
                db.update(entry.copy(amountWon = 5000, category = ExpenseCategory.TOLL))
                assertEquals(25_000L, db.month(november).totalWon)
                try { db.update(entry.copy(amountWon = 9000)); fail("stale edit") }
                catch (_: IllegalStateException) { }
                try { db.delete(entry.id, 0); fail("stale deletion") }
                catch (_: IllegalStateException) { }
            }
            ExpenseStore(context, name).use { reopened ->
                val saved = reopened.month(november).entries.single { it.id == entry.id }
                assertEquals(5000L, saved.amountWon)
                assertEquals(ExpenseCategory.TOLL, saved.category)
                assertEquals(1, saved.revision)
                reopened.delete(saved.id, saved.revision)
                assertEquals(20_000L, reopened.month(november).totalWon)
                assertEquals(70_000L, reopened.month(november.minusMonths(1)).totalWon)
                assertEquals(80_000L, reopened.month(november.plusMonths(1)).totalWon)
            }
        } finally { context.deleteDatabase(name) }
    }
}

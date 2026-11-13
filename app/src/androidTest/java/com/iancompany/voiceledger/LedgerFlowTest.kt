package com.iancompany.voiceledger

import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.view.inspector.WindowInspector
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class LedgerFlowTest {
    @SdkSuppress(minSdkVersion = 29)
    @Test fun confirmCorrectSaveOnceRestoreDraftAndReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val month = YearMonth.now()
        val initial = ExpenseStore(context).use { it.month(month) }
        var created: Expense? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await(scenario) { it.findViewById<TextView>(R.id.total) != null && it.findViewById<Button>(R.id.record_expense).isEnabled }
                scenario.onActivity { activity ->
                    activity.findViewById<Button>(R.id.record_expense).performClick()
                    activity.findViewById<EditText>(R.id.sentence).setText("오늘 주차비 삼천 원")
                    activity.findViewById<Button>(R.id.parse_sentence).performClick()
                    assertEquals("3000", activity.findViewById<EditText>(R.id.amount).text.toString())
                    assertEquals(ExpenseCategory.PARKING.ordinal + 1, activity.findViewById<Spinner>(R.id.category).selectedItemPosition)
                    activity.findViewById<EditText>(R.id.amount).setText("0")
                    activity.findViewById<Button>(R.id.save_expense).performClick()
                    assertNotNull(activity.findViewById<EditText>(R.id.amount).error)
                    assertTrue(activity.findViewById<EditText>(R.id.amount).isActivated)
                    activity.findViewById<EditText>(R.id.amount).setText("4500")
                    assertFalse(activity.findViewById<EditText>(R.id.amount).isActivated)
                    assertNull(activity.findViewById<EditText>(R.id.amount).error)
                }
                assertEquals(initial.totalWon, ExpenseStore(context).use { it.month(month).totalWon })
                scenario.recreate()
                await(scenario) { it.findViewById<Button>(R.id.save_expense)?.isEnabled == true }
                scenario.onActivity { activity ->
                    assertEquals("4500", activity.findViewById<EditText>(R.id.amount).text.toString())
                    assertEquals(ExpenseCategory.PARKING.ordinal + 1, activity.findViewById<Spinner>(R.id.category).selectedItemPosition)
                    val save = activity.findViewById<Button>(R.id.save_expense)
                    save.performClick()
                    assertFalse("Disable repeat submissions while saving", save.isEnabled)
                    assertEquals(activity.getString(R.string.saving), save.text.toString())
                    save.performClick()
                }
                await(scenario) { it.findViewById<TextView>(R.id.total) != null && it.findViewById<Button>(R.id.record_expense).isEnabled }
                val after = ExpenseStore(context).use { it.month(month) }
                assertEquals(initial.entries.size + 1, after.entries.size)
                assertEquals(initial.totalWon + 4500, after.totalWon)
                created = after.entries.single { entry -> initial.entries.none { it.id == entry.id } }
                scenario.recreate()
                await(scenario) { it.findViewById<TextView>(R.id.total) != null && it.findViewById<Button>(R.id.record_expense).isEnabled }
                scenario.onActivity { activity ->
                    activity.findViewById<android.view.View>(R.id.entries).findViewWithTag<android.view.View>(created!!.id).performClick()
                    activity.findViewById<EditText>(R.id.amount).setText("5500")
                    activity.findViewById<Button>(R.id.save_expense).performClick()
                }
                await(scenario) { it.findViewById<TextView>(R.id.total) != null && it.findViewById<Button>(R.id.record_expense).isEnabled }
                assertEquals(initial.totalWon + 5500, ExpenseStore(context).use { it.month(month).totalWon })
                // Leaving a new, unconfirmed draft must never write an expense.
                scenario.onActivity { activity ->
                    activity.findViewById<Button>(R.id.record_expense).performClick()
                    activity.findViewById<EditText>(R.id.amount).setText("9900")
                    activity.findViewById<Button>(R.id.cancel_expense).performClick()
                }
                clickDialogPositive()
                await(scenario) { it.findViewById<TextView>(R.id.total) != null && it.findViewById<Button>(R.id.record_expense).isEnabled }
                assertEquals(initial.entries.size + 1, ExpenseStore(context).use { it.month(month).entries.size })
                scenario.onActivity { activity ->
                    activity.findViewById<android.view.View>(R.id.entries).findViewWithTag<android.view.View>(created!!.id).performClick()
                    activity.findViewById<Button>(R.id.delete_expense).performClick()
                }
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val cancel = WindowInspector.getGlobalWindowViews()
                        .mapNotNull { it.findViewById<Button>(android.R.id.button2) }.single()
                    cancel.performClick()
                }
                assertEquals("Cancelling deletion must preserve the expense", initial.totalWon + 5500,
                    ExpenseStore(context).use { it.month(month).totalWon })
                scenario.onActivity { it.findViewById<Button>(R.id.delete_expense).performClick() }
                clickDialogPositive()
                await(scenario) { it.findViewById<TextView>(R.id.total) != null && it.findViewById<Button>(R.id.record_expense).isEnabled }
            }
            ExpenseStore(context).use { reopened ->
                assertEquals(initial.entries.size, reopened.month(month).entries.size)
                assertEquals(initial.totalWon, reopened.month(month).totalWon)
            }
        } finally {
            ExpenseStore(context).use { db ->
                created?.let { entry -> db.month(month).entries.find { it.id == entry.id }?.let { db.delete(it.id, it.revision) } }
            }
        }
    }

    private fun clickDialogPositive() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val button = WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<Button>(android.R.id.button1) }.single()
            button.performClick()
        }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        var ready = false
        while (!ready && System.currentTimeMillis() < deadline) {
            scenario.onActivity { ready = predicate(it) }
            if (!ready) Thread.sleep(50)
        }
        assertTrue("UI did not reach the expected state", ready)
    }
}

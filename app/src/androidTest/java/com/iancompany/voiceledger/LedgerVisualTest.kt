package com.iancompany.voiceledger

import android.graphics.Bitmap
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Opt-in render fixture; demo expenses are deleted in finally and are never app defaults. */
class LedgerVisualTest {
    @Test fun captureSampleLedgerAndConfirmation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("captureLedger") == "true")
        val suffix = args.getString("captureName", "light")
        val context = instrumentation.targetContext
        val entries = if (args.getString("stressLedger") == "true") ExpenseCategory.entries.map {
            Expense(UUID.randomUUID().toString(), LocalDate.now(), it, Expense.MAX_AMOUNT_WON)
        } else listOf(
            Expense(UUID.randomUUID().toString(), LocalDate.now(), ExpenseCategory.FUEL, 70000),
            Expense(UUID.randomUUID().toString(), LocalDate.now(), ExpenseCategory.PARKING, 3000),
            Expense(UUID.randomUUID().toString(), LocalDate.now(), ExpenseCategory.MAINTENANCE, 120000),
        )
        try {
            ExpenseStore(context).use { db -> entries.forEach { db.insert(it) } }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var ready = false
                val deadline = System.currentTimeMillis() + 10_000
                while (!ready && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { ready = it.findViewById<TextView>(R.id.total) != null }
                    if (!ready) Thread.sleep(50)
                }
                assertTrue(ready)
                scenario.onActivity { activity ->
                    val config = activity.resources.configuration
                    println("Render configuration: fontScale=${config.fontScale}, widthDp=${config.screenWidthDp}, night=${config.uiMode}")
                    args.getString("expectedFontScale")?.toFloat()?.let {
                        assertEquals("Capture must use the requested font scale", it, config.fontScale, .01f)
                    }
                    args.getString("maxWidthDp")?.toInt()?.let {
                        assertTrue("Capture must exercise the compact layout", config.screenWidthDp <= it)
                    }
                }
                instrumentation.waitForIdleSync()
                capture("home-$suffix")
                scenario.onActivity { activity ->
                    val chart = activity.findViewById<ExpenseDonutView>(R.id.category_chart)
                    assertNotNull("A populated month must show its breakdown", chart)
                    assertEquals(1.0, chart.shares.sumOf { it.fraction }, 1e-10)
                    assertTrue("Chart must have visible bounds", chart.width > 0 && chart.height > 0)
                    val total = activity.findViewById<TextView>(R.id.total)
                    assertEquals("Total must fit on one line", 1, total.lineCount)
                    assertTrue("Total must not hide trailing digits", total.layout.getEllipsisCount(0) == 0)
                    assertTrue("Total must fit its measured width", total.layout.getLineWidth(0) <= total.width)
                }
                if (args.getString("captureBottom") == "true") {
                    scenario.onActivity {
                        val root = it.findViewById<android.view.ViewGroup>(android.R.id.content)
                        (root.getChildAt(0) as android.widget.ScrollView).fullScroll(android.view.View.FOCUS_DOWN)
                    }
                    capture("home-bottom-$suffix")
                }
                scenario.onActivity {
                    it.findViewById<Button>(R.id.record_expense).performClick()
                    it.findViewById<EditText>(R.id.sentence).setText("오늘 주차비 삼천 원")
                    it.findViewById<Button>(R.id.parse_sentence).performClick()
                }
                instrumentation.waitForIdleSync()
                capture("confirm-$suffix")
                scenario.onActivity {
                    it.findViewById<EditText>(R.id.amount).setText("0")
                    it.findViewById<Button>(R.id.save_expense).performClick()
                }
                instrumentation.waitForIdleSync()
                capture("invalid-$suffix")
            }
        } finally {
            ExpenseStore(context).use { db ->
                val ids = entries.map { it.id }.toSet()
                db.month(YearMonth.now()).entries.filter { it.id in ids }.forEach { db.delete(it.id, it.revision) }
            }
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Wait for the render surface as well as the UI message queue before copying pixels.
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val deadline = System.currentTimeMillis() + 15_000
        while (instrumentation.uiAutomation.rootInActiveWindow?.packageName != instrumentation.targetContext.packageName &&
            System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals("Capture must show the ledger, not a system overlay", instrumentation.targetContext.packageName,
            instrumentation.uiAutomation.rootInActiveWindow?.packageName)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}

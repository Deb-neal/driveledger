package com.iancompany.voiceledger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.YearMonth

/** All calls run on the activity's single DB worker, never the UI thread. */
class ExpenseStore(context: Context, name: String = "expenses.db") : SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE expenses (
            id TEXT PRIMARY KEY NOT NULL,
            spent_on TEXT NOT NULL,
            category TEXT NOT NULL CHECK(category IN (${ExpenseCategory.entries.joinToString { "'${it.name}'" }})),
            amount_won INTEGER NOT NULL CHECK(amount_won BETWEEN 1 AND ${Expense.MAX_AMOUNT_WON}),
            revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0)
        )""")
        db.execSQL("CREATE INDEX expenses_date ON expenses(spent_on)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("지원하지 않는 데이터 버전입니다. 기존 기록은 삭제하지 않습니다.")
    }

    fun month(month: YearMonth): MonthLedger {
        val entries = mutableListOf<Expense>()
        readableDatabase.query("expenses", null, "spent_on >= ? AND spent_on < ?",
            arrayOf(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString()),
            null, null, "spent_on DESC, rowid DESC").use { cursor ->
            while (cursor.moveToNext()) entries += Expense(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow("spent_on"))),
                ExpenseCategory.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("category"))),
                cursor.getLong(cursor.getColumnIndexOrThrow("amount_won")),
                cursor.getInt(cursor.getColumnIndexOrThrow("revision")),
            )
        }
        return MonthLedger(month, entries)
    }

    fun insert(entry: Expense) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = db.insertWithOnConflict("expenses", null, values(entry), SQLiteDatabase.CONFLICT_IGNORE)
            if (result == -1L) {
                db.query("expenses", arrayOf("spent_on", "category", "amount_won"), "id = ?",
                    arrayOf(entry.id), null, null, null).use {
                    check(it.moveToFirst() && it.getString(0) == entry.date.toString() &&
                        it.getString(1) == entry.category.name && it.getLong(2) == entry.amountWon) {
                        "이미 다른 내용으로 저장된 기록입니다. 내역을 새로 확인해 주세요."
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun update(entry: Expense) {
        check(writableDatabase.update("expenses", values(entry).apply { put("revision", entry.revision + 1) },
            "id = ? AND revision = ?", arrayOf(entry.id, entry.revision.toString())) == 1) {
            "기록이 변경되었거나 삭제됐어요. 목록에서 다시 열어 주세요."
        }
    }

    fun delete(id: String, revision: Int) {
        check(writableDatabase.delete("expenses", "id = ? AND revision = ?",
            arrayOf(id, revision.toString())) == 1) {
            "기록이 변경되었거나 삭제됐어요. 목록에서 다시 확인해 주세요."
        }
    }

    private fun values(entry: Expense) = ContentValues().apply {
        put("id", entry.id)
        put("spent_on", entry.date.toString())
        put("category", entry.category.name)
        put("amount_won", entry.amountWon)
        put("revision", entry.revision)
    }
}

package com.iancompany.voiceledger

import java.time.LocalDate
import java.time.YearMonth

enum class ExpenseCategory(val label: String) {
    FUEL("주유"), CHARGING("충전"), PARKING("주차"), TOLL("통행료"),
    MAINTENANCE("정비"), INSURANCE("보험"), TAX("자동차세"), OTHER("기타")
}

data class Expense(
    val id: String,
    val date: LocalDate,
    val category: ExpenseCategory,
    val amountWon: Long,
    val revision: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(amountWon in 1..MAX_AMOUNT_WON)
        require(revision >= 0)
    }
    companion object { const val MAX_AMOUNT_WON = 999_999_999L }
}

data class MonthLedger(val month: YearMonth, val entries: List<Expense>) {
    init { require(entries.all { YearMonth.from(it.date) == month }) }
    val totalWon = entries.fold(0L) { sum, entry -> Math.addExact(sum, entry.amountWon) }
    val byCategory = entries.groupBy { it.category }.mapValues { (_, items) ->
        items.fold(0L) { sum, entry -> Math.addExact(sum, entry.amountWon) }
    }
}

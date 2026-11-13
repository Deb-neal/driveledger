package com.iancompany.voiceledger

/** Fractions are presentation data only; stored amounts and totals remain integer won. */
data class CategoryShare(val category: ExpenseCategory, val amountWon: Long, val fraction: Double)

fun MonthLedger.categoryShares(): List<CategoryShare> {
    if (totalWon == 0L) return emptyList()
    return byCategory.entries.sortedWith(
        compareByDescending<Map.Entry<ExpenseCategory, Long>> { it.value }.thenBy { it.key.ordinal }
    ).map { CategoryShare(it.key, it.value, it.value.toDouble() / totalWon) }
}

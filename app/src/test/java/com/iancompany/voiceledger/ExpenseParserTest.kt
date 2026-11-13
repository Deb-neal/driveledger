package com.iancompany.voiceledger

import org.junit.Assert.*
import org.junit.Test

class ExpenseParserTest {
    @Test fun onlineSyntheticTranscriptsPreserveAmountsAndDoNotGuessMissingCategories() {
        // Actual 2026-11-10 API transcripts; parser regression, not an ASR accuracy test.
        for ((text, amount) in listOf("주 3만 원." to 30000L, "주 삼천 원." to 3000L)) {
            val parsed = ExpenseParser.parse(text)
            assertEquals(text, amount, parsed.amountWon)
            assertNull(text, parsed.category)
        }
        for ((text, amount, category) in listOf(
            Triple("주유 3만 원.", 30000L, ExpenseCategory.FUEL),
            Triple("주유 삼천 원.", 3000L, ExpenseCategory.FUEL),
            Triple("오늘 주유 7만 원.", 70000L, ExpenseCategory.FUEL),
            Triple("오늘 주차비 삼천 원.", 3000L, ExpenseCategory.PARKING),
        )) {
            val parsed = ExpenseParser.parse(text)
            assertEquals(text, amount, parsed.amountWon)
            assertEquals(text, category, parsed.category)
        }
    }

    @Test fun equivalentWonFormatsHaveTheSameIntegerAmount() {
        for (value in listOf("삼천", "3천", "3000", "3,000", "삼 천"))
            assertEquals(value, 3000L, ExpenseParser.parseAmount(value))
        for ((input, expected) in mapOf("칠만" to 70_000L, "12만" to 120_000L,
            "십이만" to 120_000L, "1만5천" to 15_000L, "구천구백구십구만구천구백구십구" to 99_999_999L))
            assertEquals(input, expected, ExpenseParser.parseAmount(input))
    }

    @Test fun invalidOrAmbiguousAmountsAreNeverGuessed() {
        for (input in listOf("", "0", "-3000", "1.5만", "3,00", "1,000만", "삼오", "천천", "십백", "억", "1000000000", "999999999999999999999", "삼천오천"))
            assertNull(input, ExpenseParser.parseAmount(input))
    }

    @Test fun oneExpenseIsReadButStillRequiresConfirmation() {
        val parsed = ExpenseParser.parse("오늘 주차 비 3천원")
        assertEquals(3000L, parsed.amountWon)
        assertEquals(ExpenseCategory.PARKING, parsed.category)
        assertTrue(parsed.message.contains("확인"))
    }

    @Test fun twoExpensesRangesNegativeAmountsAndRefundsRequireManualEntry() {
        for (text in listOf("주유 7만원 주차 3천원", "주유 주차 7만원", "주차 3~5천원",
            "주차 -3000원", "주유 1.5만원", "주유 1억5천원", "주유 1e3원", "주차 환불 3000원", "주차 3000원에서 할인"))
            assertNull(text, ExpenseParser.parse(text).amountWon)
    }

    @Test fun unsupportedDatesAreNotSilentlyRecordedAsToday() {
        for (text in listOf("어제 주유 7만원", "지난달 주차 3천원", "11월 1일 주차 3천원", "내일 정비 12만원")) {
            assertNull(text, ExpenseParser.parse(text).amountWon)
            assertNull(text, ExpenseParser.parse(text).category)
        }
    }

    @Test fun unknownCategoryStaysUnselectedAndMissingWonStaysUnknown() {
        assertNull(ExpenseParser.parse("오늘 3천원").category)
        assertEquals(3000L, ExpenseParser.parse("오늘 3천원").amountWon)
        assertNull(ExpenseParser.parse("오늘 주차 3000").amountWon)
    }

    @Test fun unsupportedNumbersMustNotBePartiallyRecognized() {
        for (text in listOf("주유 일곱만원", "주유 여덟 만원", "주유 7만원 3000", "주유 약 7만원", "주유 7만원 정도", "주유 한만원", "주유 두 만원"))
            assertNull(text, ExpenseParser.parse(text).amountWon)
        assertEquals(3000L, ExpenseParser.parse("오늘 주차비 삼천 원.").amountWon)
        assertEquals(70000L, ExpenseParser.parse("주유칠만원").amountWon)
        assertEquals(10000L, ExpenseParser.parse("자동차세 만원").amountWon)
    }
}

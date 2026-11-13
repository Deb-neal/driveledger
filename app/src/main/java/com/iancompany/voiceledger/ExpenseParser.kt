package com.iancompany.voiceledger

data class ParsedExpense(val amountWon: Long?, val category: ExpenseCategory?, val message: String)

/** shortcut: supports one current-day KRW expense; expand only with ambiguity tests. */
object ExpenseParser {
    private val money = Regex("[0-9영공일이삼사오육칠팔구십백천만,\\s]+원")
    private val aliases = mapOf(
        ExpenseCategory.FUEL to listOf("주유", "기름값"),
        ExpenseCategory.CHARGING to listOf("충전"),
        ExpenseCategory.PARKING to listOf("주차"),
        ExpenseCategory.TOLL to listOf("통행료", "톨비", "톨게이트"),
        ExpenseCategory.MAINTENANCE to listOf("정비", "수리", "엔진오일", "타이어", "세차"),
        ExpenseCategory.INSURANCE to listOf("보험"),
        ExpenseCategory.TAX to listOf("자동차세"),
    )

    fun parse(text: String): ParsedExpense {
        val sentence = text.trim().trimEnd('.', '!', '?', '。')
        val compact = sentence.replace(Regex("\\s+"), "")
        if (text.length > 200 || compact.isBlank()) return ParsedExpense(null, null, "지출 한 건을 입력해 주세요.")
        if (Regex("어제|그제|그저께|지난|내일|모레|\\d+[월일]|[0-9]{4}[-/]").containsMatchIn(compact))
            return ParsedExpense(null, null, "문장 입력은 오늘 지출만 지원해요. 날짜를 확인해 주세요.")
        if (Regex("[.．~〜～−–—+＋/]|마이너스|환불|취소|할인|에서|부터").containsMatchIn(compact) || '-' in compact)
            return ParsedExpense(null, null, "범위·환불·계산식은 처리하지 않아요. 실제 지출 한 건을 직접 입력해 주세요.")
        val categories = aliases.filterValues { words -> words.any { it in compact } }.keys
        val matches = money.findAll(sentence).toList()
        if (matches.size != 1 || categories.size > 1)
            return ParsedExpense(null, categories.singleOrNull(), "한 번에 지출 한 건과 금액 하나만 입력해 주세요.")
        val match = matches.single()
        // Do not accept the tail of an unsupported number such as 1억 5천 원 or 1e3원.
        val prefix = sentence.substring(0, match.range.first).trimEnd()
        val rest = sentence.removeRange(match.range)
        val attachedToUnknownText = match.value.first().isWhitespace().not() && prefix.lastOrNull()?.isLetter() == true &&
            (aliases.values.flatten() + listOf("오늘", "비", "비용", "금액", "결제")).none { prefix.endsWith(it) }
        val unsupported = attachedToUnknownText || Regex("(?:^|\\s)(한|두|세|네|열|스무)$").containsMatchIn(prefix) ||
            Regex("일곱|여덟|아홉|다섯|여섯|스물|서른|대략|정도|쯤|약|[0-9]|[일이삼사오육칠팔구][십백천만]").containsMatchIn(rest)
        val amount = if (unsupported || prefix.lastOrNull()?.let { it.isDigit() || it in "억조.eE" } == true) null
            else parseAmount(match.value.removeSuffix("원"))
        return ParsedExpense(amount, categories.singleOrNull(), when {
            amount == null -> "금액을 확실히 읽지 못했어요. 원 단위 숫자로 직접 입력해 주세요."
            categories.isEmpty() -> "항목을 선택하고 금액을 확인해 주세요."
            else -> "읽은 금액과 항목이 맞는지 확인해 주세요. 아직 저장되지 않았어요."
        })
    }

    fun parseAmount(input: String): Long? {
        val value = input.replace(Regex("\\s+"), "")
        if (value.isEmpty() || value.length > 30) return null
        val number = if (',' in value) {
            if (!Regex("[1-9][0-9]{0,2}(,[0-9]{3})+").matches(value)) return null
            value.replace(",", "").toLongOrNull()
        } else if (value.all { it in '0'..'9' }) value.toLongOrNull()
        else {
            val chunks = value.split('만')
            if (chunks.size > 2) return null
            if (chunks.size == 2) {
                val high = if (chunks[0].isEmpty()) 1L else smallNumber(chunks[0]) ?: return null
                val low = if (chunks[1].isEmpty()) 0L else smallNumber(chunks[1]) ?: return null
                if (high !in 1..99_999 || low !in 0..9_999) return null
                high * 10_000 + low
            } else smallNumber(value)
        }
        return number?.takeIf { it in 1..Expense.MAX_AMOUNT_WON }
    }

    private fun smallNumber(value: String): Long? {
        if (value.all { it in '0'..'9' }) return value.toLongOrNull()
        val digits = "영일이삼사오육칠팔구"
        var total = 0L
        var pending: Long? = null
        var lastUnit = 10_000
        for (char in value) {
            val unit = when (char) { '천' -> 1000; '백' -> 100; '십' -> 10; else -> null }
            if (unit != null) {
                if (unit >= lastUnit || pending == 0L || (pending ?: 1) > 9) return null
                total += (pending ?: 1) * unit
                pending = null
                lastUnit = unit
            } else {
                val digit = if (char in '0'..'9') char - '0' else digits.indexOf(char)
                if (digit < 0 || pending != null) return null
                pending = digit.toLong()
            }
        }
        return total + (pending ?: 0)
    }
}

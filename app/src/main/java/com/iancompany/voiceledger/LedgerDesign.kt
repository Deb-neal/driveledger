package com.iancompany.voiceledger

/** Shared roles for both ledger screens; sizes are sp before the wide-window multiplier. */
internal enum class TextRole(val size: Float, val lineHeight: Float, val strong: Boolean = false) {
    Display(56f, 64f, true),
    Metric(32f, 44f, true),
    PageTitle(32f, 44f, true),
    SectionTitle(24f, 34f, true),
    AmountInput(40f, 52f, true),
    Amount(24f, 34f, true),
    Input(24f, 34f),
    Body(20f, 30f),
    Label(20f, 30f, true),
    Caption(16f, 24f),
    CaptionStrong(16f, 24f, true),
}

internal object Gap {
    const val XS = 8
    const val S = 12
    const val M = 16
    const val L = 24
    const val XL = 32
    const val XXL = 40
    const val Page = 48
}

internal const val SURFACE_RADIUS = 16

internal enum class ActionTone(val background: Int, val foreground: Int) {
    Primary(R.color.primary, R.color.on_primary),
    Secondary(R.color.primary_container, R.color.primary),
    Neutral(R.color.field, R.color.on_surface),
    Destructive(R.color.surface_container, R.color.error),
    Danger(R.color.error, R.color.on_error),
}

internal fun categoryColor(category: ExpenseCategory) = when (category) {
    ExpenseCategory.FUEL -> R.color.chart_fuel
    ExpenseCategory.CHARGING -> R.color.chart_charging
    ExpenseCategory.PARKING -> R.color.chart_parking
    ExpenseCategory.TOLL -> R.color.chart_toll
    ExpenseCategory.MAINTENANCE -> R.color.chart_maintenance
    ExpenseCategory.INSURANCE -> R.color.chart_insurance
    ExpenseCategory.TAX -> R.color.chart_tax
    ExpenseCategory.OTHER -> R.color.chart_other
}

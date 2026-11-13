package com.iancompany.voiceledger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: ExpenseStore
    private var month = YearMonth.now()
    private var draft: Draft? = null
    private var busy = false
    private var request = 0
    private lateinit var page: LinearLayout
    private var amountField: EditText? = null
    private var sentenceField: EditText? = null
    private var categoryField: Spinner? = null
    private var statusView: TextView? = null
    private var voiceStatusView: TextView? = null
    private var vehicleStatusView: TextView? = null
    private lateinit var voice: VoiceInput
    private lateinit var parkedGate: ParkedGate
    private var resumed = false
    private var voiceDraftId: String? = null
    private var voiceMessage = R.string.voice_hint
    private var onlineVoiceAccepted = false
    private var permissionAsked = false
    private val currency = NumberFormat.getIntegerInstance(Locale.KOREA)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA)
    private val regular by lazy { resources.getFont(R.font.ibm_plex_sans_kr_regular) }
    private val strong by lazy { resources.getFont(R.font.ibm_plex_sans_kr_semibold) }
    private val spacious get() = resources.configuration.screenWidthDp >= 1200
    private fun sp(value: Float) = value * if (spacious) 1.25f else 1f

    private data class Draft(
        val id: String = UUID.randomUUID().toString(),
        val date: LocalDate = LocalDate.now(),
        val editing: Boolean = false,
        val revision: Int = 0,
        var amount: String = "",
        var category: Int = 0,
        var sentence: String = "",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ExpenseStore(applicationContext)
        parkedGate = ParkedGate(this) {
            if (!parkedGate.allowed && voice.busy) voice.cancel(R.string.voice_vehicle_blocked)
            refreshControls()
        }
        voice = VoiceInput(this, changed = { _, message ->
            voiceMessage = message
            refreshControls()
        }, result = { text ->
            if (resumed && parkedGate.allowed && draft?.id == voiceDraftId && draft?.editing == false) {
                sentenceField?.setText(text)
                parseSentence()
            }
        })
        savedInstanceState?.let { state ->
            permissionAsked = state.getBoolean("permissionAsked")
            state.getString("month")?.let { month = YearMonth.parse(it) }
            state.getString("draftId")?.let { id ->
                draft = Draft(id, LocalDate.parse(state.getString("date")), state.getBoolean("editing"),
                    state.getInt("revision"), state.getString("amount", ""), state.getInt("category"),
                    state.getString("sentence", ""))
            }
        }
        if (draft != null) showForm() else showHome()
    }

    private fun root(): LinearLayout {
        if (voice.busy) voice.cancel()
        voiceDraftId = null
        voiceMessage = R.string.voice_hint
        voiceStatusView = null; vehicleStatusView = null
        amountField = null; sentenceField = null; categoryField = null; statusView = null
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = if (spacious) Gap.Page else Gap.L
            setPadding(dp(inset), dp(Gap.XL), dp(inset), dp(Gap.L))
            isFocusableInTouchMode = true
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(getColor(R.color.surface))
            addView(page, ViewGroup.LayoutParams(-1, -2))
        })
        return page
    }

    private fun showHome(message: String = "") {
        val generation = ++request
        val selectedMonth = month
        root()
        val header = row(page)
        icon(header, R.drawable.ic_ledger, R.color.primary, 40)
        label(header, getString(R.string.app_name), TextRole.PageTitle, weight = 1f).apply {
            setPadding(dp(Gap.M), 0, 0, 0)
        }
        val months = if (resources.configuration.screenWidthDp >= 840) header else row(page, Gap.L)
        iconButton(months, R.drawable.ic_left, R.string.previous_month, R.id.previous_month) {
            month = month.minusMonths(1); showHome()
        }
        label(months, getString(R.string.month_format, month.year, month.monthValue), TextRole.SectionTitle).apply {
            setPadding(dp(Gap.L), 0, dp(Gap.L), 0)
        }
        iconButton(months, R.drawable.ic_right, R.string.next_month, R.id.next_month) {
            month = month.plusMonths(1); showHome()
        }
        val actionParent = if (spacious) header else page
        button(actionParent, R.string.record_new, R.id.record_expense, tone = ActionTone.Primary, full = !spacious) {
            if (canEdit()) { draft = Draft(); showForm() }
        }.apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(tintedIcon(R.drawable.ic_plus, R.color.on_primary), null, null, null)
            compoundDrawablePadding = dp(Gap.S)
            if (spacious) (layoutParams as LinearLayout.LayoutParams).marginStart = dp(Gap.XL)
        }
        label(page, getString(R.string.home_description), TextRole.Body, secondary = true, top = Gap.S)
        vehicleStatusView = label(page, getString(R.string.vehicle_blocked), TextRole.Caption, secondary = true, top = Gap.XS).apply {
            id = R.id.vehicle_status
        }
        statusView = label(page, message.ifEmpty { getString(R.string.loading) }, TextRole.Caption, secondary = true, top = Gap.S).apply {
            id = R.id.status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            page.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Gap.XL) })
        }
        val footer = row(page, Gap.L)
        label(footer, getString(R.string.parked_only), TextRole.Caption, secondary = true, weight = 1f)
        if (spacious) label(footer, getString(R.string.local_note), TextRole.Caption, secondary = true)
        worker.execute {
            val result = runCatching { store.month(selectedMonth) }
            runOnUiThread {
                if (isDestroyed || generation != request) return@runOnUiThread
                result.fold(onSuccess = { ledger ->
                    setStatus(message)
                    renderLedger(content, ledger)
                    refreshControls()
                }, onFailure = {
                    setStatus(getString(R.string.load_error), error = true)
                    button(content, R.string.retry) { showHome() }
                })
            }
        }
    }

    private fun renderLedger(parent: LinearLayout, ledger: MonthLedger) {
        val wide = resources.configuration.screenWidthDp >= 1000
        val shares = ledger.categoryShares()
        val metrics = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        fun metric(weight: Float, first: Boolean = false): LinearLayout = column(metrics, if (wide) weight else 0f).apply {
            background = surface(R.color.surface_container)
            setPadding(dp(Gap.L), dp(Gap.L), dp(Gap.L), dp(Gap.L))
            minimumHeight = dp(if (spacious) 200 else 180)
            (layoutParams as LinearLayout.LayoutParams).apply {
                if (wide) height = ViewGroup.LayoutParams.MATCH_PARENT
                if (!first) { if (wide) marginStart = dp(Gap.L) else topMargin = dp(Gap.M) }
            }
        }
        val total = metric(.46f, true)
        label(total, getString(R.string.recorded_total), TextRole.Label)
        label(total, won(ledger.totalWon), TextRole.Display, top = Gap.S).apply {
            id = R.id.total
            setTextColor(getColor(R.color.primary))
            fontFeatureSettings = "tnum"
            setAutoSizeTextTypeUniformWithConfiguration(24, sp(TextRole.Display.size).toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
            maxLines = 1; layoutParams.width = -1
        }
        label(total, getString(R.string.total_note), TextRole.Caption, secondary = true, top = Gap.XS)
        val count = metric(.20f)
        label(count, getString(R.string.record_count), TextRole.Label)
        label(count, getString(R.string.month_count, ledger.entries.size), TextRole.Metric, top = Gap.L)
        label(count, getString(R.string.selected_month_note), TextRole.Caption, secondary = true, top = Gap.S)
        val top = metric(.34f)
        label(top, getString(R.string.largest_category), TextRole.Label)
        label(top, shares.firstOrNull()?.category?.label ?: getString(R.string.no_expenses), TextRole.Metric, top = Gap.L)
        label(top, shares.firstOrNull()?.let { "${won(it.amountWon)} · ${percent(it.fraction)}" }
            ?: getString(R.string.first_record_hint), TextRole.Caption, secondary = true, top = Gap.S)

        val details = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.TOP
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Gap.L) })
        }
        val breakdown = column(details, if (wide) .42f else 0f).apply {
            background = surface(R.color.surface_container)
            setPadding(dp(Gap.XL), dp(Gap.XL), dp(Gap.XL), dp(Gap.XL))
        }
        label(breakdown, getString(R.string.category_totals), TextRole.SectionTitle)
        label(breakdown, getString(R.string.category_share_note), TextRole.Caption, secondary = true, top = Gap.XS)
        if (shares.isEmpty()) {
            val empty = column(breakdown).apply {
                gravity = Gravity.CENTER; minimumHeight = dp(320)
                setPadding(0, dp(Gap.XL), 0, dp(Gap.XL))
            }
            icon(empty, R.drawable.ic_ledger, R.color.outline, 64)
            label(empty, getString(R.string.chart_empty_title), TextRole.Label, top = Gap.L).gravity = Gravity.CENTER
            label(empty, getString(R.string.chart_empty_body), TextRole.Caption, secondary = true, top = Gap.S).gravity = Gravity.CENTER
        } else {
            breakdown.addView(ExpenseDonutView(this, shares).apply { id = R.id.category_chart },
                LinearLayout.LayoutParams(-1, dp(if (spacious) 240 else 200)).apply { topMargin = dp(Gap.L); bottomMargin = dp(Gap.L) })
            for (share in shares) {
                val legend = row(breakdown, Gap.M).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    contentDescription = "${share.category.label}, ${won(share.amountWon)}, ${percent(share.fraction)}"
                }
                legend.addView(View(this).apply { background = surface(categoryColor(share.category)) },
                    LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(Gap.S) })
                label(legend, share.category.label, TextRole.Body, weight = 1f)
                val values = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.END
                    legend.addView(this, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(Gap.S) })
                }
                label(values, won(share.amountWon), TextRole.Label).fontFeatureSettings = "tnum"
                label(values, percent(share.fraction), TextRole.Caption, secondary = true)
                for (i in 0 until legend.childCount) legend.getChildAt(i).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }

        val entries = column(details, if (wide) .58f else 0f).apply {
            id = R.id.entries
            background = surface(R.color.surface_container)
            setPadding(dp(Gap.XL), dp(Gap.XL), dp(Gap.XL), dp(Gap.XL))
            (layoutParams as LinearLayout.LayoutParams).apply {
                if (wide) marginStart = dp(Gap.L) else topMargin = dp(Gap.L)
            }
        }
        val title = row(entries)
        label(title, getString(R.string.recent_entries), TextRole.SectionTitle, weight = 1f)
        label(title, getString(R.string.month_count, ledger.entries.size), TextRole.Caption, secondary = true)
        label(entries, getString(R.string.edit_hint), TextRole.Caption, secondary = true, top = Gap.XS)
        if (ledger.entries.isEmpty()) {
            val empty = column(entries).apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(320)
                setPadding(dp(Gap.L), dp(Gap.XXL), dp(Gap.L), dp(Gap.XXL))
            }
            icon(empty, R.drawable.ic_ledger, R.color.outline, 64)
            label(empty, getString(R.string.empty_title), TextRole.Label, top = Gap.L).gravity = Gravity.CENTER
            label(empty, getString(R.string.empty_body), TextRole.Caption, secondary = true, top = Gap.S).gravity = Gravity.CENTER
        } else {
            val heading = row(entries, Gap.L)
            label(heading, getString(R.string.expense_description), TextRole.Caption, secondary = true, weight = 1f)
            label(heading, getString(R.string.amount_label), TextRole.Caption, secondary = true).setPadding(0, 0, dp(48), 0)
            divider(entries, R.color.divider, Gap.S)
            for (entry in ledger.entries.take(20)) {
                val item = row(entries).apply {
                    tag = entry.id
                    minimumHeight = dp(if (spacious) 112 else 96)
                    setPadding(0, dp(Gap.M), 0, dp(Gap.M))
                    background = ripple(R.color.surface_container)
                    isFocusable = true
                    contentDescription = getString(R.string.open_entry, entry.date.format(dateFormat), entry.category.label, won(entry.amountWon))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    setOnClickListener {
                        if (!canEdit()) return@setOnClickListener
                        draft = Draft(entry.id, entry.date, true, entry.revision, entry.amountWon.toString(), entry.category.ordinal + 1)
                        showForm()
                    }
                }
                icon(item, categoryIcon(entry.category), R.color.primary, 48, R.color.primary_container)
                val description = column(item, 1f).apply { setPadding(dp(Gap.M), 0, dp(Gap.S), 0) }
                label(description, entry.category.label, TextRole.Label)
                label(description, entry.date.format(dateFormat), TextRole.Caption, secondary = true, top = Gap.XS)
                label(item, won(entry.amountWon), TextRole.Amount).fontFeatureSettings = "tnum"
                icon(item, R.drawable.ic_right, R.color.on_surface_variant, 24).apply {
                    (layoutParams as LinearLayout.LayoutParams).marginStart = dp(Gap.L)
                }
                for (i in 0 until item.childCount) item.getChildAt(i).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                divider(entries, R.color.divider, 0)
            }
            if (ledger.entries.size > 20) label(entries, getString(R.string.recent_limit), TextRole.Caption, secondary = true, top = Gap.L)
        }
    }

    private fun percent(fraction: Double): String =
        if (fraction > 0.0 && fraction < .001) getString(R.string.share_under_point_one)
        else NumberFormat.getPercentInstance(Locale.KOREA).apply { maximumFractionDigits = 1 }.format(fraction)

    private fun showForm() {
        ++request
        val current = draft ?: return
        root()
        val content = if (current.editing) object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(
                    minOf(MeasureSpec.getSize(widthMeasureSpec), dp(960)), MeasureSpec.getMode(widthMeasureSpec)), heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            page.addView(this, LinearLayout.LayoutParams(-1, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        } else page
        label(content, getString(if (current.editing) R.string.edit_title else R.string.input_title), TextRole.PageTitle)
        label(content, getString(R.string.date_label, current.date.format(dateFormat)), TextRole.Body, secondary = true, top = Gap.XS)
        vehicleStatusView = label(content, getString(R.string.vehicle_blocked), TextRole.Caption, secondary = true, top = Gap.S).apply {
            id = R.id.vehicle_status
        }
        val body = LinearLayout(this).apply {
            orientation = if (resources.configuration.screenWidthDp >= 840) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Gap.XL) })
        }
        if (!current.editing) {
            val source = column(body, if (body.orientation == LinearLayout.HORIZONTAL) 1f else 0f)
            source.setPadding(0, 0, if (body.orientation == LinearLayout.HORIZONTAL) dp(Gap.XL) else 0, dp(Gap.M))
            label(source, getString(R.string.voice_title), TextRole.SectionTitle)
            if (BuildConfig.ONLINE_VOICE) label(source, getString(
                if (BuildConfig.LOCAL_ONLINE_VOICE) R.string.voice_local_notice else R.string.voice_online_notice),
                TextRole.Body, secondary = true, top = Gap.S)
            button(source, R.string.voice_start, R.id.voice_start, tone = ActionTone.Primary, full = true) { requestVoice() }
            voiceStatusView = label(source, getString(voiceMessage), TextRole.Body, secondary = true, top = Gap.M).apply {
                id = R.id.voice_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            button(source, R.string.voice_cancel, R.id.voice_cancel, tone = ActionTone.Neutral, full = true) { voice.cancel() }
            button(source, R.string.voice_settings, R.id.voice_settings, tone = ActionTone.Neutral, full = true) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            label(source, getString(R.string.manual_or_recognized), TextRole.Body, secondary = true, top = Gap.XL).labelFor = R.id.sentence
            sentenceField = EditText(this).apply {
                id = R.id.sentence
                applyTextRole(this, TextRole.Input)
                minHeight = dp(128)
                gravity = Gravity.TOP
                styleInput(this, R.color.surface_container)
                setPadding(dp(Gap.L), dp(Gap.L), dp(Gap.L), dp(Gap.L))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                filters = arrayOf(InputFilter.LengthFilter(200))
                hint = getString(R.string.sentence_hint)
                setText(current.sentence)
                setHintTextColor(getColor(R.color.on_surface_variant))
                source.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Gap.L) })
            }
            label(source, getString(R.string.sentence_help), TextRole.Caption, secondary = true, top = Gap.XS)
            button(source, R.string.parse_sentence, R.id.parse_sentence, full = true) {
                parseSentence()
            }

        }
        val confirm = column(body, if (body.orientation == LinearLayout.HORIZONTAL) 1f else 0f)
        confirm.setPadding(dp(Gap.XL), dp(Gap.XL), dp(Gap.XL), dp(Gap.XL))
        confirm.background = surface(R.color.surface_container)
        if (!current.editing) label(confirm, getString(R.string.confirm_details), TextRole.SectionTitle)
        label(confirm, getString(R.string.amount_label), TextRole.Body, secondary = true,
            top = if (current.editing) 0 else Gap.L).labelFor = R.id.amount
        amountField = EditText(this).apply {
            id = R.id.amount
            applyTextRole(this, TextRole.AmountInput)
            fontFeatureSettings = "tnum"
            minHeight = dp(96)
            styleInput(this, R.color.field)
            setPadding(dp(Gap.L), dp(Gap.M), dp(Gap.L), dp(Gap.M))
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(12))
            setSelectAllOnFocus(true)
            hint = getString(R.string.amount_hint)
            setText(current.amount)
            setHintTextColor(getColor(R.color.on_surface_variant))
            confirm.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Gap.XS) })
        }
        label(confirm, getString(R.string.category_label), TextRole.Body, secondary = true, top = Gap.XL).labelFor = R.id.category
        categoryField = Spinner(this, Spinner.MODE_DIALOG).apply {
            id = R.id.category
            minimumHeight = dp(88)
            background = RippleDrawable(ColorStateList.valueOf(getColor(R.color.primary_container)), fieldBackground(R.color.field), null)
            prompt = getString(R.string.choose_category)
            adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item,
                listOf(getString(R.string.choose_category)) + ExpenseCategory.entries.map { it.label }) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getView(position, convertView, parent).also {
                        applyTextRole(it as TextView, TextRole.Body)
                        it.setTextColor(enabledTextColors(R.color.on_surface))
                        it.setPadding(dp(Gap.L), dp(Gap.L), dp(Gap.L), dp(Gap.L))
                        it.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                            tintedIcon(R.drawable.ic_right, R.color.on_surface_variant), null)
                    }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getDropDownView(position, convertView, parent).also {
                        applyTextRole(it as TextView, TextRole.Body)
                        it.minHeight = dp(64)
                        it.setTextColor(enabledTextColors(R.color.on_surface))
                    }
            }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    isSelected = position > 0
                    if (position > 0) isActivated = false
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            setSelection(current.category)
            confirm.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Gap.XS) })
        }
        statusView = label(confirm, getString(R.string.confirm_help), TextRole.Caption, secondary = true, top = Gap.M).apply {
            id = R.id.status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val actions = adaptiveActions(confirm, Gap.XL)
        button(actions, R.string.cancel, R.id.cancel_expense, tone = ActionTone.Neutral) { cancelDraft() }
        button(actions, if (current.editing) R.string.update else R.string.save,
            R.id.save_expense, tone = ActionTone.Primary) { save() }
        if (current.editing) {
            divider(confirm, R.color.divider, Gap.XL)
            val removal = row(confirm, Gap.M).apply { gravity = Gravity.END }
            button(removal, R.string.delete, R.id.delete_expense, tone = ActionTone.Destructive) { confirmDelete() }
        }
        label(content, getString(R.string.parked_only), TextRole.Caption, secondary = true, top = Gap.L)
        page.requestFocus()
        refreshControls()
    }

    private fun parseSentence() {
        if (!canEdit() || voice.busy) return
        syncDraft()
        val current = draft ?: return
        val parsed = ExpenseParser.parse(current.sentence)
        amountField?.setText(parsed.amountWon?.toString().orEmpty())
        categoryField?.setSelection(parsed.category?.ordinal?.plus(1) ?: 0)
        syncDraft()
        hideKeyboard()
        setStatus(parsed.message, error = parsed.amountWon == null || parsed.category == null)
    }

    private fun syncDraft() {
        draft?.let {
            it.amount = amountField?.text?.toString() ?: it.amount
            it.sentence = sentenceField?.text?.toString() ?: it.sentence
            it.category = categoryField?.selectedItemPosition ?: it.category
        }
    }

    private fun save() {
        if (busy || voice.busy || !canEdit()) return
        syncDraft()
        val current = draft ?: return
        val amount = current.amount.takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()
        if (amount == null || amount !in 1..Expense.MAX_AMOUNT_WON) {
            amountField?.error = getString(R.string.amount_error)
            amountField?.isActivated = true
            setStatus(getString(R.string.amount_error), true)
            return
        }
        val category = ExpenseCategory.entries.getOrNull(current.category - 1)
        if (category == null) {
            categoryField?.isActivated = true
            setStatus(getString(R.string.choose_category), true)
            return
        }
        if (!current.editing && current.date != LocalDate.now()) {
            setStatus(getString(R.string.stale_date), true); return
        }
        val entry = Expense(current.id, current.date, category, amount, current.revision)
        hideKeyboard()
        mutate(current, if (current.editing) R.string.updated else R.string.saved, R.string.save_error) {
            if (current.editing) store.update(entry) else store.insert(entry)
        }
    }

    private fun confirmDelete() {
        if (busy || !canEdit()) return
        syncDraft()
        val current = draft ?: return
        showConfirmation(R.string.delete_question, R.string.delete_explanation,
            R.string.delete, R.string.cancel, destructive = true) {
            if (!canEdit()) return@showConfirmation
            // Deletion addresses the original record, even if unsaved form fields are invalid.
            mutate(current, R.string.deleted, R.string.delete_error) {
                store.delete(current.id, current.revision)
            }
        }
    }

    private fun showConfirmation(title: Int, message: Int, confirm: Int, cancel: Int,
                                 destructive: Boolean = false, onConfirm: () -> Unit) {
        hideKeyboard()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Gap.XL), dp(Gap.XL), dp(Gap.XL), dp(Gap.XL))
        }
        label(content, getString(title), TextRole.PageTitle).isAccessibilityHeading = true
        label(content, getString(message), TextRole.Body, secondary = true, top = Gap.M).apply {
            id = android.R.id.message
        }
        val dialog = AlertDialog.Builder(this).create()
        dialog.setView(ScrollView(this).apply {
            addView(content)
        }, 0, 0, 0, 0)
        val actions = adaptiveActions(content, Gap.XL)
        button(actions, cancel, android.R.id.button2, tone = ActionTone.Neutral) { dialog.dismiss() }
        button(actions, confirm, android.R.id.button1,
            tone = if (destructive) ActionTone.Danger else ActionTone.Primary) {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(surface(R.color.surface_container))
            decorView.elevation = dp(4).toFloat()
            setDimAmount(0.24f)
            // Size within the app's window, not the full vehicle display.
            val available = page.width.takeIf { it > 0 } ?: dp(resources.configuration.screenWidthDp)
            setLayout(minOf(dp(880), available - dp(Gap.L * 2)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    /** Reflow actions using their own available width, including larger system text. */
    private fun adaptiveActions(parent: LinearLayout, top: Int) = object : LinearLayout(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val horizontal = MeasureSpec.getSize(widthMeasureSpec) >= dp(560) * resources.configuration.fontScale
            orientation = if (horizontal) HORIZONTAL else VERTICAL
            for (i in 0 until childCount) {
                getChildAt(i).layoutParams = LayoutParams(if (horizontal) 0 else -1, -2,
                    if (horizontal) 1f else 0f).apply {
                    if (i > 0) {
                        if (horizontal) marginStart = dp(Gap.M) else topMargin = dp(Gap.S)
                    }
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun mutate(current: Draft, success: Int, failure: Int, action: () -> Unit) {
        busy = true
        val deleting = success == R.string.deleted
        val actionButton = findViewById<Button>(if (deleting) R.id.delete_expense else R.id.save_expense)
        val originalTitle = actionButton?.text
        val busyTitle = getString(if (deleting) R.string.deleting else R.string.saving)
        actionButton?.text = busyTitle
        enableTree(page, false)
        setStatus(busyTitle)
        worker.execute {
            val result = runCatching(action)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = false
                result.fold(onSuccess = {
                    draft = null
                    month = YearMonth.from(current.date)
                    showHome(getString(success))
                }, onFailure = { error ->
                    refreshControls()
                    actionButton?.text = originalTitle
                    setStatus(if (error is IllegalStateException) error.message ?: getString(failure) else getString(failure), true)
                })
            }
        }
    }

    private fun cancelDraft() {
        if (busy) return
        if (voice.busy) voice.cancel()
        syncDraft()
        val current = draft ?: return
        fun discard() { hideKeyboard(); draft = null; showHome() }
        if (current.amount.isBlank() && current.sentence.isBlank() && current.category == 0) discard()
        else showConfirmation(R.string.discard_question, R.string.discard_detail,
            R.string.discard, R.string.keep_editing) { discard() }
    }

    @Deprecated("Android Activity back handling for this two-screen MVP")
    override fun onBackPressed() {
        if (busy) return
        if (voice.busy) voice.cancel()
        else if (draft != null) cancelDraft() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        syncDraft()
        outState.putString("month", month.toString())
        outState.putBoolean("permissionAsked", permissionAsked)
        draft?.let {
            outState.putString("draftId", it.id); outState.putString("date", it.date.toString())
            outState.putBoolean("editing", it.editing); outState.putInt("revision", it.revision)
            outState.putString("amount", it.amount); outState.putInt("category", it.category)
            outState.putString("sentence", it.sentence)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        voice.close()
        parkedGate.stop()
        ++request
        worker.execute { store.close() }
        worker.shutdown()
        super.onDestroy()
    }

    override fun onStart() { super.onStart(); parkedGate.start() }
    override fun onResume() { super.onResume(); resumed = true; refreshControls() }
    override fun onPause() {
        resumed = false
        if (voice.busy) voice.cancel(R.string.voice_background)
        refreshControls()
        super.onPause()
    }
    override fun onStop() { parkedGate.stop(); super.onStop() }

    private fun canEdit(): Boolean {
        val allowed = resumed && parkedGate.allowed
        if (!allowed) setStatus(getString(R.string.vehicle_blocked), true)
        return allowed
    }

    private fun requestVoice() {
        if (busy || !canEdit() || draft?.editing != false) return
        if (voice.state == VoiceInput.State.RECORDING) { voice.finishRecording(); return }
        if (voice.busy) return
        if (BuildConfig.ONLINE_VOICE && !onlineVoiceAccepted) {
            showConfirmation(R.string.voice_online_title,
                if (BuildConfig.LOCAL_ONLINE_VOICE) R.string.voice_local_consent else R.string.voice_online_consent,
                R.string.voice_online_accept, R.string.cancel) {
                onlineVoiceAccepted = true
                requestVoice()
            }
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionAsked = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
            return
        }
        syncDraft()
        hideKeyboard()
        voiceDraftId = draft?.id
        voice.start()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 20) {
            voiceMessage = if (grants.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                R.string.voice_permission_granted else R.string.voice_permission_denied
            refreshControls() // Permission approval does not start a microphone unexpectedly.
        }
    }

    private fun refreshControls() {
        if (!::page.isInitialized) return
        val allowed = resumed && parkedGate.allowed
        vehicleStatusView?.setText(if (parkedGate.allowed) R.string.vehicle_ready else R.string.vehicle_blocked)
        voiceStatusView?.setText(voiceMessage)
        if (busy) { enableTree(page, false); return }
        enableTree(page, true)
        for (id in listOf(R.id.record_expense, R.id.sentence, R.id.parse_sentence, R.id.amount,
            R.id.category, R.id.save_expense, R.id.delete_expense)) {
            findViewById<View>(id)?.isEnabled = allowed && !voice.busy
        }
        findViewById<View>(R.id.entries)?.let { enableTree(it, allowed) }
        findViewById<Button>(R.id.voice_start)?.apply {
            setText(if (voice.state == VoiceInput.State.RECORDING) R.string.voice_finish
                else if (BuildConfig.ONLINE_VOICE) R.string.voice_online_start else R.string.voice_start)
            isEnabled = allowed && (!voice.busy || voice.state == VoiceInput.State.RECORDING)
        }
        findViewById<View>(R.id.voice_cancel)?.apply {
            visibility = if (voice.busy) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.voice_settings)?.visibility = if (permissionAsked &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) View.VISIBLE else View.GONE
        if (!allowed) hideKeyboard()
    }

    private fun setStatus(text: String, error: Boolean = false) {
        statusView?.apply {
            this.text = text
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            setTextColor(getColor(if (error) R.color.error else R.color.on_surface_variant))
        }
    }

    private fun enableTree(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (index in 0 until view.childCount) enableTree(view.getChildAt(index), enabled)
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(page.windowToken, 0)
        page.clearFocus()
    }

    private fun label(parent: LinearLayout, value: String, role: TextRole,
                      secondary: Boolean = false, top: Int = 0, weight: Float = 0f) = TextView(this).apply {
        text = value; applyTextRole(this, role)
        setTextColor(getColor(if (secondary) R.color.on_surface_variant else R.color.on_surface))
        if (weight > 0) gravity = Gravity.CENTER_VERTICAL
        parent.addView(this, LinearLayout.LayoutParams(if (weight > 0) 0 else -2, -2, weight).apply { topMargin = dp(top) })
    }

    private fun button(parent: LinearLayout, title: Int, viewId: Int = View.NO_ID,
                       tone: ActionTone = ActionTone.Secondary, full: Boolean = false, action: () -> Unit) = Button(this).apply {
        id = viewId; setText(title); applyTextRole(this, TextRole.Label)
        isAllCaps = false; minHeight = dp(if (spacious) 80 else 64)
        setPadding(dp(Gap.L), dp(Gap.S), dp(Gap.L), dp(Gap.S))
        stateListAnimator = null
        backgroundTintList = null
        background = ripple(tone.background, tone.foreground)
        setTextColor(enabledTextColors(tone.foreground))
        parent.addView(this, LinearLayout.LayoutParams(if (full) -1 else -2, -2).apply {
            if (parent.orientation == LinearLayout.VERTICAL) topMargin = dp(Gap.S)
            else marginStart = dp(Gap.XS)
        })
        setOnClickListener { action() }
    }

    private fun row(parent: LinearLayout, top: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun column(parent: LinearLayout, weight: Float = 0f) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        parent.addView(this, LinearLayout.LayoutParams(if (weight > 0) 0 else -1, -2, weight))
    }

    private fun applyTextRole(view: TextView, role: TextRole) {
        view.textSize = sp(role.size)
        view.typeface = if (role.strong) strong else regular
        view.includeFontPadding = false
        if (role == TextRole.Display) view.setLineSpacing(0f, 1.15f)
        else view.setLineHeight(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            sp(role.lineHeight), resources.displayMetrics).toInt())
    }

    private fun fieldBackground(color: Int) = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), surface(R.color.disabled_surface))
        addState(intArrayOf(android.R.attr.state_activated), surface(color, R.color.error, 2))
        addState(intArrayOf(android.R.attr.state_focused), surface(color, R.color.primary, 2))
        addState(intArrayOf(android.R.attr.state_selected), surface(color, R.color.outline))
        addState(intArrayOf(), surface(color, R.color.outline))
    }

    private fun styleInput(view: EditText, color: Int) {
        view.backgroundTintList = null
        view.background = fieldBackground(color)
        view.setTextColor(enabledTextColors(R.color.on_surface))
        view.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                view.isSelected = !s.isNullOrEmpty()
                view.isActivated = false
                view.error = null
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun enabledTextColors(color: Int) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(getColor(R.color.on_disabled), getColor(color)))

    private fun ripple(color: Int, focusColor: Int = R.color.primary) = RippleDrawable(
        ColorStateList.valueOf(getColor(R.color.outline) and 0x00ffffff or 0x33000000),
        StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), surface(R.color.disabled_surface))
            addState(intArrayOf(android.R.attr.state_focused), surface(color, focusColor, 2))
            addState(intArrayOf(), surface(color))
        }, null)

    private fun tintedIcon(resource: Int, color: Int) = getDrawable(resource)!!.mutate().apply {
        setTint(getColor(color)); setBounds(0, 0, dp(Gap.L), dp(Gap.L))
    }

    private fun icon(parent: LinearLayout, resource: Int, color: Int, size: Int, backing: Int? = null) = ImageView(this).apply {
        setImageDrawable(tintedIcon(resource, color))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (backing != null) { background = surface(backing); setPadding(dp(Gap.S), dp(Gap.S), dp(Gap.S), dp(Gap.S)) }
        parent.addView(this, LinearLayout.LayoutParams(dp(size), dp(size)))
    }

    private fun iconButton(parent: LinearLayout, resource: Int, title: Int, viewId: Int, action: () -> Unit) = ImageButton(this).apply {
        id = viewId
        contentDescription = getString(title)
        setImageDrawable(tintedIcon(resource, R.color.on_surface))
        background = ripple(R.color.surface_container)
        val size = dp(if (spacious) 72 else 64)
        parent.addView(this, LinearLayout.LayoutParams(size, size))
        setOnClickListener { action() }
    }

    private fun divider(parent: LinearLayout, color: Int, top: Int) = View(this).apply {
        setBackgroundColor(getColor(color))
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(top) })
    }

    private fun categoryIcon(category: ExpenseCategory) = when (category) {
        ExpenseCategory.FUEL -> R.drawable.ic_fuel
        ExpenseCategory.CHARGING -> R.drawable.ic_charging
        ExpenseCategory.PARKING -> R.drawable.ic_parking
        ExpenseCategory.TOLL -> R.drawable.ic_toll
        ExpenseCategory.MAINTENANCE -> R.drawable.ic_maintenance
        ExpenseCategory.INSURANCE -> R.drawable.ic_insurance
        ExpenseCategory.TAX -> R.drawable.ic_tax
        ExpenseCategory.OTHER -> R.drawable.ic_other
    }

    private fun surface(color: Int, border: Int? = null, borderWidth: Int = 1) = GradientDrawable().apply {
        setColor(getColor(color)); cornerRadius = dp(SURFACE_RADIUS).toFloat()
        if (border != null) setStroke(dp(borderWidth), getColor(border))
    }
    private fun won(value: Long) = getString(R.string.won, currency.format(value))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

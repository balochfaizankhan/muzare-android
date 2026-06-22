package com.example.labourattendance

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.*

class AdvanceActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var autoCompleteLabour: AutoCompleteTextView
    private lateinit var imageViewClearLabour: ImageView
    private lateinit var editTextAmount: EditText
    private lateinit var editTextDescription: EditText
    private lateinit var buttonSelectDate: Button
    private lateinit var buttonSave: Button
    private lateinit var advanceListContainer: LinearLayout
    private lateinit var textViewHistoryTitle: TextView
    private lateinit var spinnerFundSource: Spinner
    
    private val calendar = Calendar.getInstance()
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var selectedLabourId: Int = -1
    private var fundSources: List<DatabaseHelper.FundSource> = emptyList()

    private fun getUiDateFormat(): SimpleDateFormat {
        val lang = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE).getString("My_Lang", "en")
        return if (lang == "en") {
            SimpleDateFormat("dd-MM-yyyy", Locale.US)
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_screen)
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val initialPaddingLeft = root.paddingLeft
        val initialPaddingTop = root.paddingTop
        val initialPaddingRight = root.paddingRight
        val initialPaddingBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom
            )
            insets
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.title_record_advance)
            setTextColor(Color.parseColor("#101828"))
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = params
        }

        val btnReport = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.btn_reports)
            setIconResource(R.drawable.ic_receipt_long)
            setBackgroundResource(R.drawable.bg_button_primary)
            setTextColor(Color.WHITE)
            iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE)
            textSize = 12f
            insetTop = 0
            insetBottom = 0
            minHeight = dpToPx(36)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                startActivity(Intent(this@AdvanceActivity, AdvanceReportActivity::class.java))
            }
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleView)
            addView(btnReport)
        }

        val subtitleView = TextView(this).apply {
            text = getString(R.string.msg_record_advance)
            setTextColor(Color.parseColor("#475467"))
            textSize = 15f
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, dpToPx(8))
            layoutParams = params
        }

        // Inflate the themed dialog-style layout for the input section
        val inputSection = layoutInflater.inflate(R.layout.dialog_record_advance, null)
        inputSection.setPadding(0, 0, 0, 0)
        
        autoCompleteLabour = inputSection.findViewById(R.id.autoCompleteLabour)
        imageViewClearLabour = inputSection.findViewById(R.id.imageViewClearLabour)
        editTextAmount = inputSection.findViewById(R.id.edit_advance_amount)
        editTextDescription = inputSection.findViewById(R.id.edit_advance_description)
        buttonSelectDate = inputSection.findViewById(R.id.button_select_date)
        spinnerFundSource = inputSection.findViewById(R.id.spinnerFundSourceAdvance)
        
        // Remove the title/message from the inflated layout since it's used as a page section here
        inputSection.findViewById<TextView>(R.id.dialog_title).visibility = View.GONE
        inputSection.findViewById<TextView>(R.id.dialog_message).visibility = View.GONE

        // Default to today's date
        buttonSelectDate.text = getUiDateFormat().format(calendar.time)
        buttonSelectDate.setBackgroundResource(R.drawable.bg_button_primary)
        buttonSelectDate.setTextColor(Color.WHITE)

        // Add a "Save" button to the input section that matches the dialogue style
        buttonSave = Button(this).apply {
            text = getString(R.string.btn_save_advance)
            setBackgroundResource(R.drawable.bg_button_primary)
            setTextColor(Color.WHITE)
            setPadding(0, dpToPx(10), 0, dpToPx(10))
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dpToPx(8), 0, dpToPx(12))
            }
        }

        textViewHistoryTitle = TextView(this).apply {
            text = getString(R.string.title_recent_history)
            textSize = 18f
            setPadding(0, dpToPx(8), 0, dpToPx(12))
            setTextColor(Color.parseColor("#101828"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        advanceListContainer = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dpToPx(16))
        }
        scrollView.addView(advanceListContainer)

        root.addView(headerLayout)
        root.addView(subtitleView)
        root.addView(inputSection)
        root.addView(buttonSave)
        root.addView(textViewHistoryTitle)
        root.addView(scrollView)

        setContentView(root)
        databaseHelper = DatabaseHelper(this)

        setupFundSourceSpinner(spinnerFundSource)
        setupLabourAutoComplete()
        
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        val isClosed = databaseHelper.isCurrentSeasonClosed()
        if (role == "viewer" || isClosed) {
            inputSection.visibility = View.GONE
            buttonSave.visibility = View.GONE
            subtitleView.visibility = View.GONE
            titleView.text = getString(R.string.btn_advance) // Rename from "Record Advance"
        }

        val passedLabourId = intent.getIntExtra("LABOUR_ID", -1)
        if (passedLabourId != -1) {
            val labour = databaseHelper.getAllLabours().find { it.id == passedLabourId }
            if (labour != null) {
                selectedLabourId = labour.id
                autoCompleteLabour.setText(labour.name)
                autoCompleteLabour.isEnabled = false
                imageViewClearLabour.visibility = View.GONE
            }
        }
        
        loadHistory()
        buttonSelectDate.setOnClickListener { showDatePicker() }
        buttonSave.setOnClickListener { saveAdvance() }
        
        imageViewClearLabour.setOnClickListener {
            autoCompleteLabour.text.clear()
            selectedLabourId = -1
            loadHistory()
        }
        
        loadHistory()
    }

    private fun setupFundSourceSpinner(spinner: Spinner) {
        fundSources = databaseHelper.getAllFundSources()
        val adapter = ArrayAdapter(this, R.layout.spinner_item, fundSources.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun setupLabourAutoComplete() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val labours = databaseHelper.getAllLabours()
        if (labours.isEmpty()) {
            Toast.makeText(this, "Add labour first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val names = labours.map { labour ->
            val isEffectiveActive = labour.endDate.isNullOrBlank() || labour.endDate!! >= todayStr
            if (isEffectiveActive) labour.name else "${labour.name} (${getString(R.string.status_inactive)})"
        }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, names)
        autoCompleteLabour.setAdapter(adapter)
        autoCompleteLabour.threshold = 1

        val preselectedId = intent.getIntExtra("LABOUR_ID", -1)
        if (preselectedId != -1) {
            val lab = labours.find { it.id == preselectedId }
            if (lab != null) {
                autoCompleteLabour.setText(lab.name, false)
                selectedLabourId = lab.id
                loadHistory()
            }
        }

        autoCompleteLabour.setOnItemClickListener { _, _, position, _ ->
            val displayName = autoCompleteLabour.adapter.getItem(position) as String
            // Find labour by checking if the name matches or the formatted name matches
            val labour = labours.find { labour ->
                val isEffectiveActive = labour.endDate.isNullOrBlank() || labour.endDate!! >= todayStr
                val formattedName = if (isEffectiveActive) labour.name else "${labour.name} (${getString(R.string.status_inactive)})"
                formattedName == displayName
            }
            if (labour != null) {
                selectedLabourId = labour.id
                loadHistory()
            }
        }

        autoCompleteLabour.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    imageViewClearLabour.visibility = View.GONE
                    selectedLabourId = -1
                    loadHistory()
                } else {
                    imageViewClearLabour.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, y, m, d ->
            calendar.set(y, m, d)
            buttonSelectDate.text = getUiDateFormat().format(calendar.time)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveAdvance() {
        val amountStr = editTextAmount.text.toString()
        if (amountStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_enter_amount), Toast.LENGTH_SHORT).show()
            return
        }
        val amount = amountStr.toDouble()
        val desc = editTextDescription.text.toString()
        val date = dbDateFormat.format(calendar.time)
        val sourceId = if (fundSources.isNotEmpty()) fundSources[spinnerFundSource.selectedItemPosition].id else 0

        databaseHelper.addAdvance(selectedLabourId, amount, date, desc, sourceId)
        editTextAmount.text.clear()
        editTextDescription.text.clear()
        Toast.makeText(this, getString(R.string.msg_advance_saved), Toast.LENGTH_SHORT).show()
        loadHistory()
    }

    private fun loadHistory() {
        advanceListContainer.removeAllViews()
        
        val history = databaseHelper.getAllAdvances()
        var totalAmount = 0.0
        history.forEach { totalAmount += it.amount }
        
        textViewHistoryTitle.text = "${getString(R.string.title_recent_history)} (${getString(R.string.report_sar)} ${totalAmount.toInt()})"

        history.forEach { record ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
                setBackgroundResource(R.drawable.bg_card)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dpToPx(8)
                layoutParams = lp
            }

            // Row 1: Name and Source Chip
            val row1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameTv = TextView(this).apply {
                text = record.labourName ?: "Unknown"
                setTextColor(Color.parseColor("#101828"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val sourceChip = TextView(this).apply {
                if (!record.sourceName.isNullOrEmpty()) {
                    text = record.sourceName
                    textSize = 10f
                    setTextColor(Color.parseColor("#475467"))
                    setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
                    setBackgroundResource(R.drawable.bg_badge) // Assuming this is the light gray badge
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F2F4F7"))
                } else {
                    visibility = View.GONE
                }
            }

            row1.addView(nameTv)
            row1.addView(sourceChip)

            // Row 2: Date, Amount, Edit, Delete
            val row2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(4), 0, 0)
            }

            val parsedDate = dbDateFormat.parse(record.date)
            val displayDate = if (parsedDate != null) getUiDateFormat().format(parsedDate) else record.date

            val dateTv = TextView(this).apply {
                text = displayDate
                setTextColor(Color.parseColor("#667085"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amountTv = TextView(this).apply {
                text = "SAR ${record.amount.toInt()}"
                setTextColor(Color.parseColor("#101828"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 14f
                setPadding(0, 0, dpToPx(12), 0)
            }

            row2.addView(dateTv)
            row2.addView(amountTv)

            val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
            val isClosed = databaseHelper.isCurrentSeasonClosed()
            
            if (role != "viewer" && !isClosed) {
                // Edit Button (Icon)
                val editTv = TextView(this).apply {
                    text = "✏️"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
                    setBackgroundResource(TypedValue().apply { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, this, true) }.resourceId)
                    setOnClickListener { showEditAdvanceDialog(record) }
                }

                val delTv = TextView(this).apply {
                    text = "✕"
                    textSize = 18f
                    setTextColor(Color.parseColor("#F04438"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
                    setBackgroundResource(TypedValue().apply { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, this, true) }.resourceId)
                    setOnClickListener {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@AdvanceActivity)
                            .setTitle(R.string.title_remove_record)
                            .setMessage(R.string.msg_confirm_delete_advance)
                            .setPositiveButton(R.string.btn_remove) { _, _ ->
                                databaseHelper.deleteAdvance(record.id)
                                loadHistory()
                            }
                            .setNegativeButton(R.string.btn_cancel, null)
                            .show()
                    }
                }
                row2.addView(editTv)
                row2.addView(delTv)
            }

            card.addView(row1)
            card.addView(row2)

            // Row 3: Description
            if (!record.description.isNullOrEmpty()) {
                val descTv = TextView(this).apply {
                    text = record.description
                    setTextColor(Color.parseColor("#475467"))
                    textSize = 13f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dpToPx(6), 0, 0)
                }
                card.addView(descTv)
            }
            
            advanceListContainer.addView(card)
        }
    }

    private fun showEditAdvanceDialog(record: DatabaseHelper.AdvanceRecord) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_record_advance, null)
        val title = dialogView.findViewById<TextView>(R.id.dialog_title)
        val msg = dialogView.findViewById<TextView>(R.id.dialog_message)
        val editAmount = dialogView.findViewById<EditText>(R.id.edit_advance_amount)
        val editDesc = dialogView.findViewById<EditText>(R.id.edit_advance_description)
        val btnDate = dialogView.findViewById<Button>(R.id.button_select_date)
        val editSpinner = dialogView.findViewById<Spinner>(R.id.spinnerFundSourceAdvance)

        title.text = getString(R.string.title_update_record)
        msg.text = record.labourName
        msg.setTextColor(Color.parseColor("#002B4E"))
        msg.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        
        setupFundSourceSpinner(editSpinner)
        val sourceIndex = fundSources.indexOfFirst { it.id == record.sourceId }
        if (sourceIndex != -1) editSpinner.setSelection(sourceIndex)

        // Find containers to adjust for edit mode
        val containerLabourSearch = dialogView.findViewById<View>(R.id.containerLabourSearch)
        val viewSpacerLabourAmount = dialogView.findViewById<View>(R.id.viewSpacerLabourAmount)
        val containerAmount = dialogView.findViewById<LinearLayout>(R.id.containerAmount)

        // Remove labour search and spacer to make room for full-width amount
        (containerLabourSearch?.parent as? ViewGroup)?.removeView(containerLabourSearch)
        (viewSpacerLabourAmount?.parent as? ViewGroup)?.removeView(viewSpacerLabourAmount)
        
        // Make amount container take full width
        containerAmount?.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        
        editAmount.setText(record.amount.toInt().toString())
        editDesc.setText(record.description)
        
        val editCalendar = Calendar.getInstance()
        val parsedDate = dbDateFormat.parse(record.date)
        if (parsedDate != null) editCalendar.time = parsedDate
        
        btnDate.text = getUiDateFormat().format(editCalendar.time)
        btnDate.setTextColor(Color.WHITE)
        btnDate.setBackgroundResource(R.drawable.bg_button_primary)
        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                editCalendar.set(y, m, d)
                btnDate.text = getUiDateFormat().format(editCalendar.time)
            }, editCalendar.get(Calendar.YEAR), editCalendar.get(Calendar.MONTH), editCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                val newAmount = editAmount.text.toString().toDoubleOrNull() ?: record.amount
                val newDesc = editDesc.text.toString()
                val newDate = dbDateFormat.format(editCalendar.time)
                val newSourceId = if (fundSources.isNotEmpty()) fundSources[editSpinner.selectedItemPosition].id else record.sourceId
                databaseHelper.updateAdvance(record.id, newAmount, newDate, newDesc, newSourceId)
                loadHistory()
                Toast.makeText(this, R.string.toast_update_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun loadLocale() {
        val prefs = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
        val lang = prefs.getString("My_Lang", "en") ?: "en"
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        if (lang == "ar" || lang == "ur") {
            config.setLayoutDirection(locale)
        } else {
            config.setLayoutDirection(java.util.Locale.ENGLISH)
        }
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
}

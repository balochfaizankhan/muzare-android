package com.example.labourattendance

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AttendanceActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var textViewSelectedDate: TextView
    private lateinit var editTextSearchLabour: EditText
    private lateinit var buttonSearch: Button
    private lateinit var buttonSelectDate: Button
    private lateinit var buttonOpenReport: Button
    private lateinit var imageViewClearSearch: android.widget.ImageView
    private lateinit var spinnerGroupFilter: android.widget.Spinner
    private lateinit var labourListContainer: LinearLayout
    private lateinit var textViewSummaryP: TextView
    private lateinit var textViewSummaryH: TextView
    private lateinit var textViewSummaryA: TextView
    private var currentGroupId: Int = 0 // 0 means all groups
    private val calendar: Calendar = Calendar.getInstance()
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var currentSearchQuery: String = ""

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
        setContentView(R.layout.activity_attendance)

        val root = findViewById<View>(R.id.attendanceRoot)
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

        databaseHelper = DatabaseHelper(this)

        textViewSelectedDate = findViewById(R.id.textViewSelectedDate)
        editTextSearchLabour = findViewById(R.id.editTextSearchLabour)
        buttonSelectDate = findViewById(R.id.buttonSelectDate)
        buttonOpenReport = findViewById(R.id.buttonOpenReport)
        spinnerGroupFilter = findViewById(R.id.spinnerGroupFilter)
        labourListContainer = findViewById(R.id.labourListContainer)
        imageViewClearSearch = findViewById(R.id.imageViewClearSearch)
        textViewSummaryP = findViewById(R.id.textViewSummaryP)
        textViewSummaryH = findViewById(R.id.textViewSummaryH)
        textViewSummaryA = findViewById(R.id.textViewSummaryA)

        setupGroupSpinner()
        updateSelectedDateText()
        loadAttendanceRows()

        imageViewClearSearch.setOnClickListener {
            editTextSearchLabour.text.clear()
            currentSearchQuery = ""
            loadAttendanceRows()
        }

        editTextSearchLabour.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    imageViewClearSearch.visibility = android.view.View.GONE
                    currentSearchQuery = ""
                } else {
                    imageViewClearSearch.visibility = android.view.View.VISIBLE
                    currentSearchQuery = s.toString().trim()
                }
                loadAttendanceRows()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadAttendanceRows()
    }

    private fun setupGroupSpinner() {
        val groups = databaseHelper.getAllGroups().toMutableList()
        val groupNames = mutableListOf(getString(R.string.all_groups))
        groupNames.addAll(groups.map { 
            if (it.id == 1) getString(R.string.group_general) else it.name 
        })

        val adapter = android.widget.ArrayAdapter(this, R.layout.spinner_item, groupNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGroupFilter.adapter = adapter

        spinnerGroupFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentGroupId = if (position == 0) 0 else groups[position - 1].id
                loadAttendanceRows()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        buttonSelectDate.setOnClickListener { showDatePicker() }
        buttonOpenReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                updateSelectedDateText()
                loadAttendanceRows()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun updateSelectedDateText() {
        textViewSelectedDate.text = "${getString(R.string.label_date, getUiDateFormat().format(calendar.time))}"
    }

    private fun loadAttendanceRows() {
        if (!::labourListContainer.isInitialized) return
        labourListContainer.removeAllViews()
        val selectedDate = dbDateFormat.format(calendar.time)
        
        // Calculate yesterday's date
        val yesterdayCal = calendar.clone() as Calendar
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayDate = dbDateFormat.format(yesterdayCal.time)

        var countP = 0
        var countH = 0
        var countA = 0

        val allLabours = databaseHelper.getActiveLaboursForDate(selectedDate, currentGroupId)
        val labourList = allLabours.filter {
            val matchesSearch = currentSearchQuery.isEmpty() || it.name.contains(currentSearchQuery, ignoreCase = true)
            val isDailyWager = it.labourType == "DAILY_WAGE"
            matchesSearch && isDailyWager
        }

        if (labourList.isEmpty()) {
            updateSummary(0, 0, 0)
            val emptyTextView = TextView(this).apply {
                text = if (currentSearchQuery.isEmpty()) getString(R.string.no_labour_found) else getString(R.string.msg_no_match_search)
                textSize = 18f
                setTextColor(Color.parseColor("#243B53"))
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundResource(R.drawable.bg_card)
            }
            labourListContainer.addView(emptyTextView)
            return
        }

        labourList.forEachIndexed { index, labour ->
            val currentStatus = databaseHelper.getAttendanceStatus(labour.id, selectedDate)
            val lastStatus = databaseHelper.getAttendanceStatus(labour.id, yesterdayDate)
            
            when (currentStatus) {
                "P" -> countP++
                "H" -> countH++
                "A" -> countA++
            }

            val rowLayout = createRowLayout()
            val serialTextView = createSerialTextView(index + 1)
            
            val nameContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dpToPx(8)
                }
            }
            
            val nameTextView = createNameTextView(labour.name)
            
            val lastStatusTextView = TextView(this).apply {
                val statusText = when(lastStatus) {
                    "P" -> getString(R.string.yesterday_label, getString(R.string.status_p))
                    "H" -> getString(R.string.yesterday_label, getString(R.string.status_h))
                    "A" -> getString(R.string.yesterday_label, getString(R.string.status_a))
                    else -> getString(R.string.yesterday_label, getString(R.string.status_none))
                }
                text = statusText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#475467"))
            }
            
            nameContainer.addView(nameTextView)
            nameContainer.addView(lastStatusTextView)

            val presentButton = createStatusButton(getString(R.string.status_p))
            val halfButton = createStatusButton(getString(R.string.status_h))
            val absentButton = createStatusButton(getString(R.string.status_a))

            updateButtonStyles(presentButton, halfButton, absentButton, currentStatus)

            val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
            val isClosed = databaseHelper.isCurrentSeasonClosed()
            
            if (role != "viewer" && !isClosed) {
                presentButton.setOnClickListener {
                    if (currentStatus == "P") {
                        showUnmarkConfirmation(labour.name, getString(R.string.status_present)) {
                            databaseHelper.deleteAttendance(labour.id, selectedDate)
                            loadAttendanceRows()
                        }
                    } else {
                        databaseHelper.saveAttendance(labour.id, selectedDate, "P")
                        updateButtonStyles(presentButton, halfButton, absentButton, "P")
                        loadAttendanceRows() // Refresh to update currentStatus for next click
                    }
                }

                halfButton.setOnClickListener {
                    if (currentStatus == "H") {
                        showUnmarkConfirmation(labour.name, getString(R.string.status_half)) {
                            databaseHelper.deleteAttendance(labour.id, selectedDate)
                            loadAttendanceRows()
                        }
                    } else {
                        databaseHelper.saveAttendance(labour.id, selectedDate, "H")
                        updateButtonStyles(presentButton, halfButton, absentButton, "H")
                        loadAttendanceRows()
                    }
                }

                absentButton.setOnClickListener {
                    if (currentStatus == "A") {
                        showUnmarkConfirmation(labour.name, getString(R.string.status_absent)) {
                            databaseHelper.deleteAttendance(labour.id, selectedDate)
                            loadAttendanceRows()
                        }
                    } else {
                        databaseHelper.saveAttendance(labour.id, selectedDate, "A")
                        updateButtonStyles(presentButton, halfButton, absentButton, "A")
                        loadAttendanceRows()
                    }
                }
            } else {
                presentButton.isEnabled = false
                halfButton.isEnabled = false
                absentButton.isEnabled = false
            }

            rowLayout.addView(serialTextView)
            rowLayout.addView(nameContainer)
            rowLayout.addView(presentButton)
            rowLayout.addView(halfButton)
            rowLayout.addView(absentButton)
            labourListContainer.addView(rowLayout)
        }
        updateSummary(countP, countH, countA)
    }

    private fun updateSummary(p: Int, h: Int, a: Int) {
        textViewSummaryP.text = getString(R.string.summary_p_count, p)
        textViewSummaryH.text = getString(R.string.summary_h_count, h)
        textViewSummaryA.text = getString(R.string.summary_a_count, a)
    }

    private fun createRowLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
            setBackgroundResource(R.drawable.bg_card)
            elevation = dpToPx(1).toFloat()
        }
    }

    private fun createSerialTextView(number: Int): TextView {
        return TextView(this).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundResource(R.drawable.bg_badge)
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                marginEnd = dpToPx(12)
            }
        }
    }

    private fun createNameTextView(name: String): TextView {
        return TextView(this).apply {
            text = name
            setTextColor(Color.parseColor("#101828"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            // Removed internal layoutParams since it's added to nameContainer
        }
    }

    private fun createStatusButton(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 13f
            setTextColor(Color.WHITE)
            minHeight = dpToPx(42)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0.28f
            ).apply {
                marginStart = dpToPx(4)
            }
            setBackgroundResource(
                when (textValue) {
                    "P" -> R.drawable.bg_button_success
                    "1/2" -> R.drawable.bg_button_neutral
                    "A" -> R.drawable.bg_button_danger
                    else -> R.drawable.bg_button_ghost
                }
            )
        }
    }

    private fun updateButtonStyles(
        presentButton: Button,
        halfButton: Button,
        absentButton: Button,
        status: String?
    ) {
        val greenColor = Color.parseColor("#12B76A")
        val yellowColor = Color.parseColor("#FDB022")
        val redColor = Color.parseColor("#F04438")
        val darkText = Color.parseColor("#101828")

        // P button (Present)
        if (status == "P") {
            presentButton.setBackgroundResource(R.drawable.bg_button_success)
            presentButton.setTextColor(Color.WHITE)
            presentButton.setTypeface(null, Typeface.BOLD)
            presentButton.alpha = 1.0f
        } else {
            presentButton.setBackgroundResource(R.drawable.bg_button_ghost)
            presentButton.setTextColor(greenColor)
            presentButton.setTypeface(null, Typeface.NORMAL)
            presentButton.alpha = 0.5f
        }

        // 1/2 button (Half Day)
        if (status == "1/2" || status == "H") {
            halfButton.setBackgroundResource(R.drawable.bg_button_neutral)
            halfButton.setTextColor(Color.WHITE) // Changed to white as requested
            halfButton.setTypeface(null, Typeface.BOLD)
            halfButton.alpha = 1.0f
        } else {
            halfButton.setBackgroundResource(R.drawable.bg_button_ghost)
            halfButton.setTextColor(yellowColor)
            halfButton.setTypeface(null, Typeface.NORMAL)
            halfButton.alpha = 0.5f
        }

        // A button (Absent)
        if (status == "A") {
            absentButton.setBackgroundResource(R.drawable.bg_button_danger)
            absentButton.setTextColor(Color.WHITE)
            absentButton.setTypeface(null, Typeface.BOLD)
            absentButton.alpha = 1.0f
        } else {
            absentButton.setBackgroundResource(R.drawable.bg_button_ghost)
            absentButton.setTextColor(redColor)
            absentButton.setTypeface(null, Typeface.NORMAL)
            absentButton.alpha = 0.5f
        }
    }

    private fun showUnmarkConfirmation(name: String, status: String, onConfirm: () -> Unit) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_unmark)
            .setMessage(getString(R.string.msg_unmark, status, name))
            .setPositiveButton(R.string.btn_unmark) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showConfirmationDialog(name: String, status: String, onConfirm: () -> Unit) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.title_confirm))
            .setMessage(getString(R.string.msg_confirm_attendance, name, status))
            .setPositiveButton(R.string.btn_confirm) { _, _ -> onConfirm() }
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

    private fun dpToPx(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}

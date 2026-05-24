package com.example.labourattendance

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.text.htmlEncode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var editTextSearchLabour: EditText
    private lateinit var imageViewClearSearch: android.widget.ImageView
    private lateinit var tvSummaryPresent: TextView
    private lateinit var tvSummaryHalf: TextView
    private lateinit var tvSummaryAbsent: TextView
    private lateinit var tvSummaryAdvance: TextView
    private lateinit var labelSummaryPresent: TextView
    private lateinit var labelSummaryHalf: TextView
    private lateinit var labelSummaryAbsent: TextView
    private lateinit var labelSummaryAdvance: TextView
    private lateinit var cardSummaryPresent: com.google.android.material.card.MaterialCardView
    private lateinit var cardSummaryHalf: com.google.android.material.card.MaterialCardView
    private lateinit var cardSummaryAbsent: com.google.android.material.card.MaterialCardView
    private lateinit var cardSummaryAdvance: com.google.android.material.card.MaterialCardView
    private lateinit var buttonCustomizeSummary: android.widget.ImageButton
    private lateinit var buttonSelectFromDate: Button
    private lateinit var buttonSelectToDate: Button
    private lateinit var buttonViewReport: Button
    private lateinit var buttonExportReport: Button
    private lateinit var spinnerGroupFilter: android.widget.Spinner
    private lateinit var spinnerTypeFilter: android.widget.Spinner
    private lateinit var reportContainer: LinearLayout

    private val fromCalendar: Calendar = Calendar.getInstance()
    private val toCalendar: Calendar = Calendar.getInstance()
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var lastReportHtml: String? = null
    private var lastPdfLines: List<String> = emptyList()
    private var lastReportData: String? = null // For CSV
    private var currentSearchQuery: String = ""
    private var currentGroupId: Int = 0 // 0 means all groups
    private var currentType: String = "ALL" // "ALL" means all types

    private val availableMetrics by lazy {
        listOf(
            Metric(0, getString(R.string.metric_present), getString(R.string.status_present), "#12B76A"),
            Metric(1, getString(R.string.metric_half), getString(R.string.status_half), "#FDB022"),
            Metric(2, getString(R.string.metric_absent), getString(R.string.status_absent), "#F04438"),
            Metric(3, getString(R.string.metric_advance), getString(R.string.report_adv), "#101828"),
            Metric(4, getString(R.string.metric_total_labours), getString(R.string.metric_total_labours), "#101828"),
            Metric(5, getString(R.string.metric_active_labours), getString(R.string.status_active), "#12B76A"),
            Metric(6, getString(R.string.metric_inactive_labours), getString(R.string.status_inactive), "#F04438")
        )
    }

    data class Metric(val id: Int, val name: String, val label: String, val color: String)

    private fun getUiDateFormat(): SimpleDateFormat {
        val lang = getSharedPreferences("Settings", MODE_PRIVATE).getString("My_Lang", "en")
        return if (lang == "en") {
            SimpleDateFormat("dd-MM-yyyy", Locale.US)
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }

    private val exportPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) exportPdf(uri)
    }

    private val exportCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) exportCsv(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val root = findViewById<View>(R.id.reportRoot)
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

        editTextSearchLabour = findViewById(R.id.editTextSearchLabour)
        imageViewClearSearch = findViewById(R.id.imageViewClearSearch)
        tvSummaryPresent = findViewById(R.id.tvSummaryPresent)
        tvSummaryHalf = findViewById(R.id.tvSummaryHalf)
        tvSummaryAbsent = findViewById(R.id.tvSummaryAbsent)
        tvSummaryAdvance = findViewById(R.id.tvSummaryAdvance)
        labelSummaryPresent = findViewById(R.id.labelSummaryPresent)
        labelSummaryHalf = findViewById(R.id.labelSummaryHalf)
        labelSummaryAbsent = findViewById(R.id.labelSummaryAbsent)
        labelSummaryAdvance = findViewById(R.id.labelSummaryAdvance)
        cardSummaryPresent = findViewById(R.id.cardSummaryPresent)
        cardSummaryHalf = findViewById(R.id.cardSummaryHalf)
        cardSummaryAbsent = findViewById(R.id.cardSummaryAbsent)
        cardSummaryAdvance = findViewById(R.id.cardSummaryAdvance)
        buttonCustomizeSummary = findViewById(R.id.buttonCustomizeSummary)
        buttonSelectFromDate = findViewById(R.id.buttonSelectFromDate)
        buttonSelectToDate = findViewById(R.id.buttonSelectToDate)
        buttonViewReport = findViewById(R.id.buttonViewReport)
        buttonExportReport = findViewById(R.id.buttonExportReport)
        spinnerGroupFilter = findViewById(R.id.spinnerGroupFilter)
        spinnerTypeFilter = findViewById(R.id.spinnerTypeFilter)
        reportContainer = findViewById(R.id.reportContainer)

        buttonCustomizeSummary.setOnClickListener { showCustomizeSummaryDialog() }

        setupFilterSpinners()
        updateDateLabels()

        imageViewClearSearch.setOnClickListener {
            editTextSearchLabour.text.clear()
            currentSearchQuery = ""
            loadReport()
        }

        editTextSearchLabour.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    imageViewClearSearch.visibility = android.view.View.GONE
                    currentSearchQuery = ""
                    loadReport()
                } else {
                    imageViewClearSearch.visibility = android.view.View.VISIBLE
                    currentSearchQuery = s.toString().trim()
                    loadReport()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun showCustomizeSummaryDialog() {
        val metricNames = availableMetrics.map { it.name }.toTypedArray()
        val selectedIndices = getSelectedMetricIndices()
        val checkedItems = BooleanArray(availableMetrics.size) { selectedIndices.contains(it) }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.title_customize_summary)
            .setMultiChoiceItems(metricNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.btn_apply) { _, _ ->
                val newSelection = checkedItems.indices.filter { checkedItems[it] }
                if (newSelection.size == 4) {
                    saveSelectedMetricIndices(newSelection)
                    loadReport()
                } else {
                    Toast.makeText(this, R.string.err_max_4, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun getSelectedMetricIndices(): List<Int> {
        val prefs = getSharedPreferences("ReportSettings", MODE_PRIVATE)
        val saved = prefs.getString("selected_metrics", "0,1,2,3") ?: "0,1,2,3"
        return saved.split(",").mapNotNull { it.toIntOrNull() }.take(4)
    }

    private fun saveSelectedMetricIndices(indices: List<Int>) {
        val prefs = getSharedPreferences("ReportSettings", MODE_PRIVATE)
        prefs.edit().putString("selected_metrics", indices.joinToString(",")).apply()
    }

    private fun updateSummaryWidgets(
        totalPresent: Int,
        totalHalf: Int,
        totalAbsent: Int,
        totalAdvance: Double,
        totalLabours: Int,
        activeLabours: Int,
        inactiveLabours: Int
    ) {
        val selectedIndices = getSelectedMetricIndices()
        val widgets = listOf(
            Pair(labelSummaryPresent, tvSummaryPresent),
            Pair(labelSummaryHalf, tvSummaryHalf),
            Pair(labelSummaryAbsent, tvSummaryAbsent),
            Pair(labelSummaryAdvance, tvSummaryAdvance)
        )

        selectedIndices.forEachIndexed { i, metricIndex ->
            if (i < widgets.size) {
                val metric = availableMetrics[metricIndex]
                val (labelView, valueView) = widgets[i]
                labelView.text = metric.label
                valueView.setTextColor(metric.color.toColorInt())
                
                valueView.text = when (metricIndex) {
                    0 -> totalPresent.toString()
                    1 -> totalHalf.toString()
                    2 -> totalAbsent.toString()
                    3 -> totalAdvance.toInt().toString()
                    4 -> totalLabours.toString()
                    5 -> activeLabours.toString()
                    6 -> inactiveLabours.toString()
                    else -> "-"
                }
            }
        }
    }

    private fun setupFilterSpinners() {
        // Group Spinner
        val groups = databaseHelper.getAllGroups()
        val groupNames = mutableListOf(getString(R.string.all_groups))
        groupNames.addAll(groups.map { if (it.id == 1) getString(R.string.group_general) else it.name })

        val groupAdapter = android.widget.ArrayAdapter(this, R.layout.spinner_item, groupNames)
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGroupFilter.adapter = groupAdapter

        spinnerGroupFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentGroupId = if (position == 0) 0 else groups[position - 1].id
                loadReport()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Type Spinner
        val typeOptions = listOf("ALL", "DAILY_WAGE", "CONTRACT", "PRODUCTION_BASED", "MONTHLY")
        val typeDisplayNames = listOf(
            getString(R.string.all_types),
            getString(R.string.labour_type_daily),
            getString(R.string.labour_type_contract),
            getString(R.string.labour_type_production),
            getString(R.string.labour_type_monthly)
        )

        val typeAdapter = android.widget.ArrayAdapter(this, R.layout.spinner_item, typeDisplayNames)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTypeFilter.adapter = typeAdapter

        spinnerTypeFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentType = typeOptions[position]
                loadReport()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        buttonSelectFromDate.setOnClickListener { showDatePicker(fromCalendar) }
        buttonSelectToDate.setOnClickListener { showDatePicker(toCalendar) }
        buttonViewReport.setOnClickListener { loadReport() }
        
        buttonExportReport.setOnClickListener { view ->
            if (lastReportHtml == null) {
                Toast.makeText(this, "Please generate a report first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showExportMenu(view)
        }
    }

    private fun showExportMenu(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.btn_print))
        popup.menu.add(0, 2, 1, getString(R.string.btn_export_pdf))
        popup.menu.add(0, 3, 2, getString(R.string.btn_export_csv))
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    lastReportHtml?.let { printReport(it) }
                    true
                }
                2 -> {
                    exportPdfLauncher.launch("attendance_report_${System.currentTimeMillis()}.pdf")
                    true
                }
                3 -> {
                    exportCsvLauncher.launch("attendance_report_${System.currentTimeMillis()}.csv")
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDatePicker(calendar: Calendar) {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                updateDateLabels()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun updateDateLabels() {
        buttonSelectFromDate.text = getUiDateFormat().format(fromCalendar.time)
        buttonSelectToDate.text = getUiDateFormat().format(toCalendar.time)
    }

    private fun loadReport() {
        if (fromCalendar.after(toCalendar)) {
            Toast.makeText(this, "From date cannot be after To date", Toast.LENGTH_SHORT).show()
            return
        }

        reportContainer.removeAllViews()

        val fromDate = dbDateFormat.format(fromCalendar.time)
        val toDate = dbDateFormat.format(toCalendar.time)

        val allLabours = if (currentGroupId == 0) databaseHelper.getAllLabours() else databaseHelper.getLaboursByGroup(currentGroupId)
        
        val dateList = buildDateList(fromDate, toDate)
        val attendanceEntries = databaseHelper.getAttendanceEntries(fromDate, toDate)
        val advances = databaseHelper.getAdvancesInRange(fromDate, toDate)
        
        val statusMap = mutableMapOf<Int, MutableMap<String, String>>()
        val advanceMap = mutableMapOf<Int, MutableMap<String, Double>>()
        val dailyPresentCounts = mutableMapOf<String, Double>()

        for (entry in attendanceEntries) {
            val labourStatusMap = statusMap.getOrPut(entry.labourId) { mutableMapOf() }
            labourStatusMap[entry.date] = entry.status
            val statusValue = when (entry.status) {
                "P" -> 1.0
                "H" -> 0.5
                else -> 0.0
            }
            if (statusValue > 0) {
                dailyPresentCounts[entry.date] = (dailyPresentCounts[entry.date] ?: 0.0) + statusValue
            }
        }
        
        for (adv in advances) {
            val labourAdvanceMap = advanceMap.getOrPut(adv.labourId) { mutableMapOf() }
            labourAdvanceMap[adv.date] = (labourAdvanceMap[adv.date] ?: 0.0) + adv.amount
        }

        // Filter: Show labour if they were active during period OR have data (attendance/advances)
        val filteredLabourList = allLabours.filter { labour ->
            val hasData = statusMap.containsKey(labour.id) || advanceMap.containsKey(labour.id)
            val wasActiveDuringPeriod = (labour.joinDate.isNullOrBlank() || labour.joinDate!! <= toDate) && (labour.endDate.isNullOrBlank() || labour.endDate!! >= fromDate)
            
            val matchesSearch = currentSearchQuery.isEmpty() || labour.name.contains(currentSearchQuery, ignoreCase = true)
            val matchesType = currentType == "ALL" || labour.labourType == currentType
            
            matchesSearch && matchesType && (hasData || wasActiveDuringPeriod)
        }

        if (filteredLabourList.isEmpty()) {
            tvSummaryPresent.text = "0"
            tvSummaryHalf.text = "0"
            tvSummaryAbsent.text = "0"
            tvSummaryAdvance.text = "0"
            lastReportHtml = null
            lastPdfLines = emptyList()
            addEmptyMessage(
                if (currentSearchQuery.isEmpty()) {
                    "No labour data found for the selected period."
                } else {
                    "No labour matches your search."
                }
            )
            return
        }

        var totalPresent = 0
        var totalHalf = 0
        var totalAbsent = 0
        var totalAdvance = 0.0
        val pdfLines = mutableListOf(
            "Labour Attendance Report",
            "From: ${getUiDateFormat().format(fromCalendar.time)}",
            "To: ${getUiDateFormat().format(toCalendar.time)}",
            ""
        )

        filteredLabourList.forEachIndexed { index, labour ->
            val labourStatuses = statusMap[labour.id].orEmpty()
            val labourAdvances = advanceMap[labour.id].orEmpty()
            
            val pCount = labourStatuses.values.count { it == "P" }
            val hCount = labourStatuses.values.count { it == "H" }
            val aCount = labourStatuses.values.count { it == "A" }
            val labourTotalAdvance = labourAdvances.values.sum()
            
            totalPresent += pCount
            totalHalf += hCount
            totalAbsent += aCount
            totalAdvance += labourTotalAdvance

            val reportCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(10)
                }
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val serialView = createSerialTextView(index + 1)
            val nameView = TextView(this).apply {
                text = labour.name
                textSize = 18f
                setTextColor("#243B53".toColorInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val summaryView = TextView(this).apply {
                text = getString(R.string.report_individual_summary, pCount, hCount, aCount, labourTotalAdvance.toInt())
                textSize = 14f
                setTextColor("#243B53".toColorInt())
                setPadding(0, dpToPx(10), 0, dpToPx(6))
            }

            val statusLine = buildStatusLine(dateList, labourStatuses, labourAdvances)
            val statusView = TextView(this).apply {
                text = statusLine
                textSize = 13f
                setTextColor("#486581".toColorInt())
            }

            headerRow.addView(serialView)
            headerRow.addView(nameView)
            reportCard.addView(headerRow)
            reportCard.addView(summaryView)
            reportCard.addView(statusView)
            reportContainer.addView(reportCard)

            pdfLines.add("${index + 1}. ${labour.name}")
            pdfLines.add("P: $pCount  1/2: $hCount  A: $aCount" + 
                (if (labourTotalAdvance > 0) "  ${getString(R.string.report_adv)}: ${getString(R.string.report_sar)} ${labourTotalAdvance.toInt()}" else ""))
            pdfLines.add(statusLine)
            pdfLines.add("")
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val totalLaboursCount = filteredLabourList.size
        val activeLaboursCount = filteredLabourList.count { it.endDate.isNullOrBlank() || it.endDate!! >= todayStr }
        val inactiveLaboursCount = totalLaboursCount - activeLaboursCount

        updateSummaryWidgets(totalPresent, totalHalf, totalAbsent, totalAdvance, totalLaboursCount, activeLaboursCount, inactiveLaboursCount)

        pdfLines.add("${getString(R.string.status_present)}: $totalPresent")
        pdfLines.add("${getString(R.string.status_half)}: $totalHalf")
        pdfLines.add("${getString(R.string.status_absent)}: $totalAbsent")
        if (totalAdvance > 0) pdfLines.add("${getString(R.string.report_advance_total)} ${getString(R.string.report_sar)} ${totalAdvance.toInt()}")

        lastPdfLines = pdfLines
        lastReportHtml = buildReportHtml(filteredLabourList, dateList, statusMap, advanceMap, totalPresent, totalHalf, totalAbsent, totalAdvance.toInt().toDouble(), dailyPresentCounts)
        lastReportData = buildReportCsv(filteredLabourList, dateList, statusMap, advanceMap, totalPresent, totalHalf, totalAbsent, totalAdvance)
    }

    private fun buildReportCsv(
        labourList: List<DatabaseHelper.Labour>,
        dateList: List<String>,
        statusMap: Map<Int, Map<String, String>>,
        advanceMap: Map<Int, Map<String, Double>>,
        totalPresent: Int,
        totalHalf: Int,
        totalAbsent: Int,
        totalAdvance: Double
    ): String {
        val sb = StringBuilder()
        sb.append("Labour Attendance Report\n")
        sb.append("From:,${dbDateFormat.format(fromCalendar.time)},To:,${dbDateFormat.format(toCalendar.time)}\n\n")
        
        val dateCols = dateList.joinToString(",")
        sb.append("#,Labour Name,P,1/2,A,Adv (SAR),$dateCols\n")

        labourList.forEachIndexed { index, labour ->
            val statuses = statusMap[labour.id].orEmpty()
            val advances = advanceMap[labour.id].orEmpty()
            
            val pCount = statuses.values.count { it == "P" }
            val hCount = statuses.values.count { it == "H" }
            val aCount = statuses.values.count { it == "A" }
            val totalAdv = advances.values.sum().toInt()
            
            val dailyStatuses = dateList.joinToString(",") { date ->
                val s = statuses[date] ?: "-"
                val a = advances[date]
                if (a != null && a > 0) "$s (Adv:${a.toInt()})" else s
            }

            sb.append("${index + 1},${labour.name},$pCount,$hCount,$aCount,$totalAdv,$dailyStatuses\n")
        }

        sb.append("\nGRAND TOTALS,,,,\n")
        sb.append(",Total P:,$totalPresent\n")
        sb.append(",Total 1/2:,$totalHalf\n")
        sb.append(",Total A:,$totalAbsent\n")
        sb.append(",Total Advance:,$totalAdvance\n")

        return sb.toString()
    }

    private fun exportCsv(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(lastReportData?.toByteArray() ?: "".toByteArray())
            }
            Toast.makeText(this, "CSV exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "CSV export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportPdf(uri: Uri) {
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint().apply { textSize = 12f }
            val titlePaint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas
            var y = 40

            lastPdfLines.forEachIndexed { index, line ->
                if (y > 800) {
                    pdfDocument.finishPage(page)
                    pageNumber += 1
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40
                }

                val currentPaint = if (index == 0) titlePaint else paint
                canvas.drawText(line, 40f, y.toFloat(), currentPaint)
                y += if (index == 0) 28 else 18
            }

            pdfDocument.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { output ->
                pdfDocument.writeTo(output)
            }
            pdfDocument.close()
            Toast.makeText(this, "PDF exported", Toast.LENGTH_SHORT).show()
        } catch (exception: Exception) {
            Toast.makeText(this, "PDF export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addEmptyMessage(message: String) {
        val textView = TextView(this).apply {
            text = message
            textSize = 16f
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
            setBackgroundResource(R.drawable.bg_card)
        }
        reportContainer.addView(textView)
    }

    private fun buildStatusLine(dateList: List<String>, labourStatuses: Map<String, String>, labourAdvances: Map<String, Double>): String {
        return dateList.joinToString("   ") { date ->
            val parsedDate = dbDateFormat.parse(date)
            val displayDate = if (parsedDate != null) getUiDateFormat().format(parsedDate) else date

            val status = when (labourStatuses[date]) {
                "P" -> "P"
                "H" -> "1/2"
                "A" -> "A"
                else -> "-"
            }
            val adv = labourAdvances[date]
            if (adv != null && adv > 0) "$displayDate: $status (SAR ${adv.toInt()})" else "$displayDate: $status"
        }
    }

    private fun buildDateList(fromDate: String, toDate: String): List<String> {
        val dates = mutableListOf<String>()
        val startDate = dbDateFormat.parse(fromDate) ?: Calendar.getInstance().time
        val endDate = dbDateFormat.parse(toDate) ?: Calendar.getInstance().time
        val workingCalendar = Calendar.getInstance().apply { time = startDate }

        while (!workingCalendar.time.after(endDate)) {
            dates.add(dbDateFormat.format(workingCalendar.time))
            workingCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }

    private fun buildReportHtml(
        labourList: List<DatabaseHelper.Labour>,
        dateList: List<String>,
        statusMap: Map<Int, Map<String, String>>,
        advanceMap: Map<Int, Map<String, Double>>,
        totalPresent: Int,
        totalHalf: Int,
        totalAbsent: Int,
        totalAdvance: Double,
        dailyPresentCounts: Map<String, Double>
    ): String {
        val dateHeaders = dateList.joinToString("") { date ->
            val parsedDate = dbDateFormat.parse(date)
            val displayDate = if (parsedDate != null) {
                val lang = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE).getString("My_Lang", "en")
                if (lang == "en") SimpleDateFormat("dd/MM", Locale.US).format(parsedDate) else date.substring(5)
            } else date.substring(5)
            "<th class='col-date'>${escapeHtml(displayDate)}</th>"
        }

        val dailyPresentsRow = dateList.joinToString("") { date ->
            val count = dailyPresentCounts[date] ?: 0.0
            val displayCount = if (count % 1.0 == 0.0) count.toInt().toString() else count.toString()
            "<td class='col-date' style='background-color: #F2F4F7; font-weight: bold;'>$displayCount</td>"
        }

        val rows = labourList.mapIndexed { index, labour ->
            val labourStatuses = statusMap[labour.id].orEmpty()
            val labourAdvances = advanceMap[labour.id].orEmpty()
            
            val presentCount = labourStatuses.values.count { it == "P" }
            val halfCount = labourStatuses.values.count { it == "H" }
            val absentCount = labourStatuses.values.count { it == "A" }
            val labourTotalAdvance = labourAdvances.values.sum()
            
            val dailyStatuses = dateList.joinToString("") { date ->
                val status = labourStatuses[date]
                val statusText = when (status) {
                    "P" -> "P"
                    "H" -> "½"
                    "A" -> "A"
                    else -> "-"
                }
                
                val adv = labourAdvances[date]
                val advValue = if (adv != null && adv > 0) adv.toInt().toString() else "&nbsp;"
                
                val bgColor = when (status) {
                    "P" -> "#12B76A" // Green
                    "H" -> "#FDB022" // Yellow
                    "A" -> "#F04438" // Red
                    else -> "transparent"
                }
                val textColor = if (status == "H") "#101828" else if (status != null) "white" else "inherit"
                val borderStyle = if (status != null) "1px solid rgba(255,255,255,0.3)" else "1px solid rgba(0,0,0,0.05)"
                
                """
                <td class="col-date" style="background-color: $bgColor; color: $textColor; font-weight: bold; padding: 0;">
                    <div style="font-size: 9px; line-height: 1.2; padding-top: 2px;">$statusText</div>
                    <div style="font-size: 7px; line-height: 1.2; border-top: $borderStyle; padding-bottom: 2px;">$advValue</div>
                </td>
                """.trimIndent()
            }

            """
            <tr>
                <td>${index + 1}</td>
                <td style="text-align: left; padding-left: 12px;">${escapeHtml(labour.name)}</td>
                <td style="color: #12B76A; font-weight: bold;">$presentCount</td>
                <td style="color: #FDB022; font-weight: bold;">$halfCount</td>
                <td style="color: #F04438; font-weight: bold;">$absentCount</td>
                <td style="font-weight: bold;">${if (labourTotalAdvance > 0) "${getString(R.string.report_sar)} ${labourTotalAdvance.toInt()}" else "-"}</td>
                $dailyStatuses
            </tr>
            """.trimIndent()
        }.joinToString("")

        val isRtl = resources.configuration.layoutDirection == android.util.LayoutDirection.RTL
        val bodyDir = if (isRtl) "rtl" else "ltr"
        val displayFromDate = getUiDateFormat().format(fromCalendar.time)
        val displayToDate = getUiDateFormat().format(toCalendar.time)

        return """
            <html>
            <head>
                <style>
                    @page { size: landscape; margin: 8mm; }
                    body { font-family: sans-serif; padding: 0; color: #101828; direction: $bodyDir; }
                    h1 { color: #004EEB; text-align: center; margin-bottom: 10px; font-size: 18px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 8px; table-layout: fixed; }
                    th, td { border: 1px solid #EAECF0; padding: 2px; text-align: center; overflow: hidden; }
                    th { background-color: #F9FAFB; color: #475467; }
                    .summary-header { background-color: #F2F4F7; font-weight: bold; }
                    .col-date { width: 22px; }
                    .col-num { width: 24px; }
                    .col-name { width: 90px; text-align: left; }
                    .col-summary { width: 26px; }
                    .col-adv { width: 40px; }
                </style>
            </head>
            <body>
                <h1>${getString(R.string.report_title)}</h1>
                <div style="margin-bottom: 10px; border-bottom: 2px solid #004EEB; padding-bottom: 8px; font-size: 10px;">
                    <strong>${getString(R.string.report_period)}</strong> ${escapeHtml(displayFromDate)} ${getString(R.string.report_to)} ${escapeHtml(displayToDate)}<br>
                    <strong>${getString(R.string.report_grand_totals)}</strong> 
                    <span style="color: #12B76A;">P: $totalPresent</span> | 
                    <span style="color: #FDB022;">1/2: $totalHalf</span> | 
                    <span style="color: #F04438;">A: $totalAbsent</span> |
                    <strong>${getString(R.string.report_advance_total)} ${getString(R.string.report_sar)} ${totalAdvance.toInt()}</strong>
                </div>
                <table>
                    <tr>
                        <th rowspan="2" class="col-num">#</th>
                        <th rowspan="2" class="col-name">${getString(R.string.report_labour_name)}</th>
                        <th colspan="4">${getString(R.string.report_financial_summary)}</th>
                        <th colspan="${dateList.size}">${getString(R.string.report_daily_status)}</th>
                    </tr>
                    <tr>
                        <th style="color: #12B76A;" class="col-summary">P</th>
                        <th style="color: #FDB022;" class="col-summary">1/2</th>
                        <th style="color: #F04438;" class="col-summary">A</th>
                        <th class="col-adv">${getString(R.string.report_adv)}</th>
                        $dateHeaders
                    </tr>
                    $rows
                    <tr class="summary-header">
                        <td colspan="2">${getString(R.string.report_grand_total_row)}</td>
                        <td class="col-summary">$totalPresent</td>
                        <td class="col-summary">$totalHalf</td>
                        <td class="col-summary">$totalAbsent</td>
                        <td class="col-adv">${totalAdvance.toInt()}</td>
                        <td colspan="${dateList.size}"></td>
                    </tr>
                    <tr class="summary-header">
                        <td colspan="6">${getString(R.string.report_total_presents)}</td>
                        $dailyPresentsRow
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun printReport(html: String) {
        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Labour Attendance Report"
                val printAdapter = view?.createPrintDocumentAdapter(jobName)
                if (printAdapter != null) {
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                        .build()
                    printManager.print(jobName, printAdapter, printAttributes)
                }
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun createSerialTextView(number: Int): TextView {
        return TextView(this).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setBackgroundResource(R.drawable.bg_badge)
            layoutParams = LinearLayout.LayoutParams(dpToPx(34), dpToPx(34)).apply {
                marginEnd = dpToPx(10)
            }
        }
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
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun escapeHtml(text: String): String = text.htmlEncode()

    private fun dpToPx(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}

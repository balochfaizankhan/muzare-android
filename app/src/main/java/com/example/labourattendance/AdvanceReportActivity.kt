package com.example.labourattendance

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.text.htmlEncode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class AdvanceReportActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val uiDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    private var fromDate: Calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var toDate: Calendar = Calendar.getInstance()
    private var selectedTypes = mutableSetOf<String>()
    private var selectedGroupIds = mutableSetOf<Int>()
    private var selectedLabourIds = mutableSetOf<Int>()

    private lateinit var btnFromDate: Button
    private lateinit var btnToDate: Button
    private lateinit var containerReport: LinearLayout
    private lateinit var cardSummary: View
    private lateinit var tvSummaryTotal: TextView
    private lateinit var tvSummaryCount: TextView
    private lateinit var tvSummaryByType: TextView
    private lateinit var tvSummaryByGroup: TextView
    private lateinit var btnExportReport: Button
    private lateinit var tvActiveFiltersPreview: TextView
    
    private lateinit var btnSelectTypes: Button
    private lateinit var btnSelectGroups: Button
    private lateinit var btnSelectLabours: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advance_report)

        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        databaseHelper = DatabaseHelper(this)

        initViews()
        setupFilters()
        
        generateReport()
    }

    private fun initViews() {
        btnFromDate = findViewById(R.id.btnFromDate)
        btnToDate = findViewById(R.id.btnToDate)
        containerReport = findViewById(R.id.containerReport)
        cardSummary = findViewById(R.id.cardSummary)
        tvSummaryTotal = findViewById(R.id.tvSummaryTotal)
        tvSummaryCount = findViewById(R.id.tvSummaryCount)
        tvSummaryByType = findViewById(R.id.tvSummaryByType)
        tvSummaryByGroup = findViewById(R.id.tvSummaryByGroup)
        btnExportReport = findViewById(R.id.btnExportReport)
        tvActiveFiltersPreview = findViewById(R.id.tvActiveFiltersPreview)
        
        btnSelectTypes = findViewById(R.id.btnSelectTypes)
        btnSelectGroups = findViewById(R.id.btnSelectGroups)
        btnSelectLabours = findViewById(R.id.btnSelectLabours)

        findViewById<Button>(R.id.btnApplyFilters).setOnClickListener { generateReport() }
        btnExportReport.setOnClickListener { exportReport() }
    }

    private fun setupFilters() {
        btnSelectTypes.setOnClickListener { showTypeSelector() }
        btnSelectGroups.setOnClickListener { showGroupSelector() }
        btnSelectLabours.setOnClickListener { showLabourSelector() }

        btnFromDate.setOnClickListener { 
            showDatePicker(fromDate) { 
                updateDateButtons()
            } 
        }
        btnToDate.setOnClickListener { 
            showDatePicker(toDate) { 
                updateDateButtons()
            } 
        }

        updateDateButtons()
        updateFilterButtonLabels()
    }

    private fun updateDateButtons() {
        btnFromDate.text = uiDateFormat.format(fromDate.time)
        btnToDate.text = uiDateFormat.format(toDate.time)
    }

    private fun updateFilterButtonLabels() {
        btnSelectTypes.text = if (selectedTypes.isEmpty()) "All Types" else "${selectedTypes.size} Selected"
        btnSelectGroups.text = if (selectedGroupIds.isEmpty()) "All Groups" else "${selectedGroupIds.size} Selected"
        btnSelectLabours.text = if (selectedLabourIds.isEmpty()) "All Labourers" else "${selectedLabourIds.size} Selected"
        
        updateFilterPreview()
    }

    private fun updateFilterPreview() {
        val typePart = if (selectedTypes.isEmpty()) "All Types" else selectedTypes.joinToString(", ") { 
            when(it) {
                "DAILY_WAGE" -> getString(R.string.labour_type_daily)
                "CONTRACT" -> getString(R.string.labour_type_contract)
                "PRODUCTION_BASED" -> getString(R.string.labour_type_production)
                "MONTHLY" -> getString(R.string.labour_type_monthly)
                else -> it
            }
        }
        val groupPart = if (selectedGroupIds.isEmpty()) "All Groups" else "${selectedGroupIds.size} Groups"
        val labourPart = if (selectedLabourIds.isEmpty()) "All Labourers" else "${selectedLabourIds.size} Labourers"
        
        tvActiveFiltersPreview.text = "Filters: $typePart • $groupPart • $labourPart"
    }

    private fun showTypeSelector() {
        val types = listOf("DAILY_WAGE", "CONTRACT", "PRODUCTION_BASED", "MONTHLY")
        val names = types.map { 
            when(it) {
                "DAILY_WAGE" -> getString(R.string.labour_type_daily)
                "CONTRACT" -> getString(R.string.labour_type_contract)
                "PRODUCTION_BASED" -> getString(R.string.labour_type_production)
                "MONTHLY" -> getString(R.string.labour_type_monthly)
                else -> it
            }
        }
        
        showSearchableMultiSelect(
            title = getString(R.string.label_labour_type),
            allItems = names,
            initialSelectedIndices = types.indices.filter { selectedTypes.contains(types[it]) }.toSet()
        ) { selectedIndices ->
            selectedTypes.clear()
            selectedIndices.forEach { selectedTypes.add(types[it]) }
            updateFilterButtonLabels()
        }
    }

    private fun showGroupSelector() {
        val groups = databaseHelper.getAllGroups()
        val names = groups.map { if (it.id == 1) getString(R.string.group_general) else it.name }

        showSearchableMultiSelect(
            title = getString(R.string.btn_groups),
            allItems = names,
            initialSelectedIndices = groups.indices.filter { selectedGroupIds.contains(groups[it].id) }.toSet()
        ) { selectedIndices ->
            selectedGroupIds.clear()
            selectedIndices.forEach { selectedGroupIds.add(groups[it].id) }
            updateFilterButtonLabels()
        }
    }

    private fun showLabourSelector() {
        val allLabours = databaseHelper.getAllLabours()
        val names = allLabours.map { it.name }

        showSearchableMultiSelect(
            title = getString(R.string.label_labour_selection),
            allItems = names,
            initialSelectedIndices = allLabours.indices.filter { selectedLabourIds.contains(allLabours[it].id) }.toSet()
        ) { selectedIndices ->
            selectedLabourIds.clear()
            selectedIndices.forEach { selectedLabourIds.add(allLabours[it].id) }
            updateFilterButtonLabels()
        }
    }

    private fun showSearchableMultiSelect(
        title: String,
        allItems: List<String>,
        initialSelectedIndices: Set<Int>,
        onSelectionConfirmed: (Set<Int>) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_searchable_selection, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearch)
        val itemsContainer = dialogView.findViewById<LinearLayout>(R.id.itemsContainer)
        
        val currentSelectedIndices = initialSelectedIndices.toMutableSet()
        
        fun populateItems(filter: String = "") {
            itemsContainer.removeAllViews()
            allItems.forEachIndexed { index, item ->
                if (filter.isEmpty() || item.contains(filter, ignoreCase = true)) {
                    val checkBox = CheckBox(this).apply {
                        text = item
                        isChecked = currentSelectedIndices.contains(index)
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) currentSelectedIndices.add(index) else currentSelectedIndices.remove(index)
                        }
                        setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                        setTextColor("#101828".toColorInt())
                    }
                    itemsContainer.addView(checkBox)
                }
            }
        }

        populateItems()

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populateItems(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_confirm) { _, _ -> onSelectionConfirmed(currentSelectedIndices) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDatePicker(cal: Calendar, onDateSet: () -> Unit) {
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            onDateSet()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun generateReport() {
        containerReport.removeAllViews()
        val start = dbDateFormat.format(fromDate.time)
        val end = dbDateFormat.format(toDate.time)
        
        val allAdvances = databaseHelper.getAdvancesInRange(start, end)
        val allLabours = databaseHelper.getAllLabours()
        val allGroups = databaseHelper.getAllGroups()

        val filteredAdvances = allAdvances.filter { adv ->
            val labour = allLabours.find { it.id == adv.labourId } ?: return@filter false
            
            val typeMatch = selectedTypes.isEmpty() || selectedTypes.contains(labour.labourType)
            val groupMatch = selectedGroupIds.isEmpty() || selectedGroupIds.contains(labour.groupId)
            val labourMatch = selectedLabourIds.isEmpty() || selectedLabourIds.contains(labour.id)
            
            typeMatch && groupMatch && labourMatch
        }

        if (filteredAdvances.isEmpty()) {
            cardSummary.visibility = View.GONE
            btnExportReport.visibility = View.GONE
            val tv = TextView(this).apply {
                text = getString(R.string.no_labour_found)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(50), 0, 0)
                setTextColor(Color.parseColor("#667085"))
            }
            containerReport.addView(tv)
            return
        }

        updateSummary(filteredAdvances, allLabours)
        displayAdvances(filteredAdvances, allLabours, allGroups)
        
        cardSummary.visibility = View.VISIBLE
        btnExportReport.visibility = View.VISIBLE
    }

    private fun updateSummary(advances: List<DatabaseHelper.AdvanceRecord>, labours: List<DatabaseHelper.Labour>) {
        val total = advances.sumOf { it.amount }
        val count = advances.map { it.labourId }.distinct().size
        
        tvSummaryTotal.text = "SAR ${String.format(Locale.US, "%,.0f", total)}"
        tvSummaryCount.text = if (count == 1) "1 Labourer" else "$count Labourers"

        val byType = advances.groupBy { adv -> labours.find { it.id == adv.labourId }?.labourType ?: "UNKNOWN" }
            .map { (type, list) -> 
                val name = when(type) {
                    "DAILY_WAGE" -> getString(R.string.labour_type_daily)
                    "CONTRACT" -> getString(R.string.labour_type_contract)
                    "PRODUCTION_BASED" -> getString(R.string.labour_type_production)
                    "MONTHLY" -> getString(R.string.labour_type_monthly)
                    else -> type
                }
                "$name: ${list.sumOf { it.amount }.toInt()}"
            }.joinToString("  •  ")
        
        tvSummaryByType.text = byType

        val allGroups = databaseHelper.getAllGroups()
        val byGroup = advances.groupBy { adv -> labours.find { it.id == adv.labourId }?.groupId ?: 1 }
            .map { (groupId, list) ->
                val groupName = allGroups.find { it.id == groupId }?.let { if(it.id == 1) getString(R.string.group_general) else it.name } ?: "Unknown"
                "$groupName: ${list.sumOf { it.amount }.toInt()}"
            }.joinToString("  •  ")
        
        tvSummaryByGroup.text = byGroup
    }

    private fun displayAdvances(advances: List<DatabaseHelper.AdvanceRecord>, labours: List<DatabaseHelper.Labour>, groups: List<DatabaseHelper.Group>) {
        val advancesByLabour = advances.groupBy { it.labourId }
        
        // Iterate through all labours to maintain their display order
        labours.forEach { labour ->
            val labourAdvances = advancesByLabour[labour.id] ?: return@forEach
            val group = groups.find { it.id == labour.groupId }
            val totalForLabour = labourAdvances.sumOf { it.amount }
            
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundResource(R.drawable.bg_card)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dpToPx(16)
                layoutParams = lp
                elevation = 0f
            }

            // Header: Name and Total
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val nameTv = TextView(this).apply {
                text = labour.name
                textSize = 17f
                setTextColor(Color.parseColor("#101828"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val totalTv = TextView(this).apply {
                text = "Total: SAR ${totalForLabour.toInt()}"
                textSize = 16f
                setTextColor(Color.parseColor("#12B76A"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            headerRow.addView(nameTv)
            headerRow.addView(totalTv)
            card.addView(headerRow)

            val typeDisplayName = when(labour.labourType) {
                "CONTRACT" -> getString(R.string.labour_type_contract)
                "PRODUCTION_BASED" -> getString(R.string.labour_type_production)
                "MONTHLY" -> getString(R.string.labour_type_monthly)
                else -> getString(R.string.labour_type_daily)
            }
            val groupName = if (group?.id == 1) getString(R.string.group_general) else group?.name ?: ""
            
            val detailsTv = TextView(this).apply {
                text = "$typeDisplayName • $groupName"
                textSize = 12f
                setTextColor(Color.parseColor("#475467"))
                setPadding(0, dpToPx(2), 0, dpToPx(12))
            }
            card.addView(detailsTv)

            // Divider
            card.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1))
                setBackgroundColor(Color.parseColor("#EAECF0"))
            })

            // Entries
            labourAdvances.sortedByDescending { it.date }.forEach { adv ->
                val entryLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dpToPx(10), 0, dpToPx(10))
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val dateTv = TextView(this).apply {
                    text = adv.date
                    textSize = 14f
                    setTextColor(Color.parseColor("#344054"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val amountTv = TextView(this).apply {
                    text = "SAR ${adv.amount.toInt()}"
                    textSize = 14f
                    setTextColor(Color.parseColor("#D92D20"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                row.addView(dateTv)
                row.addView(amountTv)
                entryLayout.addView(row)

                if (!adv.description.isNullOrEmpty()) {
                    val descTv = TextView(this).apply {
                        text = adv.description
                        textSize = 13f
                        setTextColor(Color.parseColor("#475467"))
                        setPadding(0, dpToPx(2), 0, 0)
                    }
                    entryLayout.addView(descTv)
                }

                val sourceTv = TextView(this).apply {
                    text = "Paid from: ${adv.sourceName ?: "-"}"
                    textSize = 11f
                    setTextColor(Color.parseColor("#98A2B3"))
                    setPadding(0, dpToPx(2), 0, 0)
                }
                entryLayout.addView(sourceTv)

                card.addView(entryLayout)
            }
            
            containerReport.addView(card)
        }
    }

    private fun exportReport() {
        val start = dbDateFormat.format(fromDate.time)
        val end = dbDateFormat.format(toDate.time)
        val allAdvances = databaseHelper.getAdvancesInRange(start, end)
        val allLabours = databaseHelper.getAllLabours()
        val allGroups = databaseHelper.getAllGroups()

        val filtered = allAdvances.filter { adv ->
            val labour = allLabours.find { it.id == adv.labourId } ?: return@filter false
            val typeMatch = selectedTypes.isEmpty() || selectedTypes.contains(labour.labourType)
            val groupMatch = selectedGroupIds.isEmpty() || selectedGroupIds.contains(labour.groupId)
            val labourMatch = selectedLabourIds.isEmpty() || selectedLabourIds.contains(labour.id)
            typeMatch && groupMatch && labourMatch
        }

        val html = buildHtmlReport(filtered, allLabours, allGroups, start, end)
        printHtml(html)
    }

    private fun buildHtmlReport(advances: List<DatabaseHelper.AdvanceRecord>, labours: List<DatabaseHelper.Labour>, groups: List<DatabaseHelper.Group>, start: String, end: String): String {
        val advancesByLabour = advances.groupBy { it.labourId }
        
        // Iterate through all labours to maintain their display order
        val labourSections = labours.mapNotNull { labour ->
            val labourAdvances = advancesByLabour[labour.id] ?: return@mapNotNull null
            
            val groupName = groups.find { it.id == labour.groupId }?.let { if(it.id==1) getString(R.string.group_general) else it.name } ?: ""
            val typeDisplayName = when(labour.labourType) {
                "CONTRACT" -> getString(R.string.labour_type_contract)
                "PRODUCTION_BASED" -> getString(R.string.labour_type_production)
                "MONTHLY" -> getString(R.string.labour_type_monthly)
                else -> getString(R.string.labour_type_daily)
            }
            val totalForLabour = labourAdvances.sumOf { it.amount }.toInt()

            val rows = labourAdvances.sortedByDescending { it.date }.joinToString("") { adv ->
                "<tr><td style='padding-left: 20px;'>${adv.date}</td><td>${adv.sourceName?.htmlEncode() ?: "-"}</td><td>${adv.description?.htmlEncode() ?: "-"}</td><td style='text-align: right;'>SAR ${adv.amount.toInt()}</td></tr>"
            }

            """
            <div class="labour-section">
                <div class="labour-header">
                    <span>${labour.name.htmlEncode()} ($typeDisplayName - $groupName)</span>
                    <span style="float: right;">Total: SAR $totalForLabour</span>
                </div>
                <table>
                    <thead><tr><th style='width: 100px;'>Date</th><th style='width: 120px;'>Source</th><th>Description</th><th style='width: 80px; text-align: right;'>Amount</th></tr></thead>
                    <tbody>$rows</tbody>
                </table>
            </div>
            """.trimIndent()
        }.joinToString("")

        return """
            <html><head><style>
            body { font-family: sans-serif; color: #101828; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 15px; }
            th, td { border: 1px solid #EAECF0; padding: 8px; text-align: left; font-size: 11px; }
            th { background-color: #F9FAFB; color: #475467; }
            h1, h2 { text-align: center; color: #002B4E; margin-bottom: 5px; }
            .labour-section { margin-top: 25px; border: 1px solid #EAECF0; border-radius: 8px; overflow: hidden; }
            .labour-header { background-color: #F2F4F7; padding: 10px 15px; font-weight: bold; font-size: 13px; border-bottom: 1px solid #EAECF0; }
            .summary { background: #F9FAFB; padding: 20px; border: 1px solid #EAECF0; margin-top: 30px; border-radius: 8px; }
            </style></head><body>
            <h1>Labour Advances Report</h1>
            <h2 style="font-size: 14px; font-weight: normal; color: #667085;">Period: $start to $end</h2>
            
            $labourSections

            <div class="summary">
                <h3 style="margin-top: 0;">Overall Summary</h3>
                <p><strong>Total Distributed:</strong> SAR ${advances.sumOf { it.amount }.toInt()}</p>
                <p><strong>Unique Labourers:</strong> ${advances.map { it.labourId }.distinct().size}</p>
            </div>
            </body></html>
        """.trimIndent()
    }

    private fun printHtml(html: String) {
        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view?.createPrintDocumentAdapter("Advances Report")
                printManager.print("Advances Report", adapter!!, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun loadLocale() {
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("My_Lang", "en") ?: "en"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }
}

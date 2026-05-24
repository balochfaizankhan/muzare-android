package com.example.labourattendance

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.labourattendance.databinding.ActivityDispatchReportBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class DispatchReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDispatchReportBinding
    private lateinit var databaseHelper: DatabaseHelper
    private val fromCalendar = Calendar.getInstance()
    private val toCalendar = Calendar.getInstance()
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dbDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private var lastReportHtml: String? = null
    private var lastReportCsv: String? = null
    
    private var selectedVehicleIds = mutableSetOf<Int>()
    private var selectedTypeNames = mutableSetOf<String>()

    private fun getUiDateFormat(): SimpleDateFormat {
        val lang = getSharedPreferences("Settings", MODE_PRIVATE).getString("My_Lang", "en")
        return if (lang == "en") SimpleDateFormat("dd-MM-yyyy", Locale.US) else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private fun getUiDateTimeFormat(): SimpleDateFormat {
        val lang = getSharedPreferences("Settings", MODE_PRIVATE).getString("My_Lang", "en")
        return if (lang == "en") SimpleDateFormat("dd-MM-yyyy", Locale.US) else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private val exportPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) lastReportHtml?.let { exportPdf(uri, it) }
    }

    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) lastReportCsv?.let { exportCsv(uri, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityDispatchReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val initialPaddingLeft = binding.root.paddingLeft
        val initialPaddingTop = binding.root.paddingTop
        val initialPaddingRight = binding.root.paddingRight
        val initialPaddingBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
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

        updateDateLabels()

        binding.buttonFilterVehicles.setOnClickListener { showVehicleFilterDialog() }
        binding.buttonFilterTypes.setOnClickListener { showTypeFilterDialog() }
        binding.buttonSelectFromDate.setOnClickListener { showDatePicker(fromCalendar) }
        binding.buttonSelectToDate.setOnClickListener { showDatePicker(toCalendar) }
        binding.buttonViewReport.setOnClickListener { loadReport() }
        binding.buttonExportReport.setOnClickListener { view ->
            if (lastReportHtml == null) {
                Toast.makeText(this, "Please generate a report first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showExportMenu(view)
        }
    }

    private fun showVehicleFilterDialog() {
        val vehicles = databaseHelper.getAllVehicles()
        val names = vehicles.map { "${it.number} (${it.driverName})" }.toTypedArray()
        val checked = BooleanArray(vehicles.size) { selectedVehicleIds.contains(vehicles[it].id) }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.title_filter_vehicles)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) selectedVehicleIds.add(vehicles[which].id)
                else selectedVehicleIds.remove(vehicles[which].id)
            }
            .setPositiveButton(R.string.btn_apply, null)
            .setNeutralButton(R.string.btn_select_all) { _, _ ->
                selectedVehicleIds.clear()
                vehicles.forEach { selectedVehicleIds.add(it.id) }
                loadReport() // Auto refresh
            }
            .setNegativeButton(R.string.btn_clear_all) { _, _ ->
                selectedVehicleIds.clear()
                loadReport()
            }
            .show()
    }

    private fun showTypeFilterDialog() {
        val types = databaseHelper.getAllDateTypes()
        val names = types.map { it.name }.toTypedArray()
        val checked = BooleanArray(types.size) { selectedTypeNames.contains(types[it].name) }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.title_filter_types)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) selectedTypeNames.add(types[which].name)
                else selectedTypeNames.remove(types[which].name)
            }
            .setPositiveButton(R.string.btn_apply, null)
            .setNeutralButton(R.string.btn_select_all) { _, _ ->
                selectedTypeNames.clear()
                types.forEach { selectedTypeNames.add(it.name) }
                loadReport()
            }
            .setNegativeButton(R.string.btn_clear_all) { _, _ ->
                selectedTypeNames.clear()
                loadReport()
            }
            .show()
    }

    private fun showDatePicker(calendar: Calendar) {
        DatePickerDialog(this, { _, y, m, d ->
            calendar.set(y, m, d)
            if (calendar == fromCalendar) {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
            }
            updateDateLabels()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateLabels() {
        binding.buttonSelectFromDate.text = getUiDateFormat().format(fromCalendar.time)
        binding.buttonSelectToDate.text = getUiDateFormat().format(toCalendar.time)
    }

    private fun loadReport() {
        if (fromCalendar.after(toCalendar)) {
            Toast.makeText(this, getString(R.string.err_date_range), Toast.LENGTH_SHORT).show()
            return
        }

        binding.reportContainer.removeAllViews()
        val from = dbDateFormat.format(fromCalendar.time)
        val to = dbDateFormat.format(toCalendar.time)

        val allDispatches = databaseHelper.getDispatchesInRange(from, to)
        
        // Advanced Filter Logic
        val filteredDispatches = allDispatches.filter { d ->
            val vehicleMatch = selectedVehicleIds.isEmpty() || selectedVehicleIds.contains(d.vehicleId)
            val typeMatch = selectedTypeNames.isEmpty() || d.items.any { selectedTypeNames.contains(it.dateTypeName) }
            vehicleMatch && typeMatch
        }

        if (filteredDispatches.isEmpty()) {
            binding.textViewReportSummary.text = getString(R.string.no_labour_available)
            lastReportHtml = null
            lastReportCsv = null
            return
        }

        val totalDispatches = filteredDispatches.size
        // Dynamic Totaling: count items that match selected types
        val typeSums = mutableMapOf<String, Int>()
        filteredDispatches.forEach { d ->
            d.items.forEach { item ->
                if (selectedTypeNames.isEmpty() || selectedTypeNames.contains(item.dateTypeName)) {
                    typeSums[item.dateTypeName] = (typeSums[item.dateTypeName] ?: 0) + item.cartonCount
                }
            }
        }
        val totalCartons = typeSums.values.sum()

        val displayFrom = getUiDateFormat().format(fromCalendar.time)
        val displayTo = getUiDateFormat().format(toCalendar.time)
        
        val summaryText = StringBuilder()
        summaryText.append(getString(R.string.label_dispatch_summary, displayFrom, displayTo, totalDispatches, totalCartons))
        if (typeSums.isNotEmpty()) {
            summaryText.append("\n\nType Summary:")
            typeSums.forEach { (type, sum) ->
                summaryText.append("\n• $type: $sum")
            }
        }
        binding.textViewReportSummary.text = summaryText.toString()

        filteredDispatches.forEach { dispatch ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(10)
                }
            }
            
            val parsedDate = try { dbDateTimeFormat.parse(dispatch.date) } catch (_: Exception) { dbDateFormat.parse(dispatch.date) }
            val displayDate = if (parsedDate != null) getUiDateTimeFormat().format(parsedDate) else dispatch.date

            val info = TextView(this).apply {
                text = "$displayDate | ${dispatch.vehicleNumber}"
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
                textSize = 15f
            }
            
            val driverStr = getString(R.string.label_driver, dispatch.driverName)
            val relevantItems = dispatch.items.filter { selectedTypeNames.isEmpty() || selectedTypeNames.contains(it.dateTypeName) }
            val items = TextView(this).apply {
                text = "$driverStr\n" + relevantItems.joinToString(", ") { "${it.dateTypeName}: ${it.cartonCount}" }
                setTextColor(Color.parseColor("#475467"))
                setPadding(0, dpToPx(4), 0, 0)
                textSize = 14f
            }

            val totalD = relevantItems.sumOf { it.cartonCount }
            val totalView = TextView(this).apply {
                text = getString(R.string.label_total_cartons, totalD)
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dpToPx(4), 0, 0)
                textSize = 14f
            }
            
            card.addView(info)
            card.addView(items)
            card.addView(totalView)
            binding.reportContainer.addView(card)
        }

        lastReportHtml = buildHtmlReport(filteredDispatches, totalDispatches, totalCartons, typeSums)
        lastReportCsv = buildCsvReport(filteredDispatches)
    }

    private fun buildHtmlReport(dispatches: List<DatabaseHelper.DispatchRecord>, totalCount: Int, totalCartons: Int, typeSums: Map<String, Int>): String {
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val dir = if (isRtl) "rtl" else "ltr"
        
        val rows = dispatches.map { d ->
            val relevantItems = d.items.filter { selectedTypeNames.isEmpty() || selectedTypeNames.contains(it.dateTypeName) }
            val itemsHtml = relevantItems.joinToString("<br>") { "${it.dateTypeName}: ${it.cartonCount}" }
            val totalD = relevantItems.sumOf { it.cartonCount }
            val parsedDate = try { dbDateTimeFormat.parse(d.date) } catch (_: Exception) { dbDateFormat.parse(d.date) }
            val displayDate = if (parsedDate != null) getUiDateTimeFormat().format(parsedDate) else d.date

            "<tr><td>$displayDate</td><td>${d.vehicleNumber}</td><td>${d.driverName}</td><td>$itemsHtml</td><td>$totalD</td></tr>"
        }.joinToString("")

        val summaryRows = typeSums.map { (type, sum) -> 
            "<tr><td colspan='3' style='text-align:right;'><strong>$type</strong></td><td colspan='2'>$sum</td></tr>"
        }.joinToString("")

        val vehiclesFilter = if(selectedVehicleIds.isEmpty()) "All" else "Selected (${selectedVehicleIds.size})"
        val typesFilter = if(selectedTypeNames.isEmpty()) "All" else "Selected (${selectedTypeNames.size})"

        return """
            <html dir="$dir">
            <head><style>
                body { font-family: sans-serif; padding: 20px; color: #002B4E; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 11px; }
                th, td { border: 1px solid #ccc; padding: 6px; text-align: center; }
                th { background: #f2f2f2; }
                .summary-row { background: #fafafa; font-weight: bold; }
                h2 { color: #002B4E; }
            </style></head>
            <body>
                <h2 style='text-align:center;'>${getString(R.string.title_dispatch_report)}</h2>
                <p style='text-align:center;'><strong>${getString(R.string.label_from_date)}:</strong> ${getUiDateFormat().format(fromCalendar.time)} | <strong>${getString(R.string.label_to_date)}:</strong> ${getUiDateFormat().format(toCalendar.time)}</p>
                <p style='text-align:center;'><strong>Vehicles: $vehiclesFilter | Types: $typesFilter</strong></p>
                <table>
                    <thead><tr><th>Date</th><th>Vehicle</th><th>Driver</th><th>Items</th><th>Total</th></tr></thead>
                    <tbody>
                        $rows
                        <tr class='summary-row'><td colspan='3' style='text-align:right;'>Type Summary</td><td colspan='2'></td></tr>
                        $summaryRows
                        <tr class='summary-row'><td colspan='3' style='text-align:right;'>GRAND TOTAL ($totalCount Dispatches)</td><td colspan='2'>$totalCartons Cartons</td></tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
    }

    private fun buildCsvReport(dispatches: List<DatabaseHelper.DispatchRecord>): String {
        val sb = StringBuilder()
        sb.append("Dispatch Report\n")
        sb.append("${getString(R.string.label_from_date)},${getUiDateFormat().format(fromCalendar.time)},${getString(R.string.label_to_date)},${getUiDateFormat().format(toCalendar.time)}\n")
        val vehiclesFilter = if(selectedVehicleIds.isEmpty()) "All" else "Selected"
        val typesFilter = if(selectedTypeNames.isEmpty()) "All" else "Selected"
        sb.append("Vehicles,$vehiclesFilter,Types,$typesFilter\n\n")
        sb.append("#,Vehicle,Driver,Date,Items,Total\n")
        dispatches.forEachIndexed { index, d ->
            val relevantItems = d.items.filter { selectedTypeNames.isEmpty() || selectedTypeNames.contains(it.dateTypeName) }
            val items = relevantItems.joinToString(" | ") { "${it.dateTypeName}:${it.cartonCount}" }
            val total = relevantItems.sumOf { it.cartonCount }
            val parsedDate = try { dbDateTimeFormat.parse(d.date) } catch (_: Exception) { dbDateFormat.parse(d.date) }
            val displayDate = if (parsedDate != null) getUiDateTimeFormat().format(parsedDate) else d.date
            sb.append("${index + 1},${d.vehicleNumber},${d.driverName},\"$displayDate\",\"$items\",$total\n")
        }
        return sb.toString()
    }

    private fun showExportMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.btn_print))
        popup.menu.add(0, 2, 1, getString(R.string.btn_export_pdf))
        popup.menu.add(0, 3, 2, getString(R.string.btn_export_csv))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { lastReportHtml?.let { printReport(it) }; true }
                2 -> { exportPdfLauncher.launch("dispatch_report_${System.currentTimeMillis()}.pdf"); true }
                3 -> { exportCsvLauncher.launch("dispatch_report_${System.currentTimeMillis()}.csv"); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun printReport(html: String) {
        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = view?.createPrintDocumentAdapter("Dispatch Report")
                printManager.print("Dispatch Report", printAdapter!!, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun exportPdf(uri: Uri, html: String) {
        printReport(html)
    }

    private fun exportCsv(uri: Uri, csv: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            Toast.makeText(this, "CSV Exported", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    private fun loadLocale() {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        val lang = prefs.getString("My_Lang", "en") ?: "en"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        if (lang == "ar" || lang == "ur") config.setLayoutDirection(locale) else config.setLayoutDirection(Locale.ENGLISH)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}

package com.example.labourattendance

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class ExpenditureReportActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private val fromCalendar = Calendar.getInstance()
    private val toCalendar = Calendar.getInstance()
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    private lateinit var btnSelectFrom: Button
    private lateinit var btnSelectTo: Button
    private lateinit var btnFilterCats: Button
    private lateinit var btnFilterAccounts: Button
    private lateinit var btnViewReport: Button
    private lateinit var btnExport: Button
    private lateinit var reportContainer: LinearLayout
    private lateinit var tvSummary: TextView
    
    private var lastReportHtml: String? = null
    private var lastReportCsv: String? = null
    private var selectedCategories = mutableSetOf<String>()
    private var selectedAccountIds = mutableSetOf<Int>()

    private fun getUiDateFormat(): SimpleDateFormat {
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
        setContentView(R.layout.activity_expenditure_report)

        val root = findViewById<View>(R.id.expReportRoot)
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
        
        btnSelectFrom = findViewById(R.id.buttonSelectFromDate)
        btnSelectTo = findViewById(R.id.buttonSelectToDate)
        btnFilterCats = findViewById(R.id.buttonFilterCategories)
        btnFilterAccounts = findViewById(R.id.buttonFilterAccounts)
        btnViewReport = findViewById(R.id.buttonViewReport)
        btnExport = findViewById(R.id.buttonExportReport)
        reportContainer = findViewById(R.id.reportContainer)
        tvSummary = findViewById(R.id.textViewReportSummary)

        updateDateLabels()

        btnSelectFrom.setOnClickListener { showDatePicker(fromCalendar) }
        btnSelectTo.setOnClickListener { showDatePicker(toCalendar) }
        btnFilterCats.setOnClickListener { showCategoryFilterDialog() }
        btnFilterAccounts.setOnClickListener { showAccountFilterDialog() }
        btnViewReport.setOnClickListener { loadReport() }
        btnExport.setOnClickListener { view ->
            if (lastReportHtml == null) {
                Toast.makeText(this, "Please generate a report first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showExportMenu(view)
        }
    }

    private fun getTranslatedCategory(name: String): String {
        return when (name) {
            "POL (Fuel/Oil)" -> getString(R.string.cat_pol)
            "Pesticides & Fertilizers" -> getString(R.string.cat_pesticides)
            "Repairs" -> getString(R.string.cat_repairs)
            "Salaries" -> getString(R.string.cat_salaries)
            "Groceries" -> getString(R.string.cat_groceries)
            "Vegetables" -> getString(R.string.cat_vegetables)
            "Others" -> getString(R.string.cat_others)
            else -> name
        }
    }

    private fun showCategoryFilterDialog() {
        val cats = databaseHelper.getAllExpCategories()
        val names = cats.map { getTranslatedCategory(it.name) }.toTypedArray()
        val checked = BooleanArray(cats.size) { selectedCategories.contains(cats[it].name) }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.btn_select_cats)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) selectedCategories.add(cats[which].name)
                else selectedCategories.remove(cats[which].name)
            }
            .setPositiveButton(R.string.btn_apply) { _, _ -> loadReport() }
            .setNeutralButton(R.string.btn_select_all) { _, _ ->
                selectedCategories.clear()
                cats.forEach { selectedCategories.add(it.name) }
                loadReport()
            }
            .setNegativeButton(R.string.btn_clear_all) { _, _ ->
                selectedCategories.clear()
                loadReport()
            }
            .show()
    }

    private fun showAccountFilterDialog() {
        val accounts = databaseHelper.getAllFundSources()
        val names = accounts.map { it.name }.toTypedArray()
        val checked = BooleanArray(accounts.size) { selectedAccountIds.contains(accounts[it].id) }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.btn_select_accounts)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) selectedAccountIds.add(accounts[which].id)
                else selectedAccountIds.remove(accounts[which].id)
            }
            .setPositiveButton(R.string.btn_apply) { _, _ -> loadReport() }
            .setNeutralButton(R.string.btn_select_all) { _, _ ->
                selectedAccountIds.clear()
                accounts.forEach { selectedAccountIds.add(it.id) }
                loadReport()
            }
            .setNegativeButton(R.string.btn_clear_all) { _, _ ->
                selectedAccountIds.clear()
                loadReport()
            }
            .show()
    }

    private fun showDatePicker(calendar: Calendar) {
        DatePickerDialog(this, { _, y, m, d ->
            calendar.set(y, m, d)
            updateDateLabels()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateLabels() {
        btnSelectFrom.text = getUiDateFormat().format(fromCalendar.time)
        btnSelectTo.text = getUiDateFormat().format(toCalendar.time)
    }

    private fun loadReport() {
        if (fromCalendar.after(toCalendar)) {
            Toast.makeText(this, getString(R.string.err_date_range), Toast.LENGTH_SHORT).show()
            return
        }

        reportContainer.removeAllViews()
        val from = dbDateFormat.format(fromCalendar.time)
        val to = dbDateFormat.format(toCalendar.time)

        val allVouchers = databaseHelper.getVouchersInRange(from, to)
        
        // Filter logic: Check category matches AND account source matches
        val filteredVouchers = allVouchers.mapNotNull { v ->
            val matchesAccount = selectedAccountIds.isEmpty() || selectedAccountIds.contains(v.sourceId)
            if (!matchesAccount) return@mapNotNull null
            
            val matchingItems = v.items.filter { selectedCategories.isEmpty() || selectedCategories.contains(it.category) }
            if (matchingItems.isNotEmpty()) {
                v.copy(items = matchingItems, totalAmount = matchingItems.sumOf { it.amount })
            } else null
        }

        if (filteredVouchers.isEmpty()) {
            tvSummary.text = getString(R.string.no_labour_available)
            lastReportHtml = null
            lastReportCsv = null
            return
        }

        val totalAmount = filteredVouchers.sumOf { it.totalAmount }
        val accountTotals = mutableMapOf<String, Double>()
        filteredVouchers.forEach { v ->
            val name = v.sourceName ?: "Unknown"
            accountTotals[name] = (accountTotals[name] ?: 0.0) + v.totalAmount
        }

        val dFrom = getUiDateFormat().format(fromCalendar.time)
        val dTo = getUiDateFormat().format(toCalendar.time)
        tvSummary.text = getString(R.string.label_exp_summary, dFrom, dTo, filteredVouchers.size, totalAmount.toInt())

        filteredVouchers.forEach { v ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(10)
                }
            }
            
            val info = TextView(this).apply {
                text = "${v.voucherNumber} | ${v.date}\n${getString(R.string.label_source, v.sourceName ?: "Unknown")}"
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
                textSize = 15f
            }
            
            val items = TextView(this).apply {
                text = v.items.joinToString("\n") { 
                    "${getTranslatedCategory(it.category)}: ${getString(R.string.value_sar, it.amount.toInt())} ${if(it.description?.isNotEmpty() == true) "(${it.description})" else ""}" 
                }
                setTextColor(Color.parseColor("#475467"))
                setPadding(0, dpToPx(8), 0, 0)
                textSize = 14f
            }
            
            card.addView(info)
            card.addView(items)
            reportContainer.addView(card)
        }

        // Add Breakdown View at the bottom
        val breakdownCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(20)
            }
        }
        
        breakdownCard.addView(TextView(this).apply {
            text = getString(R.string.label_account_breakdown)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#101828"))
        })
        
        accountTotals.forEach { (name, total) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(4), 0, 0)
            }
            row.addView(TextView(this).apply { text = name; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            row.addView(TextView(this).apply { text = getString(R.string.value_sar, total.toInt()); setTypeface(null, Typeface.BOLD) })
            breakdownCard.addView(row)
        }
        
        breakdownCard.addView(View(this).apply { 
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)).apply { topMargin = dpToPx(8) }
            setBackgroundColor(Color.LTGRAY)
        })
        
        val grandTotalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(8), 0, 0)
        }
        grandTotalRow.addView(TextView(this).apply { text = getString(R.string.label_grand_total); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setTypeface(null, Typeface.BOLD) })
        grandTotalRow.addView(TextView(this).apply { text = getString(R.string.value_sar, totalAmount.toInt()); setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#002B4E")) })
        breakdownCard.addView(grandTotalRow)
        
        reportContainer.addView(breakdownCard)

        lastReportHtml = buildHtmlReport(filteredVouchers, totalAmount, accountTotals)
        lastReportCsv = buildCsvReport(filteredVouchers, accountTotals)
    }

    private fun Int.sp(): Float = this.toFloat()
    private fun Int.dp(): Int = dpToPx(this)

    private fun buildHtmlReport(vouchers: List<DatabaseHelper.Voucher>, grandTotal: Double, accountTotals: Map<String, Double>): String {
        val rows = vouchers.flatMap { v ->
            v.items.map { it to v }
        }.mapIndexed { index, pair ->
            val item = pair.first
            val v = pair.second
            "<tr><td>${index+1}</td><td>${v.voucherNumber}</td><td>${v.date}</td><td>${v.sourceName ?: ""}</td><td>${getTranslatedCategory(item.category)}</td><td>${item.description ?: ""}</td><td>${getString(R.string.value_sar, item.amount.toInt())}</td></tr>"
        }.joinToString("")

        val breakdownRows = accountTotals.map { (name, total) ->
            "<tr><td colspan='6' style='text-align:right;'><strong>$name:</strong></td><td><strong>${getString(R.string.value_sar, total.toInt())}</strong></td></tr>"
        }.joinToString("")

        return """
            <html><head><style>
                body { font-family: sans-serif; padding: 20px; color: #101828; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 11px; }
                th, td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                th { background: #f2f2f2; }
            </style></head>
            <body>
                <h2 style='text-align:center;'>${getString(R.string.title_exp_report)}</h2>
                <p style='text-align:center;'>${getString(R.string.report_period)} ${getUiDateFormat().format(fromCalendar.time)} - ${getUiDateFormat().format(toCalendar.time)}</p>
                <table>
                    <thead><tr>
                        <th>#</th>
                        <th>${getString(R.string.label_voucher_no).split(":")[0]}</th>
                        <th>${getString(R.string.label_date).split(":")[0]}</th>
                        <th>${getString(R.string.label_deduction_account)}</th>
                        <th>${getString(R.string.hint_expense_category).split("(")[0]}</th>
                        <th>${getString(R.string.hint_description_optional).split("(")[0]}</th>
                        <th>${getString(R.string.hint_amount_sar).split("(")[0]}</th>
                    </tr></thead>
                    <tbody>
                        $rows
                        $breakdownRows
                        <tr style='background-color:#eee;'>
                            <td colspan='6' style='text-align:right;'><strong>${getString(R.string.label_grand_total)}</strong></td>
                            <td><strong>${getString(R.string.value_sar, grandTotal.toInt())}</strong></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
    }

    private fun buildCsvReport(vouchers: List<DatabaseHelper.Voucher>, accountTotals: Map<String, Double>): String {
        val sb = StringBuilder()
        sb.append("Expenditure Report\n")
        sb.append("From,${dbDateFormat.format(fromCalendar.time)},To,${dbDateFormat.format(toCalendar.time)}\n\n")
        sb.append("#,Voucher,Date,${getString(R.string.label_deduction_account)},Category,Description,Amount\n")
        var count = 1
        vouchers.forEach { v ->
            v.items.forEach { item ->
                sb.append("${count++},${v.voucherNumber},${v.date},\"${v.sourceName ?: ""}\",${getTranslatedCategory(item.category)},\"${item.description ?: ""}\",${item.amount}\n")
            }
        }
        sb.append("\nACCOUNT TOTALS\n")
        accountTotals.forEach { (name, total) ->
            sb.append(",,,,$name,Total,${total.toInt()}\n")
        }
        sb.append(",,,,GRAND TOTAL,,${vouchers.sumOf { it.totalAmount }.toInt()}\n")
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
                2 -> { exportPdfLauncher.launch("exp_report_${System.currentTimeMillis()}.pdf"); true }
                3 -> { exportCsvLauncher.launch("exp_report_${System.currentTimeMillis()}.csv"); true }
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
                val printAdapter = view?.createPrintDocumentAdapter("Expenditure Report")
                printManager.print("Expenditure Report", printAdapter!!, PrintAttributes.Builder().build())
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
        } catch (_: Exception) {}
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
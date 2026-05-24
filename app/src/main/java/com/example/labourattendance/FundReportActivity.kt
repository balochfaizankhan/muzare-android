package com.example.labourattendance

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.htmlEncode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class FundReportActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var fundReportContainer: LinearLayout
    private lateinit var tvSummaryInjected: TextView
    private lateinit var tvSummarySpent: TextView
    private lateinit var tvSummaryBalance: TextView
    private lateinit var tvBalanceMeaning: TextView
    private var lastReportHtml: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fund_report)

        val root = findViewById<View>(R.id.fundReportRoot)
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
        fundReportContainer = findViewById(R.id.fundReportContainer)
        tvSummaryInjected = findViewById(R.id.tvSummaryInjected)
        tvSummarySpent = findViewById(R.id.tvSummarySpent)
        tvSummaryBalance = findViewById(R.id.tvSummaryBalance)
        tvBalanceMeaning = findViewById(R.id.tvBalanceMeaning)

        loadFundReport()

        findViewById<Button>(R.id.buttonExportFundReport).setOnClickListener {
            lastReportHtml?.let { printReport(it) }
        }
    }

    private fun loadFundReport() {
        fundReportContainer.removeAllViews()

        val sources = databaseHelper.getAllFundSources()
        val fundEntries = databaseHelper.getAllFundEntries()
        val vouchers = databaseHelper.getAllVouchers()
        val advances = databaseHelper.getAllAdvances()
        val sales = databaseHelper.getAllSales()

        var grandTotalInjected = 0.0
        var grandTotalExpenses = 0.0
        var grandTotalDebits = 0.0

        val htmlRows = StringBuilder()

        sources.forEach { source ->
            // Liability side: Partner gives value to business
            val cashInjected = fundEntries.filter { it.sourceId == source.id && it.amount > 0 }.sumOf { it.amount }
            val totalVoucherPaid = vouchers.filter { it.sourceId == source.id }.sumOf { it.totalAmount }
            val totalAdvancePaid = advances.filter { it.sourceId == source.id }.sumOf { it.amount }
            val expensesPaid = totalVoucherPaid + totalAdvancePaid

            // Asset/Withdrawal side: Partner takes value from business
            val cashWithdrawn = fundEntries.filter { it.sourceId == source.id && it.amount < 0 }.sumOf { -it.amount }
            val salesReceived = sales.filter { it.sourceId == source.id }.sumOf { it.totalAmount }
            val totalDebit = cashWithdrawn + salesReceived

            // Sign Convention: Negative = Liability (Business owes Partner), Positive = Receivable (Partner owes Business)
            val balance = totalDebit - (cashInjected + expensesPaid)

            grandTotalInjected += cashInjected
            grandTotalExpenses += expensesPaid
            grandTotalDebits += totalDebit

            // UI Card
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(12)
                }
                elevation = dpToPx(1).toFloat()
            }

            val titleView = TextView(this).apply {
                text = source.name
                textSize = 18f
                setTextColor(ContextCompat.getColor(this@FundReportActivity, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dpToPx(8))
            }

            val injectedRow = createSummaryRow(getString(R.string.label_cash_injected), cashInjected, "#12B76A")
            val expensesRow = createSummaryRow(getString(R.string.label_expenses_paid), expensesPaid, "#12B76A")
            val withdrawalRow = createSummaryRow(getString(R.string.label_withdrawals), totalDebit, "#F04438")
            
            val balanceColor = if (balance <= 0) "#004EEB" else "#F04438"
            val balanceRow = createSummaryRow(getString(R.string.label_net_share), balance, balanceColor, true)

            card.addView(titleView)
            card.addView(injectedRow)
            card.addView(expensesRow)
            card.addView(withdrawalRow)
            card.addView(View(this).apply { 
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)).apply { 
                    setMargins(0, dpToPx(8), 0, dpToPx(8)) 
                }
                setBackgroundColor(ContextCompat.getColor(this@FundReportActivity, R.color.border_light))
            })
            card.addView(balanceRow)

            val meaning = when {
                balance < 0 -> "Business owes partner"
                balance > 0 -> "Partner owes business"
                else -> "Settled"
            }
            val meaningView = TextView(this).apply {
                text = meaning
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@FundReportActivity, R.color.text_muted))
                setPadding(0, dpToPx(2), 0, 0)
            }
            card.addView(meaningView)

            fundReportContainer.addView(card)

            // HTML Row
            htmlRows.append("""
                <tr>
                    <td style="text-align: left; font-weight: bold;">${source.name.htmlEncode()}</td>
                    <td style="color: #12B76A;">SAR ${cashInjected.toInt()}</td>
                    <td style="color: #12B76A;">SAR ${expensesPaid.toInt()}</td>
                    <td style="color: #F04438;">SAR ${totalDebit.toInt()}</td>
                    <td style="font-weight: bold; color: $balanceColor;">SAR ${balance.toInt()}</td>
                </tr>
            """.trimIndent())
        }

        val totalPartnerBalance = grandTotalDebits - (grandTotalInjected + grandTotalExpenses)
        tvSummaryInjected.text = "SAR ${grandTotalInjected.toInt()}"
        tvSummarySpent.text = "SAR ${grandTotalExpenses.toInt()}"
        tvSummaryBalance.text = "SAR ${totalPartnerBalance.toInt()}"
        tvSummaryBalance.setTextColor(Color.parseColor(if (totalPartnerBalance <= 0) "#004EEB" else "#F04438"))
        
        tvBalanceMeaning.text = when {
            totalPartnerBalance < 0 -> "Overall: Business owes partners"
            totalPartnerBalance > 0 -> "Overall: Partners owe business"
            else -> "Overall: Settled"
        }

        lastReportHtml = buildFundReportHtml(htmlRows.toString(), grandTotalInjected, grandTotalExpenses, grandTotalDebits)
    }

    private fun buildFundReportHtml(rows: String, totalIn: Double, totalExp: Double, totalOut: Double): String {
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val bodyDir = if (isRtl) "rtl" else "ltr"
        val totalBalance = totalOut - (totalIn + totalExp)
        val balanceColor = if (totalBalance <= 0) "#004EEB" else "#F04438"
        val meaning = if (totalBalance < 0) "Business owes partners" else if (totalBalance > 0) "Partners owe business" else "Settled"
        
        return """
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 20px; color: #101828; direction: $bodyDir; }
                    h1 { color: #002B4E; text-align: center; }
                    .summary { margin-bottom: 20px; padding: 15px; background: #F9FAFB; border-radius: 8px; border: 1px solid #EAECF0; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    th, td { border: 1px solid #EAECF0; padding: 12px; text-align: center; }
                    th { background-color: #F2F4F7; color: #475467; }
                </style>
            </head>
            <body>
                <h1>${getString(R.string.title_fund_report)}</h1>
                <div class="summary">
                    <strong>${getString(R.string.label_cash_injected)}:</strong> SAR ${totalIn.toInt()}<br>
                    <strong>${getString(R.string.label_expenses_paid)}:</strong> SAR ${totalExp.toInt()}<br>
                    <strong>${getString(R.string.label_withdrawals)}:</strong> SAR ${totalOut.toInt()}<br>
                    <strong>${getString(R.string.label_net_share)}:</strong> <span style="color: $balanceColor;">SAR ${totalBalance.toInt()}</span><br>
                    <small style="color: #667085;">$meaning</small>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th>${getString(R.string.hint_account_name)}</th>
                            <th>${getString(R.string.label_cash_injected)}</th>
                            <th>${getString(R.string.label_expenses_paid)}</th>
                            <th>${getString(R.string.label_withdrawals)}</th>
                            <th>${getString(R.string.label_net_share)}</th>
                        </tr>
                    </thead>
                    <tbody>
                        $rows
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun printReport(html: String) {
        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                val jobName = "Partner Ledger"
                val printAdapter = view?.createPrintDocumentAdapter(jobName)
                if (printAdapter != null) {
                    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                }
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun createSummaryRow(label: String, amount: Double, color: String, isBold: Boolean = false): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            
            val labelView = TextView(this@FundReportActivity).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ContextCompat.getColor(this@FundReportActivity, R.color.text_secondary))
                if (isBold) {
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@FundReportActivity, R.color.text_primary))
                }
            }

            val valueView = TextView(this@FundReportActivity).apply {
                text = "SAR ${amount.toInt()}"
                setTextColor(Color.parseColor(color))
                if (isBold) setTypeface(null, Typeface.BOLD)
            }

            addView(labelView)
            addView(valueView)
        }
    }

    private fun loadLocale() {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        val lang = prefs.getString("My_Lang", "en") ?: "en"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        if (lang == "ar" || lang == "ur") config.setLayoutDirection(locale) else config.setLayoutDirection(Locale.ENGLISH)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
}

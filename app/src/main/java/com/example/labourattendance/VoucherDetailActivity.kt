package com.example.labourattendance

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.*

class VoucherDetailActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private var voucherId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voucher_detail)

        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = DatabaseHelper(this)
        voucherId = intent.getIntExtra("VOUCHER_ID", -1)

        if (voucherId == -1) {
            finish()
            return
        }

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        
        loadVoucherDetails()
    }

    override fun onResume() {
        super.onResume()
        loadVoucherDetails()
    }

    private fun loadVoucherDetails() {
        val voucher = databaseHelper.getAllVouchers().find { it.id == voucherId }
        if (voucher == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.tvVoucherNumber).text = voucher.voucherNumber
        findViewById<TextView>(R.id.tvVoucherDate).text = getString(R.string.label_date, voucher.date)
        findViewById<TextView>(R.id.tvRecordedBy).text = "${getString(R.string.label_recorded_by).substringBefore(":")}: ${voucher.recordedBy}"
        
        val source = databaseHelper.getAllFundSources().find { it.id == voucher.sourceId }
        findViewById<TextView>(R.id.tvSourceAccount).text = "${getString(R.string.label_deduction_account)}: ${source?.name ?: "-"}"
        
        findViewById<TextView>(R.id.tvGrandTotal).text = getString(R.string.value_sar, voucher.totalAmount.toInt())

        val container = findViewById<LinearLayout>(R.id.containerItems)
        container.removeAllViews()

        voucher.items.forEach { item ->
            val itemView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dpToPx(16))
            }

            val categoryRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val catTv = TextView(this).apply {
                text = getTranslatedCategory(item.category)
                setTextColor("#101828".toColorInt())
                setTypeface(null, Typeface.BOLD)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amtTv = TextView(this).apply {
                text = getString(R.string.value_sar, item.amount.toInt())
                setTextColor("#101828".toColorInt())
                setTypeface(null, Typeface.BOLD)
                textSize = 15f
            }

            categoryRow.addView(catTv)
            categoryRow.addView(amtTv)

            val descTv = TextView(this).apply {
                text = if (item.description.isNullOrEmpty()) "-" else item.description
                setTextColor(Color.parseColor("#475467"))
                textSize = 14f
                setPadding(0, dpToPx(4), 0, 0)
            }

            itemView.addView(categoryRow)
            itemView.addView(descTv)
            container.addView(itemView)
        }

        findViewById<ImageButton>(R.id.buttonPrint).setOnClickListener { printVoucher(voucher) }

        val editBtn = findViewById<Button>(R.id.buttonEditVoucher)
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        val isClosed = databaseHelper.isCurrentSeasonClosed()
        
        if (role == "viewer" || isClosed) {
            editBtn.visibility = View.GONE
        } else {
            editBtn.setOnClickListener {
                val intent = Intent(this, AddVoucherActivity::class.java)
                intent.putExtra("VOUCHER_ID", voucher.id)
                startActivity(intent)
                finish()
            }
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

    private fun printVoucher(voucher: DatabaseHelper.Voucher) {
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val dir = if (isRtl) "rtl" else "ltr"
        
        val itemsHtml = voucher.items.joinToString("") {
            "<tr><td>${getTranslatedCategory(it.category)}</td><td>${it.description ?: ""}</td><td>${getString(R.string.value_sar, it.amount.toInt())}</td></tr>"
        }

        val html = """
            <html dir="$dir">
            <head><style>
                body { font-family: sans-serif; padding: 40px; color: #101828; }
                .header { text-align: center; border-bottom: 2px solid #002B4E; padding-bottom: 20px; margin-bottom: 30px; }
                .v-num { font-size: 24px; font-weight: bold; color: #002B4E; margin: 10px 0; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                th, td { border: 1px solid #EAECF0; padding: 12px; text-align: left; }
                th { background-color: #F9FAFB; }
                .total { margin-top: 30px; font-size: 20px; font-weight: bold; text-align: right; }
                .footer { margin-top: 50px; font-size: 12px; color: #475467; border-top: 1px solid #ccc; padding-top: 10px; }
            </style></head>
            <body>
                <div class="header">
                    <h2>${getString(R.string.app_name)}</h2>
                    <div class="v-num">${voucher.voucherNumber}</div>
                    <p>Date: ${voucher.date}</p>
                </div>
                <table>
                    <thead><tr><th>Category</th><th>Description</th><th>Amount</th></tr></thead>
                    <tbody>$itemsHtml</tbody>
                </table>
                <div class="total">${getString(R.string.label_total_amount, voucher.totalAmount.toInt())}</div>
                <div class="footer">Recorded By: ${voucher.recordedBy}</div>
            </body></html>
        """.trimIndent()

        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                val printAdapter = view?.createPrintDocumentAdapter("Voucher_${voucher.voucherNumber}")
                printManager.print("Voucher_${voucher.voucherNumber}", printAdapter!!, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun loadLocale() {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        val lang = prefs.getString("My_Lang", "en") ?: "en"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        if (lang == "ar" || lang == "ur") {
            config.setLayoutDirection(locale)
        } else {
            config.setLayoutDirection(Locale.ENGLISH)
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
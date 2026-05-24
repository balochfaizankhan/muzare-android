package com.example.labourattendance

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.*

class ProfitLossActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private val fromCalendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
    private val toCalendar = Calendar.getInstance()
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profit_loss)

        val root = findViewById<View>(R.id.profitLossRoot)
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

        updateDateLabels()
        findViewById<Button>(R.id.buttonFromDate).setOnClickListener { showDatePicker(fromCalendar) }
        findViewById<Button>(R.id.buttonToDate).setOnClickListener { showDatePicker(toCalendar) }

        loadPLData()
    }

    private fun getUiDateFormat(): SimpleDateFormat {
        val lang = getSharedPreferences("Settings", MODE_PRIVATE).getString("My_Lang", "en")
        return if (lang == "en") SimpleDateFormat("dd-MM-yyyy", Locale.US) else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private fun showDatePicker(calendar: Calendar) {
        DatePickerDialog(this, { _, y, m, d ->
            calendar.set(y, m, d)
            updateDateLabels()
            loadPLData()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateLabels() {
        findViewById<Button>(R.id.buttonFromDate).text = getUiDateFormat().format(fromCalendar.time)
        findViewById<Button>(R.id.buttonToDate).text = getUiDateFormat().format(toCalendar.time)
    }

    private fun loadPLData() {
        val from = dbDateFormat.format(fromCalendar.time)
        val to = dbDateFormat.format(toCalendar.time)
        val summary = databaseHelper.getPLSummary(from, to)

        updateCard(R.id.cardSales, getString(R.string.title_sales), summary.totalSales, "#002B4E")
        updateCard(R.id.cardExpenses, getString(R.string.title_expenditure), summary.totalExpenses, "#F04438")
        
        val profitColor = if (summary.netProfit >= 0) "#12B76A" else "#F04438"
        updateCard(R.id.cardNetProfit, getString(R.string.label_net_pl), summary.netProfit, profitColor)
        
        updateCard(R.id.cardFunds, getString(R.string.label_total_capital), summary.totalFunds, "#475467")

        loadAccountBalances()
    }

    private fun updateCard(viewId: Int, label: String, value: Double, color: String) {
        val view = findViewById<View>(viewId)
        view.findViewById<TextView>(R.id.textViewLabel).text = label
        val valTv = view.findViewById<TextView>(R.id.textViewValue)
        valTv.text = "SAR ${value.toInt()}"
        valTv.setTextColor(Color.parseColor(color))
    }

    private fun loadAccountBalances() {
        val container = findViewById<LinearLayout>(R.id.accountBalancesContainer)
        container.removeAllViews()
        
        val accounts = databaseHelper.getAllAccountsWithBalance()
        var totalCashBalance = 0.0

        accounts.forEach { account ->
            val isPartner = databaseHelper.isAccountPayable(account.id)
            if (!isPartner) {
                totalCashBalance += account.balance
            }
            
            val displayBalance = if (isPartner) -account.balance else account.balance

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(8)
                }
            }
            val name = TextView(this).apply {
                text = account.name
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(Color.parseColor("#475467"))
            }
            val bal = TextView(this).apply {
                text = "SAR ${displayBalance.toInt()}"
                
                val color = if (isPartner) {
                    if (displayBalance <= 0) "#004EEB" else "#F04438"
                } else {
                    if (displayBalance >= 0) "#12B76A" else "#F04438"
                }
                setTextColor(Color.parseColor(color))
                setTypeface(null, Typeface.BOLD)
            }
            row.addView(name)
            row.addView(bal)
            container.addView(row)
        }

        // Grand Total Card (Available Cash ONLY)
        val grandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(16))
            setBackgroundResource(R.drawable.bg_card)
            background.setTint(Color.parseColor("#F2F4F7"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8)
            }
        }
        val name = TextView(this).apply {
            text = getString(R.string.label_total_available_balance)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(Color.parseColor("#002B4E"))
            setTypeface(null, Typeface.BOLD)
        }
        val bal = TextView(this).apply {
            text = "SAR ${totalCashBalance.toInt()}"
            setTextColor(Color.parseColor("#004EEB"))
            setTypeface(null, Typeface.BOLD)
            textSize = 18f
        }
        grandRow.addView(name)
        grandRow.addView(bal)
        container.addView(grandRow)
    }

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

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
}

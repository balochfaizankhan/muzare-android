package com.example.labourattendance

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import java.util.*

class AccountDetailActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var textViewAccountName: TextView
    private lateinit var textViewAccountBalance: TextView
    private lateinit var txHistoryContainer: LinearLayout
    private var accountId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_detail)

        val root = findViewById<View>(R.id.accountDetailRoot)
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
        textViewAccountName = findViewById(R.id.textViewAccountName)
        textViewAccountBalance = findViewById(R.id.textViewAccountBalance)
        txHistoryContainer = findViewById(R.id.txHistoryContainer)

        accountId = intent.getIntExtra("ACCOUNT_ID", -1)
        if (accountId == -1) {
            finish()
            return
        }

        loadAccountData()
    }

    override fun onResume() {
        super.onResume()
        loadAccountData()
    }

    private fun loadAccountData() {
        val accounts = databaseHelper.getAllAccountsWithBalance()
        val account = accounts.find { it.id == accountId } ?: return

        val isPartner = databaseHelper.isAccountPayable(account.id)
        val displayBalance = if (isPartner) -account.balance else account.balance

        textViewAccountName.text = account.name
        textViewAccountBalance.text = getString(R.string.label_balance, displayBalance.toInt())
        
        val color = if (isPartner) {
            if (displayBalance <= 0) "#004EEB" else "#F04438"
        } else {
            if (displayBalance >= 0) "#12B76A" else "#F04438"
        }
        textViewAccountBalance.setTextColor(Color.parseColor(color))

        loadTransactions()
    }

    private fun loadTransactions() {
        txHistoryContainer.removeAllViews()
        val transactions = databaseHelper.getAccountTransactions(accountId)

        if (transactions.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.msg_no_tx)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(40), 0, 0)
                setTextColor(Color.parseColor("#475467"))
                textSize = 15f
            }
            txHistoryContainer.addView(tv)
            return
        }

        transactions.forEach { tx ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(10)
                }
                gravity = Gravity.CENTER_VERTICAL
            }

            val detailsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val sourceTv = TextView(this).apply {
                val displaySource = when(tx.moduleSource) {
                    "Funds" -> getString(R.string.title_funds)
                    "Settlement" -> getString(R.string.title_funds)
                    "Expense" -> getString(R.string.title_expenditure)
                    "Sales" -> getString(R.string.title_sales)
                    "Advance" -> getString(R.string.btn_advance)
                    else -> tx.moduleSource
                }
                text = getString(R.string.label_tx_source, displaySource)
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
                textSize = 15f
            }

            val dateTv = TextView(this).apply {
                text = tx.date
                setTextColor(Color.parseColor("#667085"))
                textSize = 11f
            }

            val remarksTv = TextView(this).apply {
                text = if (tx.remarks.isNullOrEmpty()) "No details" else tx.remarks
                setTextColor(Color.parseColor("#475467"))
                textSize = 13f
                setPadding(0, dpToPx(4), 0, 0)
            }

            detailsLayout.addView(sourceTv)
            detailsLayout.addView(dateTv)
            detailsLayout.addView(remarksTv)

            val amountTv = TextView(this).apply {
                val sign = if (tx.transactionType == "Credit") "+" else "-"
                text = "$sign SAR ${tx.amount.toInt()}"
                setTextColor(if (tx.transactionType == "Credit") Color.parseColor("#12B76A") else Color.parseColor("#F04438"))
                setTypeface(null, Typeface.BOLD)
                textSize = 16f
                gravity = Gravity.END
            }

            card.addView(detailsLayout)
            card.addView(amountTv)
            txHistoryContainer.addView(card)
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
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
}
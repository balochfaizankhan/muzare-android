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
    private lateinit var tvSummaryExpenses: TextView
    private lateinit var tvSummaryAdvances: TextView
    private lateinit var tvSummarySettlements: TextView
    private lateinit var tvNetBalance: TextView
    private lateinit var txHistoryContainer: LinearLayout
    private var accountId: Int = -1
    private val expandedGroups = mutableSetOf<String>()
    private var isFirstLoad = true

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
        tvSummaryExpenses = findViewById(R.id.tvSummaryExpenses)
        tvSummaryAdvances = findViewById(R.id.tvSummaryAdvances)
        tvSummarySettlements = findViewById(R.id.tvSummarySettlements)
        tvNetBalance = findViewById(R.id.tvNetBalance)
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
        textViewAccountBalance.text = "Balance: SAR ${displayBalance.toInt()}"
        
        val color = if (isPartner) {
            if (displayBalance <= 0) "#12B76A" else "#F04438"
        } else {
            if (displayBalance >= 0) "#12B76A" else "#F04438"
        }
        textViewAccountBalance.setTextColor(Color.parseColor(color))

        val transactions = databaseHelper.getAccountTransactions(accountId)
        
        val expensesTotal = transactions.filter { it.moduleSource == "Expense" }.sumOf { it.amount }
        val advancesTotal = transactions.filter { it.moduleSource == "Advance" }.sumOf { it.amount }
        val settlementsTotal = transactions.filter { it.moduleSource == "Settlement" || it.moduleSource == "Funds" }.sumOf { it.amount }
        
        tvSummaryExpenses.text = "SAR ${expensesTotal.toInt()}"
        tvSummaryAdvances.text = "SAR ${advancesTotal.toInt()}"
        tvSummarySettlements.text = "SAR ${settlementsTotal.toInt()}"
        tvNetBalance.text = "Net Balance: SAR ${displayBalance.toInt()}"

        loadTransactions(transactions)
    }

    private fun loadTransactions(transactions: List<DatabaseHelper.AccountTransaction>) {
        txHistoryContainer.removeAllViews()

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

        val groups = transactions.groupBy { tx ->
            when (tx.moduleSource) {
                "Expense" -> "Expenses"
                "Advance" -> "Advances"
                "Settlement", "Funds" -> "Settlements"
                "Sales" -> "Sales / Income"
                else -> "Others"
            }
        }

        // Define order of groups
        val order = listOf("Expenses", "Advances", "Settlements", "Sales / Income", "Others")
        
        if (isFirstLoad) {
            expandedGroups.addAll(order)
            isFirstLoad = false
        }
        
        order.forEach { groupName ->
            val groupTxs = groups[groupName] ?: return@forEach
            addGroupToContainer(groupName, groupTxs)
        }
    }

    private fun addGroupToContainer(groupName: String, txs: List<DatabaseHelper.AccountTransaction>) {
        val totalAmount = txs.sumOf { it.amount }
        val isExpanded = expandedGroups.contains(groupName)

        // Group Header
        val headerView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            isClickable = true
            isFocusable = true
            
            // Background with slight tint
            setBackgroundColor(Color.parseColor("#F9FAFB"))
            
            setOnClickListener {
                if (expandedGroups.contains(groupName)) {
                    expandedGroups.remove(groupName)
                } else {
                    expandedGroups.add(groupName)
                }
                loadAccountData()
            }
        }

        val indicatorTv = TextView(this).apply {
            text = if (isExpanded) "▼" else "▶"
            textSize = 12f
            setTextColor(Color.parseColor("#002B4E"))
            setPadding(0, 0, dpToPx(12), 0)
        }

        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameTv = TextView(this).apply {
            text = groupName
            textSize = 15f
            setTextColor(Color.parseColor("#002B4E"))
            setTypeface(null, Typeface.BOLD)
        }

        val statsTv = TextView(this).apply {
            text = "Total: SAR ${totalAmount.toInt()}  •  ${txs.size} Entries"
            textSize = 12f
            setTextColor(Color.parseColor("#475467"))
        }

        titleLayout.addView(nameTv)
        titleLayout.addView(statsTv)
        
        headerView.addView(indicatorTv)
        headerView.addView(titleLayout)
        
        txHistoryContainer.addView(headerView)

        // Divider after header
        txHistoryContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1))
            setBackgroundColor(Color.parseColor("#EAECF0"))
        })

        if (isExpanded) {
            val itemsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(8), 0, dpToPx(16))
            }

            txs.forEach { tx ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val detailsLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val dateTv = TextView(this).apply {
                    text = tx.date
                    setTextColor(Color.parseColor("#667085"))
                    textSize = 11f
                }

                val remarksTv = TextView(this).apply {
                    text = if (tx.remarks.isNullOrEmpty()) "No details" else tx.remarks
                    setTextColor(Color.parseColor("#344054"))
                    textSize = 14f
                    setPadding(0, dpToPx(2), 0, 0)
                }

                detailsLayout.addView(dateTv)
                detailsLayout.addView(remarksTv)

                val amountTv = TextView(this).apply {
                    val sign = if (tx.transactionType == "Credit") "+" else "-"
                    text = "$sign SAR ${tx.amount.toInt()}"
                    setTextColor(if (tx.transactionType == "Credit") Color.parseColor("#12B76A") else Color.parseColor("#F04438"))
                    setTypeface(null, Typeface.BOLD)
                    textSize = 15f
                    gravity = Gravity.END
                }

                card.addView(detailsLayout)
                card.addView(amountTv)
                itemsContainer.addView(card)
                
                // Inner divider
                itemsContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                        marginStart = dpToPx(14)
                        marginEnd = dpToPx(14)
                    }
                    setBackgroundColor(Color.parseColor("#F2F4F7"))
                })
            }
            txHistoryContainer.addView(itemsContainer)
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
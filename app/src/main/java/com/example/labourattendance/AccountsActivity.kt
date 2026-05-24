package com.example.labourattendance

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class AccountsActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var accountsListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        val root = findViewById<View>(R.id.accountsRoot)
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
        accountsListContainer = findViewById(R.id.accountsListContainer)

        findViewById<Button>(R.id.buttonAddAccount).setOnClickListener { showAddAccountDialog() }
        findViewById<Button>(R.id.buttonProfitLoss).setOnClickListener {
            startActivity(Intent(this, ProfitLossActivity::class.java))
        }

        loadAccounts()
    }

    override fun onResume() {
        super.onResume()
        loadAccounts()
    }

    private fun loadAccounts() {
        accountsListContainer.removeAllViews()
        val accounts = databaseHelper.getAllAccountsWithBalance()

        if (accounts.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.msg_no_accounts)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(40), 0, 0)
                setTextColor(Color.parseColor("#475467"))
                textSize = 15f
            }
            accountsListContainer.addView(tv)
            return
        }

        accounts.forEach { account ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(12)
                }
                elevation = dpToPx(1).toFloat()
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    val intent = Intent(this@AccountsActivity, AccountDetailActivity::class.java)
                    intent.putExtra("ACCOUNT_ID", account.id)
                    startActivity(intent)
                }
            }

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameTv = TextView(this).apply {
                text = account.name
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
                textSize = 17f
            }

            val remarksTv = TextView(this).apply {
                text = if (account.remarks.isNullOrEmpty()) "System Account" else account.remarks
                setTextColor(Color.parseColor("#667085"))
                textSize = 12f
                setPadding(0, dpToPx(2), 0, 0)
            }

            infoLayout.addView(nameTv)
            infoLayout.addView(remarksTv)

            val isPartner = databaseHelper.isAccountPayable(account.id)
            val displayBalance = if (isPartner) -account.balance else account.balance
            
            val balanceTv = TextView(this).apply {
                text = "SAR ${displayBalance.toInt()}"
                // If it's a partner account and business owes them (negative), use blue.
                // If partner owes business (positive), use red.
                // For cash accounts, positive is green, negative is red.
                val color = if (isPartner) {
                    if (displayBalance <= 0) "#004EEB" else "#F04438"
                } else {
                    if (displayBalance >= 0) "#12B76A" else "#F04438"
                }
                setTextColor(Color.parseColor(color))
                setTypeface(null, Typeface.BOLD)
                textSize = 16f
                gravity = Gravity.END
            }

            card.addView(infoLayout)
            card.addView(balanceTv)

            val optionsBtn = ImageButton(this).apply {
                setImageResource(R.drawable.ic_more_vert)
                setBackgroundResource(TypedValue().apply { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, this, true) }.resourceId)
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
                setOnClickListener { showAccountOptionsMenu(it, account) }
            }
            
            val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
            if (role != "viewer") {
                card.addView(optionsBtn)
            }

            accountsListContainer.addView(card)
        }
    }

    private fun showAccountOptionsMenu(anchor: View, account: DatabaseHelper.Account) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Edit")
        popup.menu.add("Delete")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Edit" -> showEditAccountDialog(account)
                "Delete" -> confirmDeleteAccount(account)
            }
            true
        }
        popup.show()
    }

    private fun showEditAccountDialog(account: DatabaseHelper.Account) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        }

        val editName = EditText(this).apply {
            hint = "Account Name"
            setText(account.name)
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }
        val editRemarks = EditText(this).apply {
            hint = "Remarks"
            setText(account.remarks)
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dpToPx(12)
            layoutParams = lp
        }

        container.addView(editName)
        container.addView(editRemarks)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Account")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val name = editName.text.toString().trim()
                val remarks = editRemarks.text.toString().trim()
                if (name.isNotEmpty()) {
                    databaseHelper.updateFundSource(account.id, name, remarks)
                    loadAccounts()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAccount(account: DatabaseHelper.Account) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete '${account.name}'? This will only work if there are no transactions linked to it.")
            .setPositiveButton("Delete") { _, _ ->
                val success = databaseHelper.deleteFundSource(account.id)
                if (success) {
                    Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
                    loadAccounts()
                } else {
                    Toast.makeText(this, "Cannot delete account with transactions", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddAccountDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        }

        val editName = EditText(this).apply {
            hint = "Account Name (e.g. Farm Safe)"
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }
        val editRemarks = EditText(this).apply {
            hint = "Remarks (Optional)"
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dpToPx(12)
            layoutParams = lp
        }

        container.addView(editName)
        container.addView(editRemarks)

        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Account")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = editName.text.toString().trim()
                val remarks = editRemarks.text.toString().trim()
                if (name.isNotEmpty()) {
                    val farmId = databaseHelper.getCurrentFarmId()
                    val values = android.content.ContentValues().apply {
                        put("name", name)
                        put("description", remarks)
                        put("farm_id", farmId)
                    }
                    databaseHelper.writableDatabase.insert("fund_source", null, values)
                    loadAccounts()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
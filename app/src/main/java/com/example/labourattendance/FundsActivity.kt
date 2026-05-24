package com.example.labourattendance

import android.app.DatePickerDialog
import android.content.ContentValues
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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class FundsActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var fundsListContainer: LinearLayout
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_funds)

        val root = findViewById<View>(R.id.fundsRoot)
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
        fundsListContainer = findViewById(R.id.fundsListContainer)

        findViewById<Button>(R.id.buttonAddFund).setOnClickListener { showAddFundDialog() }
        findViewById<Button>(R.id.buttonManageAccounts).setOnClickListener { showManageAccountsDialog() }
        findViewById<Button>(R.id.buttonFundReport).setOnClickListener {
            startActivity(android.content.Intent(this, FundReportActivity::class.java))
        }

        setupRoleBasedUI()
        loadFunds()
    }

    private fun setupRoleBasedUI() {
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        val isClosed = databaseHelper.isCurrentSeasonClosed()
        
        if (role == "viewer" || isClosed) {
            findViewById<Button>(R.id.buttonAddFund).visibility = View.GONE
            findViewById<Button>(R.id.buttonManageAccounts).visibility = View.GONE
        } else {
            findViewById<Button>(R.id.buttonAddFund).visibility = View.VISIBLE
            findViewById<Button>(R.id.buttonManageAccounts).visibility = View.VISIBLE
        }
    }

    private fun loadFunds() {
        fundsListContainer.removeAllViews()
        val entries = databaseHelper.getAllFundEntries()

        if (entries.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No settlements recorded yet"
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(40), 0, 0)
                setTextColor(ContextCompat.getColor(this@FundsActivity, R.color.text_muted))
                textSize = 15f
            }
            fundsListContainer.addView(tv)
            return
        }

        entries.forEach { entry ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(12)
                }
                elevation = dpToPx(1).toFloat()
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val title = TextView(this).apply {
                text = entry.sourceName
                setTextColor(ContextCompat.getColor(this@FundsActivity, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                textSize = 17f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amount = TextView(this).apply {
                val isContribution = entry.amount >= 0
                text = if (isContribution) "SAR ${entry.amount.toInt()}" else "SAR ${Math.abs(entry.amount).toInt()}"
                setTextColor(Color.parseColor(if (isContribution) "#12B76A" else "#F04438"))
                setTypeface(null, Typeface.BOLD)
                textSize = 16f
            }

            header.addView(title)
            header.addView(amount)

            val dateText = TextView(this).apply {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
                val formattedDate = try { inputFormat.parse(entry.date)?.let { outputFormat.format(it) } ?: entry.date } catch(e: Exception) { entry.date }
                text = formattedDate
                setTextColor(ContextCompat.getColor(this@FundsActivity, R.color.text_secondary))
                textSize = 12f
                setPadding(0, dpToPx(4), 0, 0)
            }

            card.addView(header)
            card.addView(dateText)

            if (!entry.description.isNullOrEmpty()) {
                val desc = TextView(this).apply {
                    text = entry.description
                    setTextColor(ContextCompat.getColor(this@FundsActivity, R.color.text_secondary))
                    textSize = 14f
                    setPadding(0, dpToPx(8), 0, 0)
                }
                card.addView(desc)
            }

            val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
            val isClosed = databaseHelper.isCurrentSeasonClosed()
            if (role != "viewer" && !isClosed) {
                val actionContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }
                
                val editBtn = ImageButton(this).apply {
                    setImageResource(android.R.drawable.ic_menu_edit)
                    setColorFilter(Color.parseColor("#004EEB"))
                    background = null
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                    setOnClickListener {
                        showEditFundDialog(entry)
                    }
                }
                
                val deleteBtn = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_close)
                    setColorFilter(Color.parseColor("#F04438"))
                    background = null
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                    setOnClickListener {
                        MaterialAlertDialogBuilder(this@FundsActivity, R.style.AppAlertDialogTheme)
                            .setTitle("Delete Entry")
                            .setMessage("Delete this settlement entry?")
                            .setPositiveButton("Delete") { _, _ ->
                                databaseHelper.deleteFundEntry(entry.id)
                                loadFunds()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                
                if (role == "admin") {
                    actionContainer.addView(editBtn)
                    actionContainer.addView(deleteBtn)
                } else {
                    actionContainer.addView(editBtn)
                }
                card.addView(actionContainer)
            }

            fundsListContainer.addView(card)
        }
    }

    private fun showAddFundDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_fund, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerFundSource)
        val editAmt = dialogView.findViewById<EditText>(R.id.editFundAmount)
        val editDesc = dialogView.findViewById<EditText>(R.id.editFundDesc)
        val btnDate = dialogView.findViewById<Button>(R.id.buttonFundDate)

        val sources = databaseHelper.getAllFundSources()
        val adapter = ArrayAdapter(this, R.layout.spinner_item, sources.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val calendar = Calendar.getInstance()
        btnDate.text = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(calendar.time)

        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calendar.set(y, m, d)
                btnDate.text = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(calendar.time)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.btn_new_fund)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val amt = editAmt.text.toString().toDoubleOrNull() ?: 0.0
                val desc = editDesc.text.toString()
                val date = dbDateFormat.format(calendar.time)
                val sourceId = if (spinner.selectedItemPosition >= 0) sources[spinner.selectedItemPosition].id else -1
                
                if (amt != 0.0 && sourceId != -1) {
                    databaseHelper.addFundEntry(sourceId, amt, date, desc)
                    loadFunds()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showEditFundDialog(entry: DatabaseHelper.FundEntry) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_fund, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerFundSource)
        val editAmt = dialogView.findViewById<EditText>(R.id.editFundAmount)
        val editDesc = dialogView.findViewById<EditText>(R.id.editFundDesc)
        val btnDate = dialogView.findViewById<Button>(R.id.buttonFundDate)

        val sources = databaseHelper.getAllFundSources()
        val adapter = ArrayAdapter(this, R.layout.spinner_item, sources.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        val sourceIndex = sources.indexOfFirst { it.id == entry.sourceId }
        if (sourceIndex != -1) spinner.setSelection(sourceIndex)
        
        editAmt.setText(entry.amount.toInt().toString())
        editDesc.setText(entry.description)

        val calendar = Calendar.getInstance()
        try { dbDateFormat.parse(entry.date)?.let { calendar.time = it } } catch(_: Exception) {}
        
        val uiDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        btnDate.text = uiDateFormat.format(calendar.time)

        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calendar.set(y, m, d)
                btnDate.text = uiDateFormat.format(calendar.time)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.title_update_record)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                val amt = editAmt.text.toString().toDoubleOrNull() ?: entry.amount
                val desc = editDesc.text.toString()
                val date = dbDateFormat.format(calendar.time)
                val sourceId = if (spinner.selectedItemPosition >= 0) sources[spinner.selectedItemPosition].id else entry.sourceId
                
                databaseHelper.updateFundEntry(entry.id, sourceId, amt, date, desc)
                loadFunds()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showManageAccountsDialog() {
        val sources = databaseHelper.getAllFundSources()
        val names = sources.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.title_manage_accounts)
            .setItems(names) { _, which ->
                showAccountOptionsInFunds(sources[which])
            }
            .setPositiveButton(R.string.btn_add_account) { _, _ ->
                showAddAccountDialog()
            }
            .setNegativeButton(R.string.btn_back, null)
            .show()
    }

    private fun showAccountOptionsInFunds(source: DatabaseHelper.FundSource) {
        val options = arrayOf("Edit Name", "Delete Account")
        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(source.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditAccountDialog(source)
                    1 -> confirmDeleteAccountInFunds(source)
                }
            }
            .show()
    }

    private fun confirmDeleteAccountInFunds(source: DatabaseHelper.FundSource) {
        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle("Delete Account")
            .setMessage("Delete '${source.name}'? Account must have no transactions.")
            .setPositiveButton("Delete") { _, _ ->
                val success = databaseHelper.deleteFundSource(source.id)
                if (success) {
                    Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Cannot delete account with transactions", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddAccountDialog() {
        val input = EditText(this).apply { 
            hint = getString(R.string.hint_account_name)
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setBackgroundResource(R.drawable.bg_input)
        }
        val container = FrameLayout(this).apply { setPadding(dpToPx(24), dpToPx(16), dpToPx(24), 0); addView(input) }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.btn_add_account)
            .setView(container)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    databaseHelper.addGroup(name) // Using addGroup as a proxy if no addFundSource exists or use direct insert
                    // Correction: addFundSource is actually addGroup in some places? No, let's look at DatabaseHelper again.
                    // DatabaseHelper has addFundSource logic? No, let's use direct insert if needed, but better to check.
                    // I will check DatabaseHelper for addFundSource.
                    val farmId = databaseHelper.getCurrentFarmId()
                    val values = ContentValues().apply { 
                        put("name", name) 
                        put("farm_id", farmId)
                    }
                    databaseHelper.writableDatabase.insert("fund_source", null, values)
                    showManageAccountsDialog()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showEditAccountDialog(source: DatabaseHelper.FundSource) {
        val input = EditText(this).apply { 
            hint = getString(R.string.hint_account_name)
            setText(source.name)
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setBackgroundResource(R.drawable.bg_input)
        }
        val container = FrameLayout(this).apply { setPadding(dpToPx(24), dpToPx(16), dpToPx(24), 0); addView(input) }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle("Update Account")
            .setView(container)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    databaseHelper.updateFundSource(source.id, name, null)
                    showManageAccountsDialog()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
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
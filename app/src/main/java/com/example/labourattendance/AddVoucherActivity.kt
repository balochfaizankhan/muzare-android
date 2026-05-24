package com.example.labourattendance

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class AddVoucherActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var itemsContainer: LinearLayout
    private lateinit var textViewGrandTotal: TextView
    private lateinit var textViewVoucherNum: TextView
    private lateinit var buttonAddItem: Button
    private lateinit var buttonSelectDate: Button
    private lateinit var buttonSaveVoucher: Button
    private lateinit var spinnerSource: Spinner
    
    private val calendar = Calendar.getInstance()
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val expenseItems = mutableListOf<View>()
    private lateinit var categories: List<DatabaseHelper.ExpCategory>
    private lateinit var fundSources: List<DatabaseHelper.FundSource>
    
    private var editingVoucherId: Int = -1

    private fun getUiDateFormat(): SimpleDateFormat {
        val lang = getSharedPreferences("Settings", MODE_PRIVATE).getString("My_Lang", "en")
        return if (lang == "en") SimpleDateFormat("dd-MM-yyyy", Locale.US) else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private fun getNumberLocale(): Locale {
        val lang = getSharedPreferences("Settings", MODE_PRIVATE).getString("My_Lang", "en")
        return if (lang == "ar") Locale("ar") else Locale.ENGLISH
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_voucher)

        val root = findViewById<View>(R.id.addVoucherRoot)
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
        categories = databaseHelper.getAllExpCategories()
        fundSources = databaseHelper.getAllFundSources()
        
        itemsContainer = findViewById(R.id.itemsContainer)
        textViewGrandTotal = findViewById(R.id.textViewGrandTotal)
        textViewVoucherNum = findViewById(R.id.textViewGeneratedVoucher)
        buttonAddItem = findViewById(R.id.buttonAddItem)
        buttonSelectDate = findViewById(R.id.buttonSelectDate)
        buttonSaveVoucher = findViewById(R.id.buttonSaveVoucher)
        spinnerSource = findViewById(R.id.spinnerAccountSource)

        setupSourceSpinner()

        editingVoucherId = intent.getIntExtra("VOUCHER_ID", -1)
        
        if (editingVoucherId != -1) {
            loadVoucherForEditing(editingVoucherId)
            buttonSaveVoucher.text = getString(R.string.btn_update)
            addDeleteButton(root as LinearLayout)
        } else {
            textViewVoucherNum.text = databaseHelper.generateVoucherNumber(getNumberLocale())
            addNewItemRow()
        }

        updateDateLabel()

        buttonSelectDate.setOnClickListener { showDatePicker() }
        buttonAddItem.setOnClickListener { addNewItemRow() }
        buttonSaveVoucher.setOnClickListener { saveVoucher() }
        
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        val isClosed = databaseHelper.isCurrentSeasonClosed()
        if (role == "viewer" || isClosed) {
            buttonSaveVoucher.visibility = View.GONE
            buttonAddItem.visibility = View.GONE
        }
    }

    private fun setupSourceSpinner() {
        val adapter = ArrayAdapter(this, R.layout.spinner_item, fundSources.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSource.adapter = adapter
    }

    private fun addDeleteButton(root: LinearLayout) {
        val btn = Button(this).apply {
            text = getString(R.string.btn_delete)
            setTextColor(Color.RED)
            background = null
            setOnClickListener {
                MaterialAlertDialogBuilder(this@AddVoucherActivity)
                    .setTitle(R.string.title_confirm)
                    .setMessage(R.string.msg_confirm_delete_voucher)
                    .setPositiveButton(R.string.btn_delete) { _, _ ->
                        databaseHelper.deleteVoucher(editingVoucherId)
                        finish()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        }
        root.addView(btn)
    }

    private fun loadVoucherForEditing(vId: Int) {
        val voucher = databaseHelper.getAllVouchers().find { it.id == vId } ?: return
        textViewVoucherNum.text = voucher.voucherNumber
        
        val parsedDate = dbDateFormat.parse(voucher.date)
        if (parsedDate != null) calendar.time = parsedDate

        val sourceIdx = fundSources.indexOfFirst { it.id == voucher.sourceId }
        if (sourceIdx != -1) spinnerSource.setSelection(sourceIdx)
        
        voucher.items.forEach { item ->
            addNewItemRow(item)
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, y, m, d ->
            calendar.set(y, m, d)
            updateDateLabel()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateLabel() {
        buttonSelectDate.text = "${getString(R.string.btn_select_date)}: ${getUiDateFormat().format(calendar.time)}"
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

    private fun addNewItemRow(existingItem: DatabaseHelper.ExpenseItem? = null) {
        val row = layoutInflater.inflate(R.layout.row_expense_item, null)
        val spinner = row.findViewById<Spinner>(R.id.spinnerCategory)
        val editAmt = row.findViewById<EditText>(R.id.editAmount)
        val editDesc = row.findViewById<EditText>(R.id.editDescription)
        val btnRemove = row.findViewById<ImageView>(R.id.buttonRemoveItem)

        // Setup Spinner with translated names
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories.map { getTranslatedCategory(it.name) })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        if (existingItem != null) {
            val catIdx = categories.indexOfFirst { it.name == existingItem.category }
            if (catIdx != -1) spinner.setSelection(catIdx)
            editAmt.setText(existingItem.amount.toInt().toString())
            editDesc.setText(existingItem.description)
        }

        btnRemove.visibility = if (expenseItems.size > 0 || editingVoucherId != -1) View.VISIBLE else View.GONE

        btnRemove.setOnClickListener {
            if (expenseItems.size > 1) {
                itemsContainer.removeView(row)
                expenseItems.remove(row)
                calculateTotal()
            }
        }

        editAmt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                calculateTotal()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        expenseItems.add(row)
        itemsContainer.addView(row)
        calculateTotal()
    }

    private fun calculateTotal() {
        var total = 0.0
        expenseItems.forEach { row ->
            val amtStr = row.findViewById<EditText>(R.id.editAmount).text.toString()
            total += amtStr.toDoubleOrNull() ?: 0.0
        }
        textViewGrandTotal.text = getString(R.string.value_sar, total.toInt())
    }

    private fun saveVoucher() {
        val voucherNum = textViewVoucherNum.text.toString()
        val date = dbDateFormat.format(calendar.time)
        val itemsToSave = mutableListOf<DatabaseHelper.ExpenseItem>()
        var grandTotal = 0.0

        expenseItems.forEach { row ->
            val spinner = row.findViewById<Spinner>(R.id.spinnerCategory)
            val category = if (spinner.selectedItemPosition != -1) categories[spinner.selectedItemPosition].name else "Others"
            val amtStr = row.findViewById<EditText>(R.id.editAmount).text.toString()
            val desc = row.findViewById<EditText>(R.id.editDescription).text.toString().trim()
            
            val amt = amtStr.toDoubleOrNull() ?: 0.0
            if (amt > 0) {
                itemsToSave.add(DatabaseHelper.ExpenseItem(category = category, amount = amt, description = desc))
                grandTotal += amt
            }
        }

        if (itemsToSave.isEmpty()) {
            Toast.makeText(this, "Please enter an amount for at least one item", Toast.LENGTH_SHORT).show()
            return
        }

        if (editingVoucherId != -1) {
            val success = databaseHelper.updateVoucher(editingVoucherId, date, grandTotal, itemsToSave, fundSources[spinnerSource.selectedItemPosition].id)
            if (success) {
                Toast.makeText(this, R.string.toast_update_success, Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            val user = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "operator") ?: "operator"
            val id = databaseHelper.addVoucher(voucherNum, date, grandTotal, user, itemsToSave, fundSources[spinnerSource.selectedItemPosition].id)
            if (id != -1L) {
                Toast.makeText(this, R.string.msg_voucher_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
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
}
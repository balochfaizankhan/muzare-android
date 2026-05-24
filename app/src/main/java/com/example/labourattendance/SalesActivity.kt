package com.example.labourattendance

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
import java.text.SimpleDateFormat
import java.util.*

class SalesActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var stockContainer: LinearLayout
    private lateinit var salesHistoryContainer: LinearLayout
    private lateinit var editTextSearchStock: EditText
    private lateinit var imageViewClearStockSearch: ImageView
    private var currentStockSearchQuery: String = ""
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales)

        val root = findViewById<View>(R.id.salesRoot)
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
        stockContainer = findViewById(R.id.stockContainer)
        salesHistoryContainer = findViewById(R.id.salesHistoryContainer)
        editTextSearchStock = findViewById(R.id.editTextSearchStock)
        imageViewClearStockSearch = findViewById(R.id.imageViewClearStockSearch)

        val isClosed = databaseHelper.isCurrentSeasonClosed()
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        if (role == "viewer" || isClosed) {
            findViewById<Button>(R.id.buttonNewSale).visibility = View.GONE
        }

        findViewById<Button>(R.id.buttonNewSale).setOnClickListener { showAddSaleDialog() }

        imageViewClearStockSearch.setOnClickListener {
            editTextSearchStock.text.clear()
            currentStockSearchQuery = ""
            loadInventory()
        }

        editTextSearchStock.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentStockSearchQuery = s?.toString()?.trim() ?: ""
                imageViewClearStockSearch.visibility = if (currentStockSearchQuery.isEmpty()) View.GONE else View.VISIBLE
                loadInventory()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        loadData()
    }

    private fun loadData() {
        loadInventory()
        loadSalesHistory()
    }

    private fun loadInventory() {
        stockContainer.removeAllViews()
        val allInventory = databaseHelper.getInventoryStatus()
        
        val inventory = if (currentStockSearchQuery.isEmpty()) {
            allInventory
        } else {
            allInventory.filter { 
                it.vehicleNumber.contains(currentStockSearchQuery, ignoreCase = true) ||
                it.dateTypeName.contains(currentStockSearchQuery, ignoreCase = true)
            }
        }

        if (inventory.isEmpty()) {
            val tv = TextView(this).apply {
                text = if (currentStockSearchQuery.isEmpty()) "No dispatches found in market" else "No matching stock found"
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(20), 0, 0)
                setTextColor(Color.parseColor("#475467"))
            }
            stockContainer.addView(tv)
            return
        }

        inventory.forEach { status ->
            if (status.remainingCount <= 0) return@forEach

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(10)
                }
                elevation = dpToPx(1).toFloat()
            }

            val header = TextView(this).apply {
                text = "${status.date} | ${status.vehicleNumber}"
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
                textSize = 14f
            }

            val details = TextView(this).apply {
                text = status.dateTypeName
                setTextColor(Color.parseColor("#101828"))
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dpToPx(4), 0, dpToPx(8))
            }

            val statsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            statsLayout.addView(createStatItem("Disp", status.dispatchedCount, "#475467"))
            statsLayout.addView(createStatItem("Sold", status.soldCount, "#12B76A"))
            statsLayout.addView(createStatItem("Bal", status.remainingCount, "#004EEB", true))

            card.addView(header)
            card.addView(details)
            card.addView(statsLayout)
            stockContainer.addView(card)
        }
    }

    private fun createStatItem(label: String, value: Int, color: String, isBold: Boolean = false): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            
            val labelTv = TextView(this@SalesActivity).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor("#667085"))
            }
            val valueTv = TextView(this@SalesActivity).apply {
                text = value.toString()
                textSize = 16f
                setTextColor(Color.parseColor(color))
                if (isBold) setTypeface(null, Typeface.BOLD)
            }
            addView(labelTv)
            addView(valueTv)
        }
    }

    private fun loadSalesHistory() {
        salesHistoryContainer.removeAllViews()
        val sales = databaseHelper.getAllSales()

        if (sales.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No sales recorded yet"
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(20), 0, 0)
                setTextColor(Color.parseColor("#475467"))
            }
            salesHistoryContainer.addView(tv)
            return
        }

        sales.forEach { sale ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(10)
                }
            }

            val buyer = TextView(this).apply {
                text = "${sale.buyerName} - ${sale.date}"
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
            }

            val total = TextView(this).apply {
                text = "Total: SAR ${sale.totalAmount.toInt()}"
                setTextColor(Color.parseColor("#12B76A"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dpToPx(4), 0, dpToPx(4))
            }

            val account = TextView(this).apply {
                text = "Received in: ${sale.sourceName ?: "N/A"}"
                setTextColor(Color.parseColor("#475467"))
                textSize = 12f
            }

            card.addView(buyer)
            card.addView(total)
            
            sale.items.forEach { item ->
                val itTv = TextView(this).apply {
                    text = "• ${item.quantity} x ${item.dateTypeName} (${item.vehicleNumber}) @ ${item.unitPrice.toInt()}"
                    setTextColor(Color.parseColor("#101828"))
                    textSize = 13f
                    setPadding(dpToPx(8), dpToPx(2), 0, 0)
                }
                card.addView(itTv)
            }
            card.addView(account)

            val delBtn = Button(this).apply {
                text = "Delete"
                setTextColor(Color.RED)
                background = null
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(40)).apply { gravity = Gravity.END }
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@SalesActivity)
                        .setTitle("Delete Sale")
                        .setMessage("Delete this sale entry?")
                        .setPositiveButton("Delete") { _, _ ->
                            databaseHelper.deleteSale(sale.id)
                            loadData()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            card.addView(delBtn)

            salesHistoryContainer.addView(card)
        }
    }

    private fun showAddSaleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_sale, null)
        val spinnerAccount = dialogView.findViewById<Spinner>(R.id.spinnerSaleAccount)
        val spinnerStock = dialogView.findViewById<Spinner>(R.id.spinnerStockItem)
        val editQty = dialogView.findViewById<EditText>(R.id.editSaleQty)
        val editPrice = dialogView.findViewById<EditText>(R.id.editSalePrice)

        // Setup Accounts
        val accounts = databaseHelper.getAllFundSources()
        val accAdapter = ArrayAdapter(this, R.layout.spinner_item, accounts.map { it.name })
        spinnerAccount.adapter = accAdapter

        // Setup Stock
        val stock = databaseHelper.getInventoryStatus().filter { it.remainingCount > 0 }
        val stockStrings = stock.map { "${it.dateTypeName} - ${it.vehicleNumber} (Rem: ${it.remainingCount})" }
        val stockAdapter = ArrayAdapter(this, R.layout.spinner_item, stockStrings)
        spinnerStock.adapter = stockAdapter

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val buyer = "General Sale"
                val qty = editQty.text.toString().toIntOrNull() ?: 0
                val price = editPrice.text.toString().toDoubleOrNull() ?: 0.0
                val date = dbDateFormat.format(Date())
                
                if (spinnerStock.selectedItemPosition < 0) return@setPositiveButton
                val selectedStock = stock[spinnerStock.selectedItemPosition]
                val accId = if (accounts.isNotEmpty()) accounts[spinnerAccount.selectedItemPosition].id else 0

                if (qty <= 0 || price <= 0) return@setPositiveButton
                
                if (qty > selectedStock.remainingCount) {
                    Toast.makeText(this, R.string.err_no_stock, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val saleItem = DatabaseHelper.SaleItem(
                    dispatchId = selectedStock.dispatchId,
                    dateTypeId = selectedStock.dateTypeId,
                    quantity = qty,
                    unitPrice = price
                )

                databaseHelper.addSale(buyer, date, listOf(saleItem), accId)
                Toast.makeText(this, R.string.msg_sale_saved, Toast.LENGTH_SHORT).show()
                loadData()
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
    private fun Int.spToPx(): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), resources.displayMetrics)
}
package com.example.labourattendance

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.*

class ExpenditureActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var voucherListContainer: LinearLayout
    private lateinit var editTextSearch: EditText
    private lateinit var imageViewClearSearch: ImageView
    
    private var allVouchers: List<DatabaseHelper.Voucher> = emptyList()
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expenditure)

        val root = findViewById<View>(R.id.expenditureRoot)
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
        voucherListContainer = findViewById(R.id.voucherListContainer)
        editTextSearch = findViewById(R.id.editTextSearchVoucher)
        imageViewClearSearch = findViewById(R.id.imageViewClearSearch)

        findViewById<Button>(R.id.buttonNewVoucher).setOnClickListener {
            startActivity(Intent(this, AddVoucherActivity::class.java))
        }
        
        findViewById<Button>(R.id.buttonViewReport).setOnClickListener {
            startActivity(Intent(this, ExpenditureReportActivity::class.java))
        }

        imageViewClearSearch.setOnClickListener {
            editTextSearch.text.clear()
            currentSearchQuery = ""
            displayVouchers()
        }

        editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                imageViewClearSearch.visibility = if (currentSearchQuery.isEmpty()) View.GONE else View.VISIBLE
                displayVouchers()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupRoleBasedUI()
        loadVouchers()
    }

    private fun setupRoleBasedUI() {
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        val isClosed = databaseHelper.isCurrentSeasonClosed()
        
        if (role == "viewer" || isClosed) {
            findViewById<Button>(R.id.buttonNewVoucher).visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadVouchers()
    }

    private fun loadVouchers() {
        allVouchers = databaseHelper.getAllVouchers()
        displayVouchers()
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

    private fun displayVouchers() {
        voucherListContainer.removeAllViews()
        
        val filtered = allVouchers.filter { v ->
            currentSearchQuery.isEmpty() || 
            v.voucherNumber.contains(currentSearchQuery, ignoreCase = true) ||
            v.items.any { getTranslatedCategory(it.category).contains(currentSearchQuery, ignoreCase = true) }
        }

        if (filtered.isEmpty()) {
            val tv = TextView(this).apply {
                text = if (currentSearchQuery.isEmpty()) getString(R.string.msg_no_vouchers) else getString(R.string.msg_no_match_voucher)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(40), 0, 0)
                setTextColor(Color.parseColor("#475467"))
                textSize = 15f
            }
            voucherListContainer.addView(tv)
            return
        }

        filtered.forEach { voucher ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(12)
                }
                elevation = dpToPx(1).toFloat()
                setOnClickListener {
                    val intent = Intent(this@ExpenditureActivity, VoucherDetailActivity::class.java)
                    intent.putExtra("VOUCHER_ID", voucher.id)
                    startActivity(intent)
                }
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val vNumView = TextView(this).apply {
                text = voucher.voucherNumber
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_badge)
                setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dpToPx(12)
                }
            }

            val dateView = TextView(this).apply {
                text = voucher.date
                textSize = 14f
                setTextColor(Color.parseColor("#475467"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            header.addView(vNumView)
            header.addView(dateView)

            val itemsView = TextView(this).apply {
                val summary = voucher.items.joinToString("\n") { 
                    "${getTranslatedCategory(it.category)}: ${getString(R.string.value_sar, it.amount.toInt())}" 
                }
                text = summary
                setTextColor(Color.parseColor("#101828"))
                textSize = 15f
                setPadding(0, dpToPx(10), 0, dpToPx(4))
            }

            val totalView = TextView(this).apply {
                text = getString(R.string.label_total_amount, voucher.totalAmount.toInt())
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
                textSize = 16f
            }

            card.addView(header)
            card.addView(itemsView)
            card.addView(totalView)
            voucherListContainer.addView(card)
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
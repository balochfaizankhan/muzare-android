package com.example.labourattendance

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MonthlySummaryActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var textViewSelectedMonth: TextView
    private lateinit var buttonSelectMonth: Button
    private lateinit var summaryContainer: LinearLayout
    private val calendar = Calendar.getInstance()
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monthly_summary)

        val root = findViewById<View>(R.id.monthlySummaryRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = DatabaseHelper(this)
        textViewSelectedMonth = findViewById(R.id.textViewSelectedMonth)
        buttonSelectMonth = findViewById(R.id.buttonSelectMonth)
        summaryContainer = findViewById(R.id.summaryContainer)

        updateMonthLabel()
        loadSummary()

        buttonSelectMonth.setOnClickListener {
            showMonthPicker()
        }
    }

    private fun updateMonthLabel() {
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        textViewSelectedMonth.text = getString(R.string.label_month, monthName)
    }

    private fun showMonthPicker() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            setBackgroundColor(Color.WHITE)
        }

        val pickersLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val monthPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 11
            displayedValues = arrayOf(
                getString(R.string.month_jan), getString(R.string.month_feb), getString(R.string.month_mar),
                getString(R.string.month_apr), getString(R.string.month_may), getString(R.string.month_jun),
                getString(R.string.month_jul), getString(R.string.month_aug), getString(R.string.month_sep),
                getString(R.string.month_oct), getString(R.string.month_nov), getString(R.string.month_dec)
            )
            value = calendar.get(Calendar.MONTH)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textColor = Color.parseColor("#101828")
            }
        }

        val yearPicker = NumberPicker(this).apply {
            minValue = 2020
            maxValue = 2050
            value = calendar.get(Calendar.YEAR)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textColor = Color.parseColor("#101828")
            }
        }

        pickersLayout.addView(monthPicker)
        pickersLayout.addView(yearPicker)
        rootLayout.addView(pickersLayout)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.btn_select_month)
            .setView(rootLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                calendar.set(Calendar.YEAR, yearPicker.value)
                calendar.set(Calendar.MONTH, monthPicker.value)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                updateMonthLabel()
                loadSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadSummary() {
        summaryContainer.removeAllViews()
        val monthKey = monthFormat.format(calendar.time)
        val summaryList = databaseHelper.getMonthlySummary(monthKey)
        
        // Pre-fetch all labours and user role for efficiency and financial display
        val allLabours = databaseHelper.getAllLabours()
        val labourMap = allLabours.associateBy { it.id }
        val role = getSharedPreferences("Settings", Context.MODE_PRIVATE).getString("User_Role", "viewer")

        if (summaryList.isEmpty()) {
            val textView = TextView(this).apply {
                text = getString(R.string.no_labour_found)
                textSize = 16f
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                gravity = Gravity.CENTER
            }
            summaryContainer.addView(textView)
            return
        }

        // Calculate Grand Totals
        var totalP = 0
        var totalH = 0
        var totalA = 0
        var totalAdv = 0.0
        var totalEarn = 0.0
        
        summaryList.forEach { s ->
            totalP += s.presentCount
            totalH += s.halfDayCount
            totalA += s.absentCount
            totalAdv += s.totalAdvance
            val l = labourMap[s.labourId]
            val w = l?.wage ?: 0.0
            
            val labourEarned = when (l?.labourType) {
                "MONTHLY" -> {
                    calculateMonthlySalary(l, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
                }
                "DAILY_WAGE" -> {
                    (s.presentCount * w) + (s.halfDayCount * (w / 2))
                }
                else -> 0.0
            }
            totalEarn += labourEarned
        }

        // Add Grand Total Summary Card
        val grandCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setBackgroundResource(R.drawable.bg_card)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, dpToPx(24))
            layoutParams = lp
            elevation = dpToPx(2).toFloat()
        }
        
        grandCard.addView(TextView(this).apply {
            text = getString(R.string.report_grand_total_row)
            textSize = 18f
            setTextColor(Color.parseColor("#002B4E"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        
        grandCard.addView(TextView(this).apply {
            text = getString(R.string.report_individual_summary, totalP, totalH, totalA, totalAdv.toInt())
            textSize = 15f
            setTextColor(Color.parseColor("#475467"))
            setPadding(0, dpToPx(8), 0, 0)
        })
        
        if (role != "viewer") {
            val net = totalEarn - totalAdv
            grandCard.addView(TextView(this).apply {
                text = "${getString(R.string.label_total_earnings)}: ${totalEarn.toInt()} ${getString(R.string.report_sar)}\n" +
                       "${getString(R.string.label_net_balance)}: ${net.toInt()} ${getString(R.string.report_sar)}"
                textSize = 15f
                setTextColor(if (net >= 0) Color.parseColor("#12B76A") else Color.parseColor("#F04438"))
                setPadding(0, dpToPx(8), 0, 0)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        }
        
        summaryContainer.addView(grandCard)

        summaryList.forEachIndexed { index, summary ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                setBackgroundResource(R.drawable.bg_card)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, dpToPx(10))
                layoutParams = lp
                elevation = dpToPx(1).toFloat()
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val serialView = TextView(this).apply {
                text = (index + 1).toString()
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setBackgroundResource(R.drawable.bg_badge)
                layoutParams = LinearLayout.LayoutParams(dpToPx(34), dpToPx(34)).apply {
                    if (resources.configuration.layoutDirection == android.util.LayoutDirection.RTL) {
                        marginStart = dpToPx(12)
                    } else {
                        marginEnd = dpToPx(12)
                    }
                }
            }

            val nameView = TextView(this).apply {
                text = summary.labourName
                textSize = 18f
                setTextColor(Color.parseColor("#102A43"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Get labour from pre-fetched map
            val labour = labourMap[summary.labourId]
            val typeDisplayName = when(labour?.labourType) {
                "CONTRACT" -> getString(R.string.labour_type_contract)
                "PRODUCTION_BASED" -> getString(R.string.labour_type_production)
                "MONTHLY" -> getString(R.string.labour_type_monthly)
                else -> getString(R.string.labour_type_daily)
            }

            val typeView = TextView(this).apply {
                text = typeDisplayName
                textSize = 13f
                setTextColor(Color.parseColor("#475467"))
                setPadding(dpToPx(46), 0, 0, 0) // Align with name (badge width + margin)
                if (resources.configuration.layoutDirection == android.util.LayoutDirection.RTL) {
                    setPadding(0, 0, dpToPx(46), 0)
                }
            }

            val detailsView = TextView(this).apply {
                text = getString(R.string.summary_counts, summary.presentCount, summary.halfDayCount, summary.absentCount, summary.totalAdvance.toInt())
                textSize = 15f
                setTextColor(Color.parseColor("#243B53"))
                setPadding(0, dpToPx(10), 0, 0)
            }

            headerRow.addView(serialView)
            headerRow.addView(nameView)
            card.addView(headerRow)
            card.addView(typeView)
            card.addView(detailsView)

            // Add financial info if the user is not a viewer
            if (role != "viewer") {
                val wage = labour?.wage ?: 0.0
                val earnings = when (labour?.labourType) {
                    "MONTHLY" -> calculateMonthlySalary(labour, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
                    "DAILY_WAGE" -> (summary.presentCount * wage) + (summary.halfDayCount * (wage / 2))
                    else -> 0.0
                }
                val net = earnings - summary.totalAdvance
                
                val financialView = TextView(this).apply {
                    val wageStr = getString(R.string.label_wage, wage.toInt().toString())
                    val netStr = getString(R.string.label_net, "${getString(R.string.report_sar)} ${net.toInt()}")
                    val earningsStr = "${getString(R.string.label_total_earnings)}: ${getString(R.string.report_sar)} ${earnings.toInt()}"
                    
                    text = "$wageStr  |  $earningsStr\n$netStr"
                    textSize = 14f
                    setTextColor(if (net >= 0) Color.parseColor("#12B76A") else Color.parseColor("#F04438"))
                    setPadding(0, dpToPx(8), 0, 0)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                card.addView(financialView)
                
                card.setOnClickListener {
                    // Show profile if clicked
                    showLabourProfile(summary.labourId)
                }
            }

            summaryContainer.addView(card)
        }
    }

    private fun showLabourProfile(labourId: Int) {
        val stats = databaseHelper.getLabourStats(labourId) ?: return
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(24))
        }

        fun createStatRow(label: String, value: String, isBold: Boolean = false, color: Int = Color.parseColor("#101828")): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpToPx(8)
                }
                
                val labelView = TextView(this@MonthlySummaryActivity).apply {
                    text = label
                    textSize = 14f
                    setTextColor(Color.parseColor("#475467"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                
                val valueView = TextView(this@MonthlySummaryActivity).apply {
                    text = value
                    textSize = 15f
                    setTextColor(color)
                    if (isBold) setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.END
                }
                
                addView(labelView)
                addView(valueView)
            }
        }

        val sectionTitleStyle = { tv: TextView ->
            tv.apply {
                textSize = 16f
                setTextColor(Color.parseColor("#101828"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dpToPx(16), 0, dpToPx(4))
            }
        }

        container.addView(TextView(this).apply {
            text = getString(R.string.label_attendance_stats)
            sectionTitleStyle(this)
        })
        
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isEffectiveActive = stats.labour.endDate.isNullOrBlank() || stats.labour.endDate!! >= todayStr
        container.addView(createStatRow(getString(R.string.label_status), if(isEffectiveActive) getString(R.string.status_active) else getString(R.string.status_inactive), color = if(isEffectiveActive) Color.parseColor("#12B76A") else Color.parseColor("#F04438")))
        
        val typeDisplayName = when(stats.labour.labourType) {
            "CONTRACT" -> getString(R.string.labour_type_contract)
            "PRODUCTION_BASED" -> getString(R.string.labour_type_production)
            "MONTHLY" -> getString(R.string.labour_type_monthly)
            else -> getString(R.string.labour_type_daily)
        }
        container.addView(createStatRow(getString(R.string.label_labour_type), typeDisplayName))
        container.addView(createStatRow(getString(R.string.status_present), stats.presentCount.toString()))
        container.addView(createStatRow(getString(R.string.status_half), stats.halfDayCount.toString()))
        container.addView(createStatRow(getString(R.string.status_absent), stats.absentCount.toString()))

        val role = getSharedPreferences("Settings", Context.MODE_PRIVATE).getString("User_Role", "viewer")
        if (role != "viewer") {
            container.addView(TextView(this).apply {
                text = getString(R.string.label_financial_overview)
                sectionTitleStyle(this)
            })
            
            container.addView(createStatRow(getString(R.string.hint_daily_wage), "SAR ${stats.labour.wage.toInt()}"))
            container.addView(createStatRow(getString(R.string.label_total_earnings), "SAR ${stats.totalEarnings.toInt()}", isBold = true, color = Color.parseColor("#12B76A")))
            container.addView(createStatRow(getString(R.string.btn_advance), "SAR ${stats.totalAdvance.toInt()}", color = Color.parseColor("#F04438")))
            
            val balanceColor = if (stats.netBalance >= 0) Color.parseColor("#002B4E") else Color.parseColor("#F04438")
            container.addView(createStatRow(getString(R.string.label_net_balance), "SAR ${stats.netBalance.toInt()}", isBold = true, color = balanceColor))
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(stats.labour.name)
            .setView(container)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    private fun calculateMonthlySalary(labour: DatabaseHelper.Labour, year: Int, month: Int): Double {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        val monthStart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, daysInMonth)
        val monthEnd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        
        val effectiveStart = if (labour.joinDate != null && labour.joinDate > monthStart) labour.joinDate else monthStart
        val effectiveEnd = if (labour.endDate != null && labour.endDate < monthEnd) labour.endDate else monthEnd
        
        if (effectiveStart > monthEnd || effectiveEnd < monthStart) return 0.0
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = sdf.parse(effectiveStart) ?: return 0.0
        val end = sdf.parse(effectiveEnd) ?: return 0.0
        
        val activeDays = ((end.time - start.time) / (1000 * 60 * 60 * 24)).toInt() + 1
        return (labour.wage / daysInMonth) * activeDays
    }

    private fun loadLocale() {
        val prefs = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
        val lang = prefs.getString("My_Lang", "en") ?: "en"
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        if (lang == "ar" || lang == "ur") {
            config.setLayoutDirection(locale)
        } else {
            config.setLayoutDirection(java.util.Locale.ENGLISH)
        }
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun dpToPx(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}

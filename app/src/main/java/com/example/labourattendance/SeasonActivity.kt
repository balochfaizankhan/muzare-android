package com.example.labourattendance

import android.app.DatePickerDialog
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

class SeasonActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var seasonListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_season)

        val root = findViewById<View>(R.id.seasonRoot)
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
        seasonListContainer = findViewById(R.id.seasonListContainer)

        findViewById<Button>(R.id.buttonStartNewSeason).setOnClickListener { showStartNewSeasonDialog() }

        setupRoleBasedUI()
        loadSeasons()
    }

    private fun setupRoleBasedUI() {
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        if (role == "viewer") {
            findViewById<Button>(R.id.buttonStartNewSeason).visibility = View.GONE
        }
    }

    private fun loadSeasons() {
        seasonListContainer.removeAllViews()
        val seasons = databaseHelper.getAllSeasons()
        val currentId = databaseHelper.getCurrentSeasonId()

        seasons.forEach { season ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundResource(if (season.id == currentId) R.drawable.bg_card_selected else R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(12)
                }
                elevation = dpToPx(1).toFloat()
                setOnClickListener {
                    if (season.id != currentId) {
                        confirmSwitchSeason(season)
                    }
                }
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val title = TextView(this).apply {
                text = season.name
                setTextColor(ContextCompat.getColor(this@SeasonActivity, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val statusBadge = TextView(this).apply {
                text = if (season.isActive) "ACTIVE" else if (season.isClosed) "CLOSED" else "INACTIVE"
                textSize = 11f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
                setBackgroundResource(if (season.isActive) R.drawable.bg_badge_active else R.drawable.bg_badge_inactive)
            }

            header.addView(title)
            header.addView(statusBadge)

            val detailsText = "Year: ${season.year} | Started: ${season.startDate}${if (season.endDate != null) " | Ended: ${season.endDate}" else ""}"
            val details = TextView(this).apply {
                text = detailsText
                setTextColor(ContextCompat.getColor(this@SeasonActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(0, dpToPx(4), 0, 0)
            }

            card.addView(header)
            card.addView(details)

            if (season.isActive && !season.isClosed) {
                val closeBtn = com.google.android.material.button.MaterialButton(this).apply {
                    text = "Close Season"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F04438"))
                    cornerRadius = dpToPx(8)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(40))
                    lp.topMargin = dpToPx(12)
                    lp.gravity = Gravity.END
                    layoutParams = lp
                    setOnClickListener { confirmCloseSeason(season) }
                }
                // Only allow admin to close season
                val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
                if (role == "admin") {
                    card.addView(closeBtn)
                }
            }

            seasonListContainer.addView(card)
        }
    }

    private fun confirmSwitchSeason(season: DatabaseHelper.Season) {
        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle("Switch Season")
            .setMessage("Do you want to switch to '${season.name}'? You will only see data for that period.")
            .setPositiveButton("Switch") { _, _ ->
                databaseHelper.setCurrentSeasonId(season.id)
                Toast.makeText(this, "Switched to ${season.name}", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmCloseSeason(season: DatabaseHelper.Season) {
        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle("Close Season")
            .setMessage("Are you sure you want to close '${season.name}'? This will lock all records for this period. You should start a new season immediately after.")
            .setPositiveButton("Close Now") { _, _ ->
                if (databaseHelper.closeSeason(season.id)) {
                    Toast.makeText(this, "Season Closed", Toast.LENGTH_SHORT).show()
                    loadSeasons()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showStartNewSeasonDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_farm, null) // Reusing farm dialog structure for simplicity
        val nameInput = view.findViewById<EditText>(R.id.editTextFarmName).apply { hint = "Season Name (e.g. 2027 Season)" }
        val yearInput = view.findViewById<EditText>(R.id.editTextLocation).apply { 
            hint = "Year (e.g. 2027)" 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val dateBtn = view.findViewById<EditText>(R.id.editTextOwner).apply {
            hint = "Start Date"
            isFocusable = false
            isClickable = true
        }
        view.findViewById<View>(R.id.editTextRemarks).visibility = View.GONE

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateBtn.setText(sdf.format(calendar.time))

        dateBtn.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calendar.set(y, m, d)
                dateBtn.setText(sdf.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle("Start New Season")
            .setView(view)
            .setPositiveButton("Start Season") { _, _ ->
                val name = nameInput.text.toString().trim()
                val year = yearInput.text.toString().toIntOrNull() ?: calendar.get(Calendar.YEAR)
                val date = dateBtn.text.toString()
                
                if (name.isNotEmpty()) {
                    val id = databaseHelper.startNewSeason(name, year, date)
                    if (id != -1L) {
                        Toast.makeText(this, "New Season Started: $name", Toast.LENGTH_LONG).show()
                        finish()
                    }
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

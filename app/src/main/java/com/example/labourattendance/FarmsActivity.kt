package com.example.labourattendance

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FarmsActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var farmsListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_farms)

        val root = findViewById<View>(R.id.farmsRoot)
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
        farmsListContainer = findViewById(R.id.farmsListContainer)

        findViewById<Button>(R.id.buttonAddFarm).setOnClickListener {
            showAddFarmDialog()
        }

        loadFarms()
    }

    private fun loadFarms() {
        farmsListContainer.removeAllViews()
        val farms = databaseHelper.getAllFarms()
        val currentFarmId = databaseHelper.getCurrentFarmId()

        farms.forEach { farm ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundResource(if (farm.id == currentFarmId) R.drawable.bg_card_selected else R.drawable.bg_card)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, dpToPx(12))
                layoutParams = lp
                setOnClickListener {
                    if (farm.id != currentFarmId) {
                        databaseHelper.setCurrentFarmId(farm.id)
                        Toast.makeText(this@FarmsActivity, getString(R.string.toast_farm_switched, farm.name), Toast.LENGTH_SHORT).show()
                        loadFarms()
                    }
                }
                setOnLongClickListener {
                    showEditFarmDialog(farm)
                    true
                }
            }

            val nameView = TextView(this).apply {
                text = farm.name
                textSize = 18f
                setTextColor(Color.parseColor("#102A43"))
                setTypeface(null, Typeface.BOLD)
            }

            val locationView = TextView(this).apply {
                text = farm.location ?: ""
                textSize = 14f
                setTextColor(Color.parseColor("#475467"))
                visibility = if (farm.location.isNullOrEmpty()) View.GONE else View.VISIBLE
            }

            val ownerView = TextView(this).apply {
                text = getString(R.string.label_owner_display, farm.owner ?: "-")
                textSize = 14f
                setTextColor(Color.parseColor("#475467"))
            }

            if (farm.id == currentFarmId) {
                val activeBadge = TextView(this).apply {
                    text = getString(R.string.label_active)
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_badge)
                    setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.gravity = Gravity.END
                    layoutParams = lp
                }
                card.addView(activeBadge)
            }

            card.addView(nameView)
            card.addView(locationView)
            card.addView(ownerView)
            farmsListContainer.addView(card)
        }
    }

    private fun showAddFarmDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_farm, null)
        val nameInput = view.findViewById<EditText>(R.id.editTextFarmName)
        val locationInput = view.findViewById<EditText>(R.id.editTextLocation)
        val ownerInput = view.findViewById<EditText>(R.id.editTextOwner)
        val remarksInput = view.findViewById<EditText>(R.id.editTextRemarks)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_add_farm)
            .setView(view)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val name = nameInput.text.toString()
                if (name.isNotEmpty()) {
                    databaseHelper.addFarm(name, locationInput.text.toString(), ownerInput.text.toString(), remarksInput.text.toString())
                    Toast.makeText(this, R.string.toast_farm_added, Toast.LENGTH_SHORT).show()
                    loadFarms()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showEditFarmDialog(farm: DatabaseHelper.Farm) {
        val view = layoutInflater.inflate(R.layout.dialog_add_farm, null)
        val nameInput = view.findViewById<EditText>(R.id.editTextFarmName)
        val locationInput = view.findViewById<EditText>(R.id.editTextLocation)
        val ownerInput = view.findViewById<EditText>(R.id.editTextOwner)
        val remarksInput = view.findViewById<EditText>(R.id.editTextRemarks)

        nameInput.setText(farm.name)
        locationInput.setText(farm.location)
        ownerInput.setText(farm.owner)
        remarksInput.setText(farm.remarks)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_edit_farm)
            .setView(view)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                val name = nameInput.text.toString()
                if (name.isNotEmpty()) {
                    databaseHelper.updateFarm(farm.id, name, locationInput.text.toString(), ownerInput.text.toString(), remarksInput.text.toString())
                    loadFarms()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun loadLocale() {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
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
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }
}

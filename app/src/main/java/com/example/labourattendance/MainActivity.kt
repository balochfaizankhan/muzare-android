package com.example.labourattendance

import android.app.DatePickerDialog
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var editTextSearchLabour: EditText
    private lateinit var imageViewClearSearch: ImageView
    private lateinit var buttonAddLabour: Button
    private lateinit var buttonAdvance: Button
    private lateinit var buttonOpenAttendance: Button
    private lateinit var buttonOpenReport: Button
    private lateinit var buttonManageGroups: Button
    private lateinit var labourListContainer: LinearLayout
    private var currentSearchQuery: String = ""
    private var selectedGroupIds = mutableSetOf<Int>()
    private var selectedTypes = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.mainRoot)
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

        editTextSearchLabour = findViewById(R.id.editTextSearchLabour)
        imageViewClearSearch = findViewById(R.id.imageViewClearSearch)
        buttonAddLabour = findViewById(R.id.buttonAddLabour)
        buttonAdvance = findViewById(R.id.buttonAdvance)
        buttonOpenAttendance = findViewById(R.id.buttonOpenAttendance)
        buttonOpenReport = findViewById(R.id.buttonOpenReport)
        buttonManageGroups = findViewById(R.id.buttonManageGroups)
        labourListContainer = findViewById(R.id.labourListContainer)

        buttonAddLabour.setOnClickListener { showAddLabourDialog() }
        buttonAdvance.setOnClickListener { startActivity(Intent(this, AdvanceActivity::class.java)) }
        buttonOpenAttendance.setOnClickListener { startActivity(Intent(this, AttendanceActivity::class.java)) }
        buttonOpenReport.setOnClickListener { startActivity(Intent(this, ReportActivity::class.java)) }
        buttonManageGroups.setOnClickListener { showGroupManagementDialog() }

        findViewById<ImageButton>(R.id.buttonLabourListMenu).setOnClickListener { view ->
            showLabourListMenu(view)
        }

        imageViewClearSearch.setOnClickListener {
            editTextSearchLabour.text.clear()
            currentSearchQuery = ""
            loadLabourList()
        }

        editTextSearchLabour.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                imageViewClearSearch.visibility = if (currentSearchQuery.isEmpty()) View.GONE else View.VISIBLE
                loadLabourList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupRoleBasedUI()
    }

    private fun setupRoleBasedUI() {
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        val isClosed = databaseHelper.isCurrentSeasonClosed()
        
        if (role == "viewer" || isClosed) {
            buttonAddLabour.visibility = View.GONE
            buttonManageGroups.visibility = View.GONE
            buttonAdvance.visibility = View.GONE
            findViewById<ImageButton>(R.id.buttonLabourListMenu).visibility = View.GONE
        } else {
            buttonAddLabour.visibility = View.VISIBLE
            buttonManageGroups.visibility = View.VISIBLE
            buttonAdvance.visibility = View.VISIBLE
            findViewById<ImageButton>(R.id.buttonLabourListMenu).visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        loadLabourList()
    }

    private fun loadLabourList() {
        labourListContainer.removeAllViews()
        val allLabours = if (selectedGroupIds.isEmpty()) {
            databaseHelper.getAllLabours()
        } else {
            selectedGroupIds.flatMap { databaseHelper.getLaboursByGroup(it) }.distinctBy { it.id }
        }
        
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val labourList = allLabours.filter {
            val matchesSearch = currentSearchQuery.isEmpty() || it.name.contains(currentSearchQuery, ignoreCase = true)
            val matchesType = selectedTypes.isEmpty() || selectedTypes.contains(it.labourType)
            matchesSearch && matchesType
        }

        if (labourList.isEmpty()) {
            val tv = TextView(this).apply {
                text = if (currentSearchQuery.isEmpty()) getString(R.string.no_labour_found) else getString(R.string.msg_no_match_search)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(32), 0, 0)
                setTextColor(Color.parseColor("#475467"))
                textSize = 15f
            }
            labourListContainer.addView(tv)
            return
        }

        val allGroups = databaseHelper.getAllGroups()

        labourList.forEachIndexed { index, labour ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(8)
                }
                elevation = dpToPx(1).toFloat()
            }

            val serialView = createSerialTextView(index + 1)

            val nameLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { showLabourProfileDialog(labour.id) }
            }

            val nameView = TextView(this).apply {
                text = labour.name
                textSize = 17f
                setTextColor(Color.parseColor("#002B4E"))
                setTypeface(null, Typeface.BOLD)
            }

            val groupData = allGroups.find { it.id == labour.groupId }
            val groupName = groupData?.name ?: getString(R.string.group_general)
            val typeDisplayName = when(labour.labourType) {
                "CONTRACT" -> getString(R.string.labour_type_contract)
                "PRODUCTION_BASED" -> getString(R.string.labour_type_production)
                "MONTHLY" -> getString(R.string.labour_type_monthly)
                else -> getString(R.string.labour_type_daily)
            }
            val isEffectiveActive = labour.endDate.isNullOrBlank() || 
                                    labour.endDate == "null" || 
                                    labour.endDate!!.length < 10 || 
                                    labour.endDate!! >= todayStr
            val statusLabel = if (isEffectiveActive) getString(R.string.status_active) else getString(R.string.status_inactive)
            val statusColor = if (isEffectiveActive) "#12B76A" else "#F04438"

            val groupView = TextView(this).apply {
                val baseText = "${if (labour.groupId == 1) getString(R.string.group_general) else groupName} • $typeDisplayName • "
                val fullText = "$baseText$statusLabel"
                val spannable = android.text.SpannableString(fullText)
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(Color.parseColor(statusColor)),
                    baseText.length,
                    fullText.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    baseText.length,
                    fullText.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text = spannable
                textSize = 13f
                setTextColor(Color.parseColor("#475467"))
            }

            nameLayout.addView(nameView)
            nameLayout.addView(groupView)

            card.addView(serialView)
            card.addView(nameLayout)

            val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
            val isClosed = databaseHelper.isCurrentSeasonClosed()
            
            if (role != "viewer" && !isClosed) {
                val updateBtn = com.google.android.material.button.MaterialButton(this).apply {
                    text = getString(R.string.btn_update_icon)
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#002B4E"))
                    cornerRadius = dpToPx(12)
                    insetTop = 0
                    insetBottom = 0
                    minHeight = dpToPx(44)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dpToPx(8)
                    }
                    setPadding(dpToPx(24), 0, dpToPx(24), 0)
                    setOnClickListener { showEditDialog(labour.id, labour.name) }
                }
                card.addView(updateBtn)
            }

            labourListContainer.addView(card)
        }
    }

    private fun createSerialTextView(number: Int): TextView {
        return TextView(this).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundResource(R.drawable.bg_badge)
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                marginEnd = dpToPx(12)
            }
        }
    }

    private fun showLabourListMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.action_filter_by_group))
        popup.menu.add(0, 3, 1, getString(R.string.action_filter_by_type))
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        if (role == "admin") {
            popup.menu.add(0, 2, 2, getString(R.string.action_delete_labour))
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showGroupsFilterDialog()
                2 -> showMultiDeleteLabourDialog()
                3 -> showTypesFilterDialog()
            }
            true
        }
        popup.show()
    }

    private fun showTypesFilterDialog() {
        val typeOptions = listOf("DAILY_WAGE", "CONTRACT", "PRODUCTION_BASED", "MONTHLY")
        val typeDisplayNames = listOf(
            getString(R.string.labour_type_daily),
            getString(R.string.labour_type_contract),
            getString(R.string.labour_type_production),
            getString(R.string.labour_type_monthly)
        )
        val names = typeDisplayNames.toTypedArray()
        val checked = BooleanArray(typeOptions.size) { selectedTypes.contains(typeOptions[it]) }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.action_filter_by_type)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) selectedTypes.add(typeOptions[which]) else selectedTypes.remove(typeOptions[which])
            }
            .setPositiveButton(R.string.btn_apply) { _, _ -> loadLabourList() }
            .setNeutralButton(R.string.btn_select_all) { _, _ ->
                selectedTypes.clear()
                selectedTypes.addAll(typeOptions)
                loadLabourList()
            }
            .setNegativeButton(R.string.btn_clear_all) { _, _ ->
                selectedTypes.clear()
                loadLabourList()
            }
            .show()
    }

    private fun showGroupsFilterDialog() {
        val groups = databaseHelper.getAllGroups()
        val names = groups.map { if (it.id == 1) getString(R.string.group_general) else it.name }.toTypedArray()
        val checked = BooleanArray(groups.size) { selectedGroupIds.contains(groups[it].id) }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.title_filter_groups)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) selectedGroupIds.add(groups[which].id) else selectedGroupIds.remove(groups[which].id)
            }
            .setPositiveButton(R.string.btn_apply) { _, _ -> loadLabourList() }
            .setNeutralButton(R.string.btn_select_all) { _, _ ->
                selectedGroupIds.clear()
                groups.forEach { selectedGroupIds.add(it.id) }
                loadLabourList()
            }
            .setNegativeButton(R.string.btn_clear_all) { _, _ ->
                selectedGroupIds.clear()
                loadLabourList()
            }
            .show()
    }

    private fun showMultiDeleteLabourDialog() {
        val all = databaseHelper.getAllLabours()
        if (all.isEmpty()) return
        val names = all.map { it.name }.toTypedArray()
        val checked = BooleanArray(all.size) { false }
        val toDelete = mutableSetOf<Int>()

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.action_delete_labour)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) toDelete.add(all[which].id) else toDelete.remove(all[which].id)
            }
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                if (toDelete.isEmpty()) return@setPositiveButton
                MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
                    .setTitle(R.string.title_confirm)
                    .setMessage(getString(R.string.msg_confirm_delete, "${toDelete.size} labourers"))
                    .setPositiveButton(R.string.btn_remove) { _, _ ->
                        toDelete.forEach { databaseHelper.deleteLabour(it) }
                        loadLabourList()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showAddLabourDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_labour, null)
        val title = dialogView.findViewById<TextView>(R.id.dialog_title)
        val message = dialogView.findViewById<TextView>(R.id.dialog_message)
        val editName = dialogView.findViewById<EditText>(R.id.edit_labour_name)
        val editWage = dialogView.findViewById<EditText>(R.id.edit_labour_wage)
        val editRemarks = dialogView.findViewById<EditText>(R.id.edit_remarks)
        val spinnerGroup = dialogView.findViewById<Spinner>(R.id.spinner_group)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_labour_type)
        val btnJoinDate = dialogView.findViewById<Button>(R.id.btn_join_date)
        
        title.text = getString(R.string.btn_add_labour)
        message.text = getString(R.string.msg_add_labour_info)
        
        var selectedJoinDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        btnJoinDate.text = selectedJoinDate
        
        btnJoinDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedJoinDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                btnJoinDate.text = selectedJoinDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val groups = databaseHelper.getAllGroups()
        spinnerGroup.adapter = ArrayAdapter(this, R.layout.spinner_item, groups.map { if (it.id == 1) getString(R.string.group_general) else it.name })

        val typeOptions = listOf("DAILY_WAGE", "CONTRACT", "PRODUCTION_BASED", "MONTHLY")
        val typeDisplayNames = listOf(
            getString(R.string.labour_type_daily),
            getString(R.string.labour_type_contract),
            getString(R.string.labour_type_production),
            getString(R.string.labour_type_monthly)
        )
        spinnerType.adapter = ArrayAdapter(this, R.layout.spinner_item, typeDisplayNames)

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val name = editName.text.toString().trim()
                val wage = editWage.text.toString().toDoubleOrNull() ?: 0.0
                val group = groups[spinnerGroup.selectedItemPosition]
                val type = typeOptions[spinnerType.selectedItemPosition]
                val remarks = editRemarks.text.toString().trim()
                if (name.isNotEmpty()) {
                    databaseHelper.addLabour(name, group.id, wage, selectedJoinDate, type, remarks.ifEmpty { null })
                    loadLabourList()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showEditDialog(labourId: Int, currentName: String) {
        val labour = databaseHelper.getAllLabours().find { it.id == labourId } ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_labour, null)
        val title = dialogView.findViewById<TextView>(R.id.dialog_title)
        val message = dialogView.findViewById<TextView>(R.id.dialog_message)
        val editName = dialogView.findViewById<EditText>(R.id.edit_labour_name)
        val editWage = dialogView.findViewById<EditText>(R.id.edit_labour_wage)
        val layoutRemarks = dialogView.findViewById<View>(R.id.layout_remarks)
        val spinnerGroup = dialogView.findViewById<Spinner>(R.id.spinner_group)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_labour_type)
        val btnJoinDate = dialogView.findViewById<Button>(R.id.btn_join_date)
        val layoutEndDate = dialogView.findViewById<View>(R.id.layout_end_date)
        val btnEndDate = dialogView.findViewById<Button>(R.id.btn_end_date)
        val tvStatusBadge = dialogView.findViewById<TextView>(R.id.tv_status_badge)
        
        title.text = getString(R.string.title_update_record)
        message.text = currentName // Show labour name as subtitle
        
        layoutRemarks.visibility = View.GONE
        layoutEndDate.visibility = View.VISIBLE
        
        editName.setText(currentName)
        editWage.setText(labour.wage.toInt().toString())
        
        var selectedJoinDate = labour.joinDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var selectedEndDate = labour.endDate
        
        btnJoinDate.text = selectedJoinDate
        btnEndDate.text = selectedEndDate ?: "-"
        
        fun updateAutomaticStatus() {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val isActive = selectedEndDate == null || selectedEndDate!! >= today
            tvStatusBadge.text = if (isActive) getString(R.string.status_active).uppercase() else getString(R.string.status_inactive).uppercase()
            tvStatusBadge.setBackgroundResource(if (isActive) R.drawable.bg_badge_active else R.drawable.bg_badge_inactive)
            tvStatusBadge.visibility = View.VISIBLE
        }
        
        updateAutomaticStatus()
        
        btnJoinDate.setOnClickListener {
            val cal = Calendar.getInstance()
            try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedJoinDate)?.let { cal.time = it } } catch(_: Exception) {}
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedJoinDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                btnJoinDate.text = selectedJoinDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        
        btnEndDate.setOnClickListener {
            val cal = Calendar.getInstance()
            selectedEndDate?.let { try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.let { d -> cal.time = d } } catch(_: Exception) {} }
            
            val picker = DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedEndDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                btnEndDate.text = selectedEndDate
                updateAutomaticStatus()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            
            picker.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, getString(R.string.btn_clear_all)) { _, _ ->
                selectedEndDate = null
                btnEndDate.text = "-"
                updateAutomaticStatus()
            }
            picker.show()
        }

        val groups = databaseHelper.getAllGroups()
        spinnerGroup.adapter = ArrayAdapter(this, R.layout.spinner_item, groups.map { if (it.id == 1) getString(R.string.group_general) else it.name })
        spinnerGroup.setSelection(groups.indexOfFirst { it.id == labour.groupId }.coerceAtLeast(0))
        
        val typeOptions = listOf("DAILY_WAGE", "CONTRACT", "PRODUCTION_BASED", "MONTHLY")
        val typeDisplayNames = listOf(
            getString(R.string.labour_type_daily),
            getString(R.string.labour_type_contract),
            getString(R.string.labour_type_production),
            getString(R.string.labour_type_monthly)
        )
        spinnerType.adapter = ArrayAdapter(this, R.layout.spinner_item, typeDisplayNames)
        spinnerType.setSelection(typeOptions.indexOf(labour.labourType).coerceAtLeast(0))

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                val name = editName.text.toString().trim()
                val wage = editWage.text.toString().toDoubleOrNull() ?: 0.0
                val group = groups[spinnerGroup.selectedItemPosition]
                val type = typeOptions[spinnerType.selectedItemPosition]
                
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val status = if (selectedEndDate == null || selectedEndDate!! >= today) "active" else "inactive"

                if (name.isNotEmpty()) {
                    databaseHelper.updateLabour(labourId, name, group.id, wage, selectedJoinDate, selectedEndDate, status, type, null)
                    loadLabourList()
                }
            }
            .setNeutralButton(R.string.btn_delete) { _, _ ->
                MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
                    .setTitle(R.string.title_confirm)
                    .setMessage(getString(R.string.msg_confirm_delete, currentName))
                    .setPositiveButton(R.string.btn_remove) { _, _ ->
                        databaseHelper.deleteLabour(labourId)
                        loadLabourList()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showGroupManagementDialog() {
        val groups = databaseHelper.getAllGroups()
        val names = groups.map { if (it.id == 1) getString(R.string.group_general) else it.name }.toTypedArray()
        
        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.btn_manage_groups)
            .setItems(names) { _, which ->
                val group = groups[which]
                if (group.id != 1) {
                    MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
                        .setTitle(R.string.title_delete_group)
                        .setMessage(getString(R.string.msg_delete_group, group.name))
                        .setPositiveButton(R.string.btn_delete) { _, _ ->
                            databaseHelper.deleteGroup(group.id)
                            showGroupManagementDialog()
                        }
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
                }
            }
            .setPositiveButton(R.string.btn_add_group) { _, _ -> showAddGroupDialog() }
            .setNegativeButton(R.string.btn_back, null)
            .show()
    }

    private fun showAddGroupDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_group_name) }
        val container = FrameLayout(this).apply { 
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.title_new_group)
            .setView(container)
            .setPositiveButton(R.string.btn_create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    databaseHelper.addGroup(name)
                    showGroupManagementDialog()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showLabourProfileDialog(labourId: Int) {
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
                
                val labelView = TextView(this@MainActivity).apply {
                    text = label
                    textSize = 14f
                    setTextColor(Color.parseColor("#475467"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                
                val valueView = TextView(this@MainActivity).apply {
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

        container.addView(createStatRow(getString(R.string.label_join_date), stats.labour.joinDate ?: "-"))
        if(stats.labour.endDate != null) {
            container.addView(createStatRow(getString(R.string.label_end_date), stats.labour.endDate!!))
        }

        if (!stats.labour.remarks.isNullOrEmpty()) {
            container.addView(createStatRow(getString(R.string.hint_remarks), stats.labour.remarks!!))
        }

        container.addView(createStatRow(getString(R.string.status_present), stats.presentCount.toString()))
        container.addView(createStatRow(getString(R.string.status_half), stats.halfDayCount.toString()))
        container.addView(createStatRow(getString(R.string.status_absent), stats.absentCount.toString()))

        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
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

        val builder = MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(stats.labour.name)
            .setView(container)
            .setPositiveButton(R.string.btn_close, null)

        if (role != "viewer") {
            builder.setNeutralButton(R.string.btn_update) { _, _ ->
                showEditDialog(stats.labour.id, stats.labour.name)
            }
            builder.setNegativeButton(R.string.btn_advance) { _, _ ->
                val intent = Intent(this, AdvanceActivity::class.java)
                intent.putExtra("LABOUR_ID", labourId)
                startActivity(intent)
            }
        }
        
        builder.show()
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

package com.example.labourattendance

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.labourattendance.databinding.ActivityDashboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var syncManager: SyncManager

    private val backupLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) backupDatabase(uri)
    }

    private val restoreLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) restoreDatabase(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val initialPaddingLeft = binding.root.paddingLeft
        val initialPaddingTop = binding.root.paddingTop
        val initialPaddingRight = binding.root.paddingRight
        val initialPaddingBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
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
        databaseHelper.syncExistingToTransactions()

        syncManager = SyncManager.getInstance(this)
        observeSyncStatus()
        
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        if (role != "viewer") {
            syncManager.startAutoSync()
        }

        updateFarmUI()
        updateSeasonUI()

        binding.cardFarmSelector.setOnClickListener {
            startActivity(Intent(this, FarmsActivity::class.java))
        }

        binding.cardSeasonSelector.setOnClickListener {
            startActivity(Intent(this, SeasonActivity::class.java))
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""

        binding.cardWorkforce.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        binding.cardDispatch.setOnClickListener {
            startActivity(Intent(this, DispatchActivity::class.java))
        }

        binding.cardExpenditure.setOnClickListener {
            startActivity(Intent(this, ExpenditureActivity::class.java))
        }

        binding.cardFunds.setOnClickListener {
            startActivity(Intent(this, FundsActivity::class.java))
        }

        binding.cardAccounts.setOnClickListener {
            startActivity(Intent(this, AccountsActivity::class.java))
        }

        binding.cardSales.setOnClickListener {
            startActivity(Intent(this, SalesActivity::class.java))
        }

        setupRoleBasedUI()

        if (intent.getBooleanExtra("AUTO_RESTORE", false)) {
            restoreFromCloud()
        }
    }

    override fun onResume() {
        super.onResume()
        updateFarmUI()
        updateSeasonUI()
        updateOperationalSummary()
    }

    private fun observeSyncStatus() {
        lifecycleScope.launch {
            syncManager.syncStatus.collect { status ->
                updateSyncUI(status)
            }
        }
    }

    private fun updateSyncUI(status: SyncManager.SyncStatus) {
        val indicator = binding.viewSyncIndicator
        val text = binding.tvSyncStatus
        
        when (status) {
            is SyncManager.SyncStatus.Idle -> {
                indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#98A2B3"))
                text.text = "Idle"
            }
            is SyncManager.SyncStatus.Syncing -> {
                indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#004EEB"))
                text.text = "Syncing..."
            }
            is SyncManager.SyncStatus.Synced -> {
                indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#12B76A"))
                text.text = "Synced"
            }
            is SyncManager.SyncStatus.Offline -> {
                indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F04438"))
                text.text = "Offline"
            }
            is SyncManager.SyncStatus.Pending -> {
                indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FDB022"))
                text.text = "Pending: ${status.count}"
            }
            is SyncManager.SyncStatus.Failed -> {
                indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F04438"))
                text.text = "Sync Failed"
            }
        }
    }

    private fun updateFarmUI() {
        val farmId = databaseHelper.getCurrentFarmId()
        val farm = databaseHelper.getAllFarms().find { it.id == farmId }
        binding.textViewCurrentFarm.text = farm?.name ?: "Main Farm"
    }

    private fun updateSeasonUI() {
        val seasonId = databaseHelper.getCurrentSeasonId()
        val seasons = databaseHelper.getAllSeasons()
        val currentSeason = seasons.find { it.id == seasonId }
        binding.textViewCurrentSeason.text = currentSeason?.name ?: "No Season"
    }

    private fun updateOperationalSummary() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        
        // 1. Workforce Today
        val attendance = databaseHelper.getAttendanceEntries(today, today)
        val presentCount = attendance.count { (it.status == "P" || it.status == "H") }
        binding.tvSummaryWorkforce.text = getString(R.string.summary_workforce_val, presentCount)

        // 2. Dispatch Today
        val dispatches = databaseHelper.getAllDispatches().filter { it.date == today }
        val totalCartons = dispatches.sumOf { d -> d.items.sumOf { it.cartonCount } }
        binding.tvSummaryDispatch.text = getString(R.string.summary_dispatch_val, totalCartons)

        // 3. Sales Today
        val sales = databaseHelper.getAllSales().filter { it.date == today }
        val totalSalesAmount = sales.sumOf { it.totalAmount }
        binding.tvSummarySales.text = getString(R.string.summary_sales_val, String.format(Locale.US, "%,.0f", totalSalesAmount))

        // 4. Available Cash (Cash + Bank ONLY)
        val accounts = databaseHelper.getAllAccountsWithBalance()
        val totalCashBalance = accounts.filter { !databaseHelper.isAccountPayable(it.id) }.sumOf { it.balance }
        binding.tvSummaryBalance.text = getString(R.string.summary_balance_val, String.format(Locale.US, "%,.0f", totalCashBalance))

        // 5. Total Owed (Liability to Partners)
        val totalLiability = databaseHelper.getTotalPartnerBalance()
        val owedValue = if (totalLiability < 0) -totalLiability else 0.0
        binding.tvSummaryOwed.text = getString(R.string.summary_balance_val, String.format(Locale.US, "%,.0f", owedValue))

        // 6. Total Expenses Done (All time)
        val totalExpenses = databaseHelper.getTotalExpensesDone()
        binding.tvSummaryTotalExpenses.text = getString(R.string.summary_balance_val, String.format(Locale.US, "%,.0f", totalExpenses))
    }

    private fun setupRoleBasedUI() {
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        binding.cardWorkforce.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        
        val role = getSharedPreferences("Settings", MODE_PRIVATE).getString("User_Role", "viewer")
        if (role == "viewer") {
            menu.findItem(R.id.action_sync)?.isVisible = false
            menu.findItem(R.id.action_backup)?.isVisible = false
            menu.findItem(R.id.action_restore)?.isVisible = false
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_language -> {
                showLanguageDialog()
                true
            }
            R.id.action_notifications -> {
                showNotificationsDialog()
                true
            }
            R.id.action_sync -> {
                syncManager.triggerSync()
                true
            }
            R.id.action_restore_cloud -> {
                restoreFromCloud()
                true
            }
            R.id.action_backup -> {
                backupLauncher.launch("labour_backup_${System.currentTimeMillis()}.db")
                true
            }
            R.id.action_restore -> {
                restoreLauncher.launch(arrayOf("*/*"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun restoreFromCloud() {
        val firestore = FirestoreHelper()
        Toast.makeText(this, R.string.msg_restoring, Toast.LENGTH_LONG).show()

        firestore.fetchAllFarms { farms ->
            databaseHelper.clearAndRestoreFarms(farms)
            
            firestore.fetchAllSeasons { seasons ->
                databaseHelper.clearAndRestoreSeasons(seasons)
                
                firestore.fetchAllGroups { groups ->
                    databaseHelper.clearAndRestoreGroups(groups)
                    
                    firestore.fetchAllLabours { labours ->
                        databaseHelper.clearAndRestoreLabours(labours)
                        
                        firestore.fetchAllAttendance { attendance ->
                            databaseHelper.clearAndRestoreAttendance(attendance)
                            
                            firestore.fetchAllAdvances { advances ->
                                databaseHelper.clearAndRestoreAdvances(advances)
                                
                                firestore.fetchAllExpenditure { vouchers ->
                                    databaseHelper.clearAndRestoreExpenditure(vouchers)
                                    
                                    firestore.fetchAllFundSources { sources ->
                                        databaseHelper.clearAndRestoreFundSources(sources)
                                        
                                        firestore.fetchAllFundEntries { entries ->
                                            databaseHelper.clearAndRestoreFundEntries(entries)
                                            
                                            firestore.fetchAllVehicles { vehicles ->
                                                databaseHelper.clearAndRestoreVehicles(vehicles)
                                                
                                                firestore.fetchAllDateTypes { types ->
                                                    databaseHelper.clearAndRestoreDateTypes(types)
                                                    
                                                    firestore.fetchAllDispatches { dispatches ->
                                                        databaseHelper.clearAndRestoreDispatches(dispatches)
                                                        
                                                        // Force a database cleanup/re-sync for logic integrity
                                                        databaseHelper.onOpen(databaseHelper.writableDatabase)
                                                        databaseHelper.syncExistingToTransactions()
                                                        
                                                        Toast.makeText(this, R.string.msg_restore_success, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun backupDatabase(uri: Uri) {
        try {
            val dbFile: File = getDatabasePath("labour_attendance.db")
            contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, "Backup Successful", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreDatabase(uri: Uri) {
        try {
            val dbFile: File = getDatabasePath("labour_attendance.db")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, "Restore Successful. Restarting app...", Toast.LENGTH_SHORT).show()
            binding.root.postDelayed({
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                Runtime.getRuntime().exit(0)
            }, 1500)
        } catch (e: Exception) {
            Toast.makeText(this, "Restore Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "العربية (Arabic)", "اردو (Urdu)")
        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.action_language)
            .setItems(languages) { _, which ->
                when (which) {
                    0 -> setLocale("en", true)
                    1 -> setLocale("ar", true)
                    2 -> setLocale("ur", true)
                }
            }
            .show()
    }

    private fun setLocale(lang: String, refresh: Boolean) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        
        if (lang == "ar" || lang == "ur") {
            config.setLayoutDirection(locale)
        } else {
            config.setLayoutDirection(Locale.ENGLISH)
        }

        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        
        getSharedPreferences("Settings", MODE_PRIVATE).edit().putString("My_Lang", lang).apply()

        if (refresh) {
            // Force fully immediate restart of the application to the dashboard
            val intent = Intent(this, DashboardActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun loadLocale() {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        val lang = prefs.getString("My_Lang", "en") ?: "en"
        setLocale(lang, false)
    }

    private fun showNotificationsDialog() {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("Reminder_Enabled", false)
        val hour = prefs.getInt("Reminder_Hour", 19)
        val minute = prefs.getInt("Reminder_Minute", 0)

        val dialogView = layoutInflater.inflate(R.layout.dialog_notifications, null)
        val switchEnabled = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchReminder)
        val buttonTime = dialogView.findViewById<android.widget.Button>(R.id.buttonPickTime)

        switchEnabled.isChecked = isEnabled
        buttonTime.text = String.format(Locale.US, "%02d:%02d", hour, minute)

        var selectedHour = hour
        var selectedMinute = minute

        buttonTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selectedHour = h
                selectedMinute = m
                buttonTime.text = String.format(Locale.US, "%02d:%02d", h, m)
            }, selectedHour, selectedMinute, false).show()
        }

        MaterialAlertDialogBuilder(this, R.style.AppAlertDialogTheme)
            .setTitle(R.string.title_notifications)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                prefs.edit().apply {
                    putBoolean("Reminder_Enabled", switchEnabled.isChecked)
                    putInt("Reminder_Hour", selectedHour)
                    putInt("Reminder_Minute", selectedMinute)
                    apply()
                }
                if (switchEnabled.isChecked) {
                    scheduleReminder(selectedHour, selectedMinute)
                } else {
                    cancelReminder()
                }
                Toast.makeText(this, R.string.toast_reminder_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun scheduleReminder(hour: Int, minute: Int) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    private fun cancelReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }
}

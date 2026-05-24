package com.example.labourattendance

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val firestoreHelper = FirestoreHelper()

    data class Labour(
        val id: Int,
        val name: String,
        val groupId: Int,
        val wage: Double,
        val displayOrder: Int,
        val joinDate: String? = null,
        val endDate: String? = null,
        val status: String = "active", // "active" or "inactive"
        val farmId: Int = 0,
        val labourType: String = "DAILY_WAGE",
        val remarks: String? = null
    )

    data class Group(
        val id: Int,
        val name: String,
        val farmId: Int = 0
    )

    data class Farm(
        val id: Int,
        val name: String,
        val location: String? = null,
        val owner: String? = null,
        val remarks: String? = null,
        val activeStatus: Int = 1,
        val createdBy: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class AttendanceEntry(
        val labourId: Int,
        val date: String,
        val status: String,
        val farmId: Int = 0
    )

    data class MonthlySummary(
        val labourId: Int,
        val labourName: String,
        val presentCount: Int,
        val absentCount: Int,
        val halfDayCount: Int,
        val totalAdvance: Double
    )

    data class AdvanceRecord(
        val id: Int,
        val labourId: Int,
        val amount: Double,
        val date: String,
        val description: String?,
        val labourName: String? = null,
        val sourceId: Int = 0,
        val sourceName: String? = null,
        val farmId: Int = 0
    )

    data class LabourStats(
        val labour: Labour,
        val presentCount: Int,
        val halfDayCount: Int,
        val absentCount: Int,
        val totalAdvance: Double,
        val totalEarnings: Double,
        val netBalance: Double
    )

    data class Vehicle(
        val id: Int,
        val number: String,
        val driverName: String,
        val driverPhone: String,
        val farmId: Int = 0
    )

    data class DateType(
        val id: Int,
        val name: String,
        val farmId: Int = 0
    )

    data class ExpCategory(
        val id: Int,
        val name: String,
        val farmId: Int = 0
    )

    data class DispatchRecord(
        val id: Int,
        val vehicleId: Int,
        val vehicleNumber: String,
        val driverName: String,
        val date: String,
        val cloudId: String? = null,
        val items: List<DispatchItem> = emptyList(),
        val farmId: Int = 0
    )

    data class DispatchItem(
        val id: Int = 0,
        val dispatchId: Int = 0,
        val dateTypeId: Int,
        val dateTypeName: String = "",
        val cartonCount: Int
    )

    // Expenditure Data Classes
    data class Voucher(
        val id: Int,
        val voucherNumber: String, // Simple ID for paper reference (e.g. V-101)
        val date: String,
        val totalAmount: Double,
        val recordedBy: String,
        val sourceId: Int = 0,
        val sourceName: String? = null,
        val items: List<ExpenseItem> = emptyList(),
        val farmId: Int = 0
    )

    data class ExpenseItem(
        val id: Int = 0,
        val voucherId: Int = 0,
        val category: String,
        val amount: Double,
        val description: String? = null
    )

    // Funds Data Classes
    data class FundSource(
        val id: Int,
        val name: String,
        val description: String? = null,
        val farmId: Int = 0
    )

    data class FundEntry(
        val id: Int,
        val sourceId: Int,
        val sourceName: String? = null,
        val amount: Double,
        val date: String,
        val description: String? = null,
        val farmId: Int = 0
    )

    // --- NEW ACCOUNTS MODULE DATA CLASSES ---
    data class Account(
        val id: Int,
        val name: String,
        val remarks: String? = null,
        val balance: Double = 0.0,
        val farmId: Int = 0
    )

    data class AccountTransaction(
        val id: Int,
        val accountId: Int,
        val moduleSource: String, // "Funds", "Expense", "Sales", "Advance"
        val referenceId: Int,
        val transactionType: String, // "Credit" (In) or "Debit" (Out)
        val amount: Double,
        val remarks: String?,
        val date: String,
        val accountName: String? = null,
        val farmId: Int = 0
    )

    data class PLSummary(
        val totalSales: Double,
        val totalExpenses: Double,
        val totalFunds: Double,
        val netProfit: Double
    )

    // Sales Data Classes
    data class Sale(
        val id: Int,
        val date: String,
        val buyerName: String,
        val totalAmount: Double,
        val sourceId: Int = 0, // Account where money is received
        val sourceName: String? = null,
        val items: List<SaleItem> = emptyList(),
        val farmId: Int = 0
    )

    data class SaleItem(
        val id: Int = 0,
        val saleId: Int = 0,
        val dispatchId: Int, // Link to vehicle/dispatch
        val vehicleNumber: String = "", // Helper for UI
        val dateTypeId: Int,
        val dateTypeName: String = "",
        val quantity: Int,
        val unitPrice: Double
    )

    data class StockStatus(
        val dispatchId: Int,
        val vehicleNumber: String,
        val date: String,
        val dateTypeId: Int,
        val dateTypeName: String,
        val dispatchedCount: Int,
        val soldCount: Int,
        val remainingCount: Int
    )

    data class Season(
        val id: Int,
        val name: String,
        val year: Int,
        val isActive: Boolean,
        val isClosed: Boolean,
        val startDate: String,
        val endDate: String? = null
    )

    override fun onCreate(db: SQLiteDatabase) {
        // Seasons Table
        db.execSQL("CREATE TABLE $TABLE_SEASON ($COLUMN_SEASON_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SEASON_NAME TEXT NOT NULL, $COLUMN_SEASON_YEAR INTEGER NOT NULL, $COLUMN_SEASON_IS_ACTIVE INTEGER DEFAULT 1, $COLUMN_SEASON_IS_CLOSED INTEGER DEFAULT 0, $COLUMN_SEASON_START_DATE TEXT NOT NULL, $COLUMN_SEASON_END_DATE TEXT)")
        
        // Initial Default Season (2026)
        val initialSeasonValues = ContentValues().apply {
            put(COLUMN_SEASON_NAME, "2026 Season")
            put(COLUMN_SEASON_YEAR, 2026)
            put(COLUMN_SEASON_IS_ACTIVE, 1)
            put(COLUMN_SEASON_START_DATE, "2026-01-01")
        }
        val defaultSeasonId = db.insert(TABLE_SEASON, null, initialSeasonValues)

        // Farms Table
        db.execSQL("CREATE TABLE $TABLE_FARM ($COLUMN_FARM_ID_PK INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_FARM_NAME TEXT NOT NULL, $COLUMN_FARM_LOCATION TEXT, $COLUMN_FARM_OWNER TEXT, $COLUMN_FARM_REMARKS TEXT, $COLUMN_FARM_STATUS INTEGER DEFAULT 1, $COLUMN_FARM_CREATED_BY TEXT, $COLUMN_FARM_TIMESTAMP INTEGER)")
        
        // Initial Default Farm
        val farmValues = ContentValues().apply {
            put(COLUMN_FARM_NAME, "Main Farm")
            put(COLUMN_FARM_TIMESTAMP, System.currentTimeMillis())
        }
        val defaultFarmId = db.insert(TABLE_FARM, null, farmValues)

        db.execSQL("CREATE TABLE $TABLE_GROUP ($COLUMN_GROUP_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_GROUP_NAME TEXT NOT NULL, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId)")
        db.execSQL("CREATE TABLE $TABLE_LABOUR ($COLUMN_LABOUR_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_LABOUR_NAME TEXT NOT NULL, $COLUMN_LABOUR_GROUP_ID INTEGER DEFAULT 1, $COLUMN_LABOUR_WAGE REAL DEFAULT 0.0, $COLUMN_DISPLAY_ORDER INTEGER NOT NULL, $COLUMN_LABOUR_JOIN_DATE TEXT, $COLUMN_LABOUR_END_DATE TEXT, $COLUMN_LABOUR_STATUS TEXT DEFAULT 'active', $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId, $COLUMN_LABOUR_TYPE TEXT DEFAULT 'DAILY_WAGE', $COLUMN_LABOUR_REMARKS TEXT, FOREIGN KEY($COLUMN_LABOUR_GROUP_ID) REFERENCES $TABLE_GROUP($COLUMN_GROUP_ID))")
        db.execSQL("CREATE TABLE $TABLE_ATTENDANCE ($COLUMN_ATTENDANCE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_ATTENDANCE_LABOUR_ID INTEGER NOT NULL, $COLUMN_ATTENDANCE_DATE TEXT NOT NULL, $COLUMN_ATTENDANCE_STATUS TEXT NOT NULL, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId, $COLUMN_SEASON_LINK_ID INTEGER DEFAULT $defaultSeasonId, UNIQUE($COLUMN_ATTENDANCE_LABOUR_ID, $COLUMN_ATTENDANCE_DATE), FOREIGN KEY($COLUMN_SEASON_LINK_ID) REFERENCES $TABLE_SEASON($COLUMN_SEASON_ID))")
        db.execSQL("CREATE TABLE $TABLE_ADVANCE ($COLUMN_ADVANCE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_ADVANCE_LABOUR_ID INTEGER NOT NULL, $COLUMN_ADVANCE_AMOUNT REAL NOT NULL, $COLUMN_ADVANCE_DATE TEXT NOT NULL, $COLUMN_ADVANCE_DESCRIPTION TEXT, $COLUMN_ADVANCE_SOURCE_ID INTEGER DEFAULT 0, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId, $COLUMN_SEASON_LINK_ID INTEGER DEFAULT $defaultSeasonId, FOREIGN KEY($COLUMN_ADVANCE_LABOUR_ID) REFERENCES $TABLE_LABOUR($COLUMN_LABOUR_ID), FOREIGN KEY($COLUMN_SEASON_LINK_ID) REFERENCES $TABLE_SEASON($COLUMN_SEASON_ID))")
        db.execSQL("CREATE TABLE $TABLE_VEHICLE ($COLUMN_VEHICLE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_VEHICLE_NUMBER TEXT NOT NULL, $COLUMN_VEHICLE_DRIVER_NAME TEXT NOT NULL, $COLUMN_VEHICLE_DRIVER_PHONE TEXT NOT NULL, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId)")
        db.execSQL("CREATE TABLE $TABLE_DATE_TYPE ($COLUMN_DATE_TYPE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_DATE_TYPE_NAME TEXT NOT NULL, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId)")
        db.execSQL("CREATE TABLE $TABLE_DISPATCH ($COLUMN_DISPATCH_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_DISPATCH_VEHICLE_ID INTEGER NOT NULL, $COLUMN_DISPATCH_DATE TEXT NOT NULL, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId, $COLUMN_SEASON_LINK_ID INTEGER DEFAULT $defaultSeasonId, FOREIGN KEY($COLUMN_SEASON_LINK_ID) REFERENCES $TABLE_SEASON($COLUMN_SEASON_ID))")
        db.execSQL("CREATE TABLE $TABLE_DISPATCH_ITEM ($COLUMN_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_ITEM_DISPATCH_ID INTEGER NOT NULL, $COLUMN_ITEM_DATE_TYPE_ID INTEGER NOT NULL, $COLUMN_ITEM_COUNT INTEGER NOT NULL, FOREIGN KEY($COLUMN_ITEM_DISPATCH_ID) REFERENCES $TABLE_DISPATCH($COLUMN_DISPATCH_ID), FOREIGN KEY($COLUMN_ITEM_DATE_TYPE_ID) REFERENCES $TABLE_DATE_TYPE($COLUMN_DATE_TYPE_ID))")
        
        // Expenditure Tables
        db.execSQL("CREATE TABLE $TABLE_EXP_VOUCHER ($COLUMN_VOUCHER_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_VOUCHER_NUMBER TEXT NOT NULL, $COLUMN_VOUCHER_DATE TEXT NOT NULL, $COLUMN_VOUCHER_TOTAL REAL NOT NULL, $COLUMN_VOUCHER_BY TEXT, $COLUMN_VOUCHER_SOURCE_ID INTEGER DEFAULT 0, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId, $COLUMN_SEASON_LINK_ID INTEGER DEFAULT $defaultSeasonId, FOREIGN KEY($COLUMN_SEASON_LINK_ID) REFERENCES $TABLE_SEASON($COLUMN_SEASON_ID))")
        db.execSQL("CREATE TABLE $TABLE_EXP_ITEM ($COLUMN_EXP_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_EXP_VOUCHER_ID INTEGER NOT NULL, $COLUMN_EXP_CATEGORY TEXT NOT NULL, $COLUMN_EXP_AMOUNT REAL NOT NULL, $COLUMN_EXP_DESC TEXT, FOREIGN KEY($COLUMN_EXP_VOUCHER_ID) REFERENCES $TABLE_EXP_VOUCHER($COLUMN_VOUCHER_ID))")
        db.execSQL("CREATE TABLE $TABLE_EXP_CAT ($COLUMN_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_CAT_NAME TEXT NOT NULL, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId)")

        // Funds Tables
        db.execSQL("CREATE TABLE $TABLE_FUND_SOURCE ($COLUMN_SOURCE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SOURCE_NAME TEXT NOT NULL, $COLUMN_SOURCE_DESC TEXT, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId)")
        db.execSQL("CREATE TABLE $TABLE_FUND_ENTRY ($COLUMN_FUND_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_FUND_SOURCE_ID INTEGER NOT NULL, $COLUMN_FUND_AMOUNT REAL NOT NULL, $COLUMN_FUND_DATE TEXT NOT NULL, $COLUMN_FUND_DESC TEXT, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId, $COLUMN_SEASON_LINK_ID INTEGER DEFAULT $defaultSeasonId, FOREIGN KEY($COLUMN_FUND_SOURCE_ID) REFERENCES $TABLE_FUND_SOURCE($COLUMN_SOURCE_ID), FOREIGN KEY($COLUMN_SEASON_LINK_ID) REFERENCES $TABLE_SEASON($COLUMN_SEASON_ID))")

        // Sales Tables
        db.execSQL("CREATE TABLE $TABLE_SALE ($COLUMN_SALE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SALE_DATE TEXT NOT NULL, $COLUMN_SALE_BUYER TEXT NOT NULL, $COLUMN_SALE_TOTAL REAL NOT NULL, $COLUMN_SALE_SOURCE_ID INTEGER DEFAULT 0, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId, $COLUMN_SEASON_LINK_ID INTEGER DEFAULT $defaultSeasonId, FOREIGN KEY($COLUMN_SEASON_LINK_ID) REFERENCES $TABLE_SEASON($COLUMN_SEASON_ID))")
        db.execSQL("CREATE TABLE $TABLE_SALE_ITEM ($COLUMN_SALE_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SALE_ITEM_SALE_ID INTEGER NOT NULL, $COLUMN_SALE_ITEM_DISPATCH_ID INTEGER NOT NULL, $COLUMN_SALE_ITEM_DATE_TYPE_ID INTEGER NOT NULL, $COLUMN_SALE_ITEM_QTY INTEGER NOT NULL, $COLUMN_SALE_ITEM_PRICE REAL NOT NULL, FOREIGN KEY($COLUMN_SALE_ITEM_SALE_ID) REFERENCES $TABLE_SALE($COLUMN_SALE_ID))")

        // Account Transactions Table
        db.execSQL("CREATE TABLE $TABLE_ACCOUNT_TX ($COLUMN_TX_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_TX_ACCOUNT_ID INTEGER NOT NULL, $COLUMN_TX_SOURCE TEXT NOT NULL, $COLUMN_TX_REF_ID INTEGER NOT NULL, $COLUMN_TX_TYPE TEXT NOT NULL, $COLUMN_TX_AMOUNT REAL NOT NULL, $COLUMN_TX_REMARKS TEXT, $COLUMN_TX_DATE TEXT NOT NULL, $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId, $COLUMN_SEASON_LINK_ID INTEGER DEFAULT $defaultSeasonId, FOREIGN KEY($COLUMN_TX_ACCOUNT_ID) REFERENCES $TABLE_FUND_SOURCE($COLUMN_SOURCE_ID), FOREIGN KEY($COLUMN_SEASON_LINK_ID) REFERENCES $TABLE_SEASON($COLUMN_SEASON_ID))")

        db.insert(TABLE_GROUP, null, ContentValues().apply { put(COLUMN_GROUP_NAME, "General"); put(COLUMN_FARM_ID, defaultFarmId) })
        listOf("Sukkari", "Sugai", "Ajwa").forEach { name ->
            db.insert(TABLE_DATE_TYPE, null, ContentValues().apply { put(COLUMN_DATE_TYPE_NAME, name); put(COLUMN_FARM_ID, defaultFarmId) })
        }
        listOf("POL (Fuel/Oil)", "Pesticides & Fertilizers", "Repairs", "Salaries", "Groceries", "Vegetables", "Others").forEach { name ->
            db.insert(TABLE_EXP_CAT, null, ContentValues().apply { put(COLUMN_CAT_NAME, name); put(COLUMN_FARM_ID, defaultFarmId) })
        }
        
        // Initial Fund Sources
        listOf("Cash", "Younis Khan", "Partner A", "Partner B", "Loan").forEach { name ->
            db.insert(TABLE_FUND_SOURCE, null, ContentValues().apply { put(COLUMN_SOURCE_NAME, name); put(COLUMN_FARM_ID, defaultFarmId) })
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // Cleanup: Apply logic to existing data
        // Anyone with no end date or a future end date should be 'active' in the database column as well
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        db.execSQL("UPDATE $TABLE_LABOUR SET $COLUMN_LABOUR_STATUS = 'active' WHERE $COLUMN_LABOUR_END_DATE IS NULL OR $COLUMN_LABOUR_END_DATE = '' OR $COLUMN_LABOUR_END_DATE >= ?", arrayOf(today))
        db.execSQL("UPDATE $TABLE_LABOUR SET $COLUMN_LABOUR_STATUS = 'inactive' WHERE $COLUMN_LABOUR_END_DATE IS NOT NULL AND $COLUMN_LABOUR_END_DATE != '' AND $COLUMN_LABOUR_END_DATE < ?", arrayOf(today))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_GROUP ($COLUMN_GROUP_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_GROUP_NAME TEXT NOT NULL UNIQUE)")
            db.execSQL("INSERT OR IGNORE INTO $TABLE_GROUP ($COLUMN_GROUP_ID, $COLUMN_GROUP_NAME) VALUES (1, 'General')")
            try { db.execSQL("ALTER TABLE $TABLE_LABOUR ADD COLUMN $COLUMN_LABOUR_GROUP_ID INTEGER DEFAULT 1") } catch (_: Exception) {}
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_ADVANCE ($COLUMN_ADVANCE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_ADVANCE_LABOUR_ID INTEGER NOT NULL, $COLUMN_ADVANCE_AMOUNT REAL NOT NULL, $COLUMN_ADVANCE_DATE TEXT NOT NULL, $COLUMN_ADVANCE_DESCRIPTION TEXT, FOREIGN KEY($COLUMN_ADVANCE_LABOUR_ID) REFERENCES $TABLE_LABOUR($COLUMN_LABOUR_ID))")
        }
        if (oldVersion < 4) try { db.execSQL("ALTER TABLE $TABLE_LABOUR ADD COLUMN $COLUMN_LABOUR_WAGE REAL DEFAULT 0.0") } catch (_: Exception) {}
        if (oldVersion < 5) try { db.execSQL("UPDATE $TABLE_LABOUR SET $COLUMN_LABOUR_WAGE = 90.0") } catch (_: Exception) {}
        if (oldVersion < 6) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_VEHICLE ($COLUMN_VEHICLE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_VEHICLE_NUMBER TEXT NOT NULL, $COLUMN_VEHICLE_DRIVER_NAME TEXT NOT NULL, $COLUMN_VEHICLE_DRIVER_PHONE TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_DATE_TYPE ($COLUMN_DATE_TYPE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_DATE_TYPE_NAME TEXT NOT NULL UNIQUE)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_DISPATCH ($COLUMN_DISPATCH_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_DISPATCH_VEHICLE_ID INTEGER NOT NULL, $COLUMN_DISPATCH_DATE TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_DISPATCH_ITEM ($COLUMN_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_ITEM_DISPATCH_ID INTEGER NOT NULL, $COLUMN_ITEM_DATE_TYPE_ID INTEGER NOT NULL, $COLUMN_ITEM_COUNT INTEGER NOT NULL)")
            listOf("Sukkari", "Sugai", "Ajwa").forEach { name ->
                db.insert(TABLE_DATE_TYPE, null, ContentValues().apply { put(COLUMN_DATE_TYPE_NAME, name) })
            }
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_EXP_VOUCHER ($COLUMN_VOUCHER_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_VOUCHER_NUMBER TEXT NOT NULL, $COLUMN_VOUCHER_DATE TEXT NOT NULL, $COLUMN_VOUCHER_TOTAL REAL NOT NULL, $COLUMN_VOUCHER_BY TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_EXP_ITEM ($COLUMN_EXP_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_EXP_VOUCHER_ID INTEGER NOT NULL, $COLUMN_EXP_CATEGORY TEXT NOT NULL, $COLUMN_EXP_AMOUNT REAL NOT NULL, $COLUMN_EXP_DESC TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_EXP_CAT ($COLUMN_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_CAT_NAME TEXT NOT NULL UNIQUE)")
            listOf("Fuel", "Seeds", "Fertilizer", "Repairs", "Salaries", "Others").forEach { name ->
                db.insert(TABLE_EXP_CAT, null, ContentValues().apply { put(COLUMN_CAT_NAME, name) })
            }
        }
        if (oldVersion < 12) {
            // Comprehensive v12 upgrade for Funds module and Source linking
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_FUND_SOURCE ($COLUMN_SOURCE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SOURCE_NAME TEXT NOT NULL UNIQUE, $COLUMN_SOURCE_DESC TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_FUND_ENTRY ($COLUMN_FUND_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_FUND_SOURCE_ID INTEGER NOT NULL, $COLUMN_FUND_AMOUNT REAL NOT NULL, $COLUMN_FUND_DATE TEXT NOT NULL, $COLUMN_FUND_DESC TEXT)")
            
            try { db.execSQL("ALTER TABLE $TABLE_EXP_VOUCHER ADD COLUMN $COLUMN_VOUCHER_SOURCE_ID INTEGER DEFAULT 0") } catch (_: Exception) {}

            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_FUND_SOURCE", null)
            var count = 0
            if (cursor.moveToFirst()) count = cursor.getInt(0)
            cursor.close()
            if (count == 0) {
                listOf("Cash", "Partner A", "Partner B", "Loan").forEach { name ->
                    db.insert(TABLE_FUND_SOURCE, null, ContentValues().apply { put(COLUMN_SOURCE_NAME, name) })
                }
            }
        }
        if (oldVersion < 13) {
            try { db.execSQL("ALTER TABLE $TABLE_LABOUR ADD COLUMN $COLUMN_LABOUR_JOIN_DATE TEXT") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_LABOUR ADD COLUMN $COLUMN_LABOUR_END_DATE TEXT") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_LABOUR ADD COLUMN $COLUMN_LABOUR_STATUS TEXT DEFAULT 'active'") } catch (_: Exception) {}
        }
        if (oldVersion < 14) {
            try { db.execSQL("ALTER TABLE $TABLE_ADVANCE ADD COLUMN $COLUMN_ADVANCE_SOURCE_ID INTEGER DEFAULT 0") } catch (_: Exception) {}
        }
        if (oldVersion < 15) {
            // Ensure "Younis Khan" account exists and update advances
            db.execSQL("INSERT OR IGNORE INTO $TABLE_FUND_SOURCE ($COLUMN_SOURCE_NAME) VALUES ('Younis Khan')")
            db.execSQL("UPDATE $TABLE_ADVANCE SET $COLUMN_ADVANCE_SOURCE_ID = (SELECT $COLUMN_SOURCE_ID FROM $TABLE_FUND_SOURCE WHERE $COLUMN_SOURCE_NAME = 'Younis Khan') WHERE $COLUMN_ADVANCE_SOURCE_ID = 0")
        }
        if (oldVersion < 16) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_SALE ($COLUMN_SALE_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SALE_DATE TEXT NOT NULL, $COLUMN_SALE_BUYER TEXT NOT NULL, $COLUMN_SALE_TOTAL REAL NOT NULL, $COLUMN_SALE_SOURCE_ID INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_SALE_ITEM ($COLUMN_SALE_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SALE_ITEM_SALE_ID INTEGER NOT NULL, $COLUMN_SALE_ITEM_DISPATCH_ID INTEGER NOT NULL, $COLUMN_SALE_ITEM_DATE_TYPE_ID INTEGER NOT NULL, $COLUMN_SALE_ITEM_QTY INTEGER NOT NULL, $COLUMN_SALE_ITEM_PRICE REAL NOT NULL)")
        }
        if (oldVersion < 17) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_ACCOUNT_TX ($COLUMN_TX_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_TX_ACCOUNT_ID INTEGER NOT NULL, $COLUMN_TX_SOURCE TEXT NOT NULL, $COLUMN_TX_REF_ID INTEGER NOT NULL, $COLUMN_TX_TYPE TEXT NOT NULL, $COLUMN_TX_AMOUNT REAL NOT NULL, $COLUMN_TX_REMARKS TEXT, $COLUMN_TX_DATE TEXT NOT NULL)")
        }
        if (oldVersion < 18) {
            // Introducting Multi-Farm Support
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_FARM ($COLUMN_FARM_ID_PK INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_FARM_NAME TEXT NOT NULL, $COLUMN_FARM_LOCATION TEXT, $COLUMN_FARM_OWNER TEXT, $COLUMN_FARM_REMARKS TEXT, $COLUMN_FARM_STATUS INTEGER DEFAULT 1, $COLUMN_FARM_CREATED_BY TEXT, $COLUMN_FARM_TIMESTAMP INTEGER)")
                
                // Initial Default Farm
                val farmValues = ContentValues().apply {
                    put(COLUMN_FARM_NAME, "Main Farm")
                    put(COLUMN_FARM_TIMESTAMP, System.currentTimeMillis())
                }
                val defaultFarmId = db.insert(TABLE_FARM, null, farmValues)

                // Add farm_id to all existing tables
                val tables = listOf(
                    TABLE_GROUP, TABLE_LABOUR, TABLE_ATTENDANCE, TABLE_ADVANCE,
                    TABLE_VEHICLE, TABLE_DATE_TYPE, TABLE_DISPATCH, TABLE_EXP_VOUCHER,
                    TABLE_EXP_CAT, TABLE_FUND_SOURCE, TABLE_FUND_ENTRY, TABLE_SALE, TABLE_ACCOUNT_TX
                )
                
                for (table in tables) {
                    try {
                        db.execSQL("ALTER TABLE $table ADD COLUMN $COLUMN_FARM_ID INTEGER DEFAULT $defaultFarmId")
                    } catch (e: Exception) {
                        // Column might already exist
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 19) {
            try { db.execSQL("ALTER TABLE $TABLE_LABOUR ADD COLUMN $COLUMN_LABOUR_TYPE TEXT DEFAULT 'DAILY_WAGE'") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_LABOUR ADD COLUMN $COLUMN_LABOUR_REMARKS TEXT") } catch (_: Exception) {}
        }
        if (oldVersion < 20) {
            try {
                // 1. Create Seasons Table
                db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_SEASON ($COLUMN_SEASON_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_SEASON_NAME TEXT NOT NULL, $COLUMN_SEASON_YEAR INTEGER NOT NULL, $COLUMN_SEASON_IS_ACTIVE INTEGER DEFAULT 1, $COLUMN_SEASON_IS_CLOSED INTEGER DEFAULT 0, $COLUMN_SEASON_START_DATE TEXT NOT NULL, $COLUMN_SEASON_END_DATE TEXT)")
                
                // 2. Insert Default Season
                val initialSeasonValues = ContentValues().apply {
                    put(COLUMN_SEASON_NAME, "2026 Season")
                    put(COLUMN_SEASON_YEAR, 2026)
                    put(COLUMN_SEASON_IS_ACTIVE, 1)
                    put(COLUMN_SEASON_START_DATE, "2026-01-01")
                }
                val defaultSeasonId = db.insert(TABLE_SEASON, null, initialSeasonValues)

                // 3. Add season_id column to operational tables
                val transactionalTables = listOf(
                    TABLE_ATTENDANCE, TABLE_ADVANCE, TABLE_DISPATCH, 
                    TABLE_EXP_VOUCHER, TABLE_FUND_ENTRY, TABLE_SALE, TABLE_ACCOUNT_TX
                )
                
                for (table in transactionalTables) {
                    try {
                        db.execSQL("ALTER TABLE $table ADD COLUMN $COLUMN_SEASON_LINK_ID INTEGER DEFAULT $defaultSeasonId")
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getCurrentFarmId(): Int {
        val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        return prefs.getInt("current_farm_id", 1) // Default to 1 (Main Farm)
    }

    fun setCurrentFarmId(farmId: Int) {
        val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("current_farm_id", farmId).apply()
    }

    // --- SEASON MANAGEMENT ---

    fun getCurrentSeasonId(): Int {
        val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        var seasonId = prefs.getInt("current_season_id", -1)
        
        if (seasonId == -1) {
            // Default to the first active season found in DB
            val cursor = readableDatabase.query(TABLE_SEASON, arrayOf(COLUMN_SEASON_ID), "$COLUMN_SEASON_IS_ACTIVE = 1", null, null, null, "$COLUMN_SEASON_ID DESC", "1")
            if (cursor.moveToFirst()) {
                seasonId = cursor.getInt(0)
                setCurrentSeasonId(seasonId)
            }
            cursor.close()
        }
        return seasonId
    }

    fun setCurrentSeasonId(seasonId: Int) {
        val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("current_season_id", seasonId).apply()
    }

    fun getAllSeasons(): List<Season> {
        val list = mutableListOf<Season>()
        val cursor = readableDatabase.query(TABLE_SEASON, null, null, null, null, null, "$COLUMN_SEASON_YEAR DESC")
        while (cursor.moveToNext()) {
            list.add(Season(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SEASON_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SEASON_NAME)),
                year = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SEASON_YEAR)),
                isActive = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SEASON_IS_ACTIVE)) == 1,
                isClosed = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SEASON_IS_CLOSED)) == 1,
                startDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SEASON_START_DATE)),
                endDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SEASON_END_DATE))
            ))
        }
        cursor.close()
        return list
    }

    fun closeSeason(seasonId: Int): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(COLUMN_SEASON_IS_ACTIVE, 0)
            put(COLUMN_SEASON_IS_CLOSED, 1)
            put(COLUMN_SEASON_END_DATE, today)
        }
        return writableDatabase.update(TABLE_SEASON, values, "$COLUMN_SEASON_ID = ?", arrayOf(seasonId.toString())) > 0
    }

    fun startNewSeason(name: String, year: Int, startDate: String): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1. Deactivate all other seasons
            db.execSQL("UPDATE $TABLE_SEASON SET $COLUMN_SEASON_IS_ACTIVE = 0")

            // 2. Create new season
            val values = ContentValues().apply {
                put(COLUMN_SEASON_NAME, name)
                put(COLUMN_SEASON_YEAR, year)
                put(COLUMN_SEASON_IS_ACTIVE, 1)
                put(COLUMN_SEASON_IS_CLOSED, 0)
                put(COLUMN_SEASON_START_DATE, startDate)
            }
            val newSeasonId = db.insert(TABLE_SEASON, null, values)
            if (newSeasonId == -1L) return -1L

            // 3. Carry forward balances
            carryForwardBalances(newSeasonId.toInt())

            db.setTransactionSuccessful()
            setCurrentSeasonId(newSeasonId.toInt())
            return newSeasonId
        } finally {
            db.endTransaction()
        }
    }

    private fun carryForwardBalances(newSeasonId: Int) {
        val farms = getAllFarms()
        val accounts = getAllFundSources() // This returns all sources for the current farm, but I should iterate all farms
        
        for (farm in farms) {
            // Get all accounts for this farm
            val farmAccounts = readableDatabase.query(TABLE_FUND_SOURCE, null, "$COLUMN_FARM_ID = ?", arrayOf(farm.id.toString()), null, null, null)
            while (farmAccounts.moveToNext()) {
                val accId = farmAccounts.getInt(farmAccounts.getColumnIndexOrThrow(COLUMN_SOURCE_ID))
                val accName = farmAccounts.getString(farmAccounts.getColumnIndexOrThrow(COLUMN_SOURCE_NAME))
                
                // Calculate balance from ALL previous seasons for this account
                val balance = getAccountBalance(accId) // This method needs to be aware of seasons now... wait.
                // Re-thinking: getAccountBalance should probably sum EVERYTHING up to now across all seasons?
                // Or only for the current season?
                // The user said: "balances = continuous (carry forward)".
                // So opening balance entry in the NEW season should be the sum of all transactions in ALL PREVIOUS seasons.
                
                if (balance != 0.0) {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val type = if (balance > 0) "Credit" else "Debit"
                    val remarks = "Opening Balance - Carry forward"
                    
                    // Create an opening transaction in the NEW season
                    val values = ContentValues().apply {
                        put(COLUMN_TX_ACCOUNT_ID, accId)
                        put(COLUMN_TX_SOURCE, "Opening")
                        put(COLUMN_TX_REF_ID, 0)
                        put(COLUMN_TX_TYPE, type)
                        put(COLUMN_TX_AMOUNT, Math.abs(balance))
                        put(COLUMN_TX_REMARKS, remarks)
                        put(COLUMN_TX_DATE, today)
                        put(COLUMN_FARM_ID, farm.id)
                        put(COLUMN_SEASON_LINK_ID, newSeasonId)
                    }
                    writableDatabase.insert(TABLE_ACCOUNT_TX, null, values)
                }
            }
            farmAccounts.close()
        }
    }

    fun isCurrentSeasonClosed(): Boolean {
        val id = getCurrentSeasonId()
        val cursor = readableDatabase.query(TABLE_SEASON, arrayOf(COLUMN_SEASON_IS_CLOSED), "$COLUMN_SEASON_ID = ?", arrayOf(id.toString()), null, null, null)
        var closed = false
        if (cursor.moveToFirst()) {
            closed = cursor.getInt(0) == 1
        }
        cursor.close()
        return closed
    }

    fun addFarm(name: String, location: String? = null, owner: String? = null, remarks: String? = null): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FARM_NAME, name.trim())
            put(COLUMN_FARM_LOCATION, location?.trim())
            put(COLUMN_FARM_OWNER, owner?.trim())
            put(COLUMN_FARM_REMARKS, remarks?.trim())
            put(COLUMN_FARM_TIMESTAMP, System.currentTimeMillis())
        }
        val id = db.insert(TABLE_FARM, null, values)
        
        if (id != -1L) {
            val newFarm = Farm(id.toInt(), name.trim(), location?.trim(), owner?.trim(), remarks?.trim(), 1, null, System.currentTimeMillis())
            firestoreHelper.syncFarm(newFarm)

            // Initialize basic data for the new farm
            db.insert(TABLE_GROUP, null, ContentValues().apply {
                put(COLUMN_GROUP_NAME, "General")
                put(COLUMN_FARM_ID, id)
            })
            listOf("Sukkari", "Sugai", "Ajwa").forEach { n ->
                db.insert(TABLE_DATE_TYPE, null, ContentValues().apply { put(COLUMN_DATE_TYPE_NAME, n); put(COLUMN_FARM_ID, id) })
            }
            listOf("Fuel", "Seeds", "Fertilizer", "Repairs", "Salaries", "Others").forEach { n ->
                db.insert(TABLE_EXP_CAT, null, ContentValues().apply { put(COLUMN_CAT_NAME, n); put(COLUMN_FARM_ID, id) })
            }
            listOf("Cash", "Partner A", "Partner B", "Loan").forEach { n ->
                db.insert(TABLE_FUND_SOURCE, null, ContentValues().apply { put(COLUMN_SOURCE_NAME, n); put(COLUMN_FARM_ID, id) })
            }
        }
        return id
    }

    fun getAllFarms(): List<Farm> {
        val list = mutableListOf<Farm>()
        val cursor = readableDatabase.query(TABLE_FARM, null, null, null, null, null, "$COLUMN_FARM_NAME ASC")
        while (cursor.moveToNext()) {
            list.add(Farm(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_FARM_ID_PK)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FARM_NAME)),
                location = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FARM_LOCATION)),
                owner = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FARM_OWNER)),
                remarks = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FARM_REMARKS)),
                activeStatus = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_FARM_STATUS)),
                createdBy = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FARM_CREATED_BY)),
                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FARM_TIMESTAMP))
            ))
        }
        cursor.close()
        return list
    }

    fun updateFarm(id: Int, name: String, location: String?, owner: String?, remarks: String?): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_FARM_NAME, name.trim())
            put(COLUMN_FARM_LOCATION, location?.trim())
            put(COLUMN_FARM_OWNER, owner?.trim())
            put(COLUMN_FARM_REMARKS, remarks?.trim())
        }
        val success = writableDatabase.update(TABLE_FARM, values, "$COLUMN_FARM_ID_PK = ?", arrayOf(id.toString())) > 0
        if (success) {
            val farm = getAllFarms().find { it.id == id }
            if (farm != null) firestoreHelper.syncFarm(farm)
        }
        return success
    }

    fun clearAndRestoreFarms(farms: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_FARM, null, null)
        farms.forEach { map ->
            val values = ContentValues().apply {
                put(COLUMN_FARM_ID_PK, (map["id"] as? Long)?.toInt() ?: 0)
                put(COLUMN_FARM_NAME, map["name"] as? String ?: "")
                put(COLUMN_FARM_LOCATION, map["location"] as? String)
                put(COLUMN_FARM_OWNER, map["owner"] as? String)
                put(COLUMN_FARM_REMARKS, map["remarks"] as? String)
                put(COLUMN_FARM_STATUS, (map["activeStatus"] as? Long)?.toInt() ?: 1)
                put(COLUMN_FARM_CREATED_BY, map["createdBy"] as? String)
                put(COLUMN_FARM_TIMESTAMP, (map["timestamp"] as? Long) ?: System.currentTimeMillis())
            }
            db.insert(TABLE_FARM, null, values)
        }
    }

    fun generateVoucherNumber(locale: Locale): String {
        val db = readableDatabase
        val farmId = getCurrentFarmId()
        val cursor = db.rawQuery("SELECT MAX($COLUMN_VOUCHER_ID) FROM $TABLE_EXP_VOUCHER WHERE $COLUMN_FARM_ID = ?", arrayOf(farmId.toString()))
        var nextId = 1
        if (cursor.moveToFirst()) {
            nextId = cursor.getInt(0) + 1
        }
        cursor.close()
        
        // Use the provided locale to ensure Arabic digits for AR and English for others (including UR)
        return "V-${String.format(locale, "%04d", nextId)}"
    }

    fun isAccountPayable(accountId: Int): Boolean {
        val cursor = readableDatabase.query(TABLE_FUND_SOURCE, arrayOf(COLUMN_SOURCE_NAME), "$COLUMN_SOURCE_ID = ?", arrayOf(accountId.toString()), null, null, null)
        var isPayable = true
        if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            // Anything that is NOT Cash or Bank is considered a Partner/Payable account
            if (name.equals("Cash", ignoreCase = true) || name.contains("Bank", ignoreCase = true)) {
                isPayable = false
            }
        }
        cursor.close()
        return isPayable
    }

    fun getTotalPartnerBalance(): Double {
        val accounts = getAllAccountsWithBalance()
        // For partner accounts, we flip the sign to match (Debit - Credit) convention
        // where negative means the business owes the partner.
        return accounts.filter { isAccountPayable(it.id) }
            .sumOf { -it.balance }
    }

    fun getTotalExpensesDone(): Double {
        val db = readableDatabase
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        // Total of all vouchers + total of all advances
        val vQuery = "SELECT SUM($COLUMN_VOUCHER_TOTAL) FROM $TABLE_EXP_VOUCHER WHERE $COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ?"
        val aQuery = "SELECT SUM($COLUMN_ADVANCE_AMOUNT) FROM $TABLE_ADVANCE WHERE $COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ?"
        
        var total = 0.0
        val vCursor = db.rawQuery(vQuery, arrayOf(farmId.toString(), seasonId.toString()))
        if (vCursor.moveToFirst()) total += vCursor.getDouble(0)
        vCursor.close()
        
        val aCursor = db.rawQuery(aQuery, arrayOf(farmId.toString(), seasonId.toString()))
        if (aCursor.moveToFirst()) total += aCursor.getDouble(0)
        aCursor.close()
        
        return total
    }

    fun addVoucher(vNumber: String, date: String, total: Double, by: String, items: List<ExpenseItem>, sourceId: Int = 0): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val farmId = getCurrentFarmId()
            val seasonId = getCurrentSeasonId()
            val vId = db.insert(TABLE_EXP_VOUCHER, null, ContentValues().apply {
                put(COLUMN_VOUCHER_NUMBER, vNumber)
                put(COLUMN_VOUCHER_DATE, date)
                put(COLUMN_VOUCHER_TOTAL, total)
                put(COLUMN_VOUCHER_BY, by)
                put(COLUMN_VOUCHER_SOURCE_ID, sourceId)
                put(COLUMN_FARM_ID, farmId)
                put(COLUMN_SEASON_LINK_ID, seasonId)
            })
            if (vId == -1L) return -1L
            items.forEach { 
                db.insert(TABLE_EXP_ITEM, null, ContentValues().apply {
                    put(COLUMN_EXP_VOUCHER_ID, vId)
                    put(COLUMN_EXP_CATEGORY, it.category)
                    put(COLUMN_EXP_AMOUNT, it.amount)
                    put(COLUMN_EXP_DESC, it.description)
                })
            }

            // ACCOUNTS LINK
            val type = if (isAccountPayable(sourceId)) "Credit" else "Debit"
            addAccountTransaction(sourceId, "Expense", vId.toInt(), type, total, vNumber, date, farmId)

            db.setTransactionSuccessful()
            
            val savedVoucher = getAllVouchers().find { it.id == vId.toInt() }
            if (savedVoucher != null) firestoreHelper.syncVoucher(savedVoucher)
            
            return vId
        } finally { db.endTransaction() }
    }

    fun deleteVoucher(voucherId: Int): Int {
        writableDatabase.delete(TABLE_EXP_ITEM, "$COLUMN_EXP_VOUCHER_ID = ?", arrayOf(voucherId.toString()))
        val rows = writableDatabase.delete(TABLE_EXP_VOUCHER, "$COLUMN_VOUCHER_ID = ?", arrayOf(voucherId.toString()))
        if (rows > 0) {
            firestoreHelper.deleteVoucher(voucherId)
            writableDatabase.delete(TABLE_ACCOUNT_TX, "$COLUMN_TX_SOURCE = 'Expense' AND $COLUMN_TX_REF_ID = ?", arrayOf(voucherId.toString()))
        }
        return rows
    }

    fun updateVoucher(vId: Int, date: String, total: Double, items: List<ExpenseItem>, sourceId: Int = 0): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Update Header
            db.update(TABLE_EXP_VOUCHER, ContentValues().apply {
                put(COLUMN_VOUCHER_DATE, date)
                put(COLUMN_VOUCHER_TOTAL, total)
                put(COLUMN_VOUCHER_SOURCE_ID, sourceId)
            }, "$COLUMN_VOUCHER_ID = ?", arrayOf(vId.toString()))
            
            // Re-sync items: simplest way is delete and re-insert
            db.delete(TABLE_EXP_ITEM, "$COLUMN_EXP_VOUCHER_ID = ?", arrayOf(vId.toString()))
            items.forEach { 
                db.insert(TABLE_EXP_ITEM, null, ContentValues().apply {
                    put(COLUMN_EXP_VOUCHER_ID, vId)
                    put(COLUMN_EXP_CATEGORY, it.category)
                    put(COLUMN_EXP_AMOUNT, it.amount)
                    put(COLUMN_EXP_DESC, it.description)
                })
            }

            // ACCOUNTS LINK
            val voucherNum = getAllVouchers().find { it.id == vId }?.voucherNumber ?: "Voucher"
            val type = if (isAccountPayable(sourceId)) "Credit" else "Debit"
            updateAccountTransaction("Expense", vId, sourceId, total, voucherNum, date, type)

            db.setTransactionSuccessful()
            
            val updatedVoucher = getAllVouchers().find { it.id == vId }
            if (updatedVoucher != null) firestoreHelper.syncVoucher(updatedVoucher)
            
            return true
        } catch (e: Exception) {
            return false
        } finally { db.endTransaction() }
    }

    fun getAllVouchers(): List<Voucher> {
        val list = mutableListOf<Voucher>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val query = "SELECT v.*, s.$COLUMN_SOURCE_NAME FROM $TABLE_EXP_VOUCHER v LEFT JOIN $TABLE_FUND_SOURCE s ON v.$COLUMN_VOUCHER_SOURCE_ID = s.$COLUMN_SOURCE_ID WHERE v.$COLUMN_FARM_ID = ? AND v.$COLUMN_SEASON_LINK_ID = ? ORDER BY v.$COLUMN_VOUCHER_DATE DESC, v.$COLUMN_VOUCHER_ID DESC"
        val cursor = readableDatabase.rawQuery(query, arrayOf(farmId.toString(), seasonId.toString()))
        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_ID))
            list.add(Voucher(
                id = id,
                voucherNumber = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_NUMBER)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_DATE)),
                totalAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_TOTAL)),
                recordedBy = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_BY)) ?: "",
                sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_SOURCE_ID)),
                sourceName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
                items = getVoucherItems(id),
                farmId = farmId
            ))
        }
        cursor.close()
        return list
    }

    private fun getVoucherItems(voucherId: Int): List<ExpenseItem> {
        val list = mutableListOf<ExpenseItem>()
        val cursor = readableDatabase.query(TABLE_EXP_ITEM, null, "$COLUMN_EXP_VOUCHER_ID = ?", arrayOf(voucherId.toString()), null, null, null)
        while (cursor.moveToNext()) {
            list.add(ExpenseItem(
                id = cursor.getInt(0),
                voucherId = cursor.getInt(1),
                category = cursor.getString(2),
                amount = cursor.getDouble(3),
                description = cursor.getString(4)
            ))
        }
        cursor.close()
        return list
    }

    fun getVouchersInRange(from: String, to: String): List<Voucher> {
        val list = mutableListOf<Voucher>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val query = "SELECT v.*, s.$COLUMN_SOURCE_NAME FROM $TABLE_EXP_VOUCHER v LEFT JOIN $TABLE_FUND_SOURCE s ON v.$COLUMN_VOUCHER_SOURCE_ID = s.$COLUMN_SOURCE_ID WHERE v.$COLUMN_FARM_ID = ? AND v.$COLUMN_SEASON_LINK_ID = ? AND v.$COLUMN_VOUCHER_DATE BETWEEN ? AND ? ORDER BY v.$COLUMN_VOUCHER_DATE ASC"
        val cursor = readableDatabase.rawQuery(query, arrayOf(farmId.toString(), seasonId.toString(), from, to))
        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_ID))
            list.add(Voucher(
                id = id,
                voucherNumber = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_NUMBER)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_DATE)),
                totalAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_TOTAL)),
                recordedBy = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_BY)) ?: "",
                sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VOUCHER_SOURCE_ID)),
                sourceName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
                items = getVoucherItems(id),
                farmId = farmId
            ))
        }
        cursor.close()
        return list
    }

    fun addExpCategory(name: String): Long {
        val farmId = getCurrentFarmId()
        val id = writableDatabase.insert(TABLE_EXP_CAT, null, ContentValues().apply {
            put(COLUMN_CAT_NAME, name.trim())
            put(COLUMN_FARM_ID, farmId)
        })
        if (id != -1L) firestoreHelper.syncExpCategory(ExpCategory(id.toInt(), name.trim(), farmId))
        return id
    }
    fun getAllExpCategories(): List<ExpCategory> {
        val list = mutableListOf<ExpCategory>()
        val farmId = getCurrentFarmId()
        val cursor = readableDatabase.query(TABLE_EXP_CAT, null, "$COLUMN_FARM_ID = ?", arrayOf(farmId.toString()), null, null, "$COLUMN_CAT_NAME ASC")
        while (cursor.moveToNext()) list.add(ExpCategory(cursor.getInt(0), cursor.getString(1), farmId))
        cursor.close()
        return list
    }
    fun deleteExpCategory(id: Int) { 
        writableDatabase.delete(TABLE_EXP_CAT, "id=?", arrayOf(id.toString()))
        firestoreHelper.deleteExpCategory(id)
    }

    // --- FUNDS METHODS ---

    fun getAllFundSources(): List<FundSource> {
        val list = mutableListOf<FundSource>()
        val farmId = getCurrentFarmId()
        val cursor = readableDatabase.query(TABLE_FUND_SOURCE, null, "$COLUMN_FARM_ID = ?", arrayOf(farmId.toString()), null, null, "$COLUMN_SOURCE_NAME ASC")
        while (cursor.moveToNext()) {
            list.add(FundSource(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_DESC)),
                farmId = farmId
            ))
        }
        cursor.close()
        return list
    }

    fun updateFundSource(id: Int, name: String, desc: String?): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_SOURCE_NAME, name.trim())
            put(COLUMN_SOURCE_DESC, desc?.trim())
        }
        val rows = writableDatabase.update(TABLE_FUND_SOURCE, values, "$COLUMN_SOURCE_ID = ?", arrayOf(id.toString()))
        if (rows > 0) firestoreHelper.syncFundSource(FundSource(id, name.trim(), desc?.trim()))
        return rows > 0
    }

    fun deleteFundSource(id: Int): Boolean {
        // Check if there are any transactions for this source
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_ACCOUNT_TX WHERE $COLUMN_TX_ACCOUNT_ID = ?", arrayOf(id.toString()))
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        
        if (count > 0) return false // Cannot delete account with transactions
        
        return writableDatabase.delete(TABLE_FUND_SOURCE, "$COLUMN_SOURCE_ID = ?", arrayOf(id.toString())) > 0
    }

    fun getSaleById(id: Int): Sale? {
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_SALE WHERE $COLUMN_SALE_ID = ?", arrayOf(id.toString()))
        var sale: Sale? = null
        if (cursor.moveToFirst()) {
            sale = Sale(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_ID)),
                buyerName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SALE_BUYER)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SALE_DATE)),
                totalAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SALE_TOTAL)),
                sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_SOURCE_ID)),
                items = getSaleItems(id)
            )
        }
        cursor.close()
        return sale
    }

    fun getFundEntryById(id: Int): FundEntry? {
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_FUND_ENTRY WHERE $COLUMN_FUND_ID = ?", arrayOf(id.toString()))
        var entry: FundEntry? = null
        if (cursor.moveToFirst()) {
            val sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_FUND_SOURCE_ID))
            val sources = getAllFundSources()
            val sourceName = sources.find { it.id == sourceId }?.name ?: "Unknown"
            entry = FundEntry(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_FUND_ID)),
                sourceId = sourceId,
                sourceName = sourceName,
                amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FUND_AMOUNT)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FUND_DATE)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FUND_DESC))
            )
        }
        cursor.close()
        return entry
    }

    fun getAdvanceById(id: Int): AdvanceRecord? {
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_ADVANCE WHERE $COLUMN_ADVANCE_ID = ?", arrayOf(id.toString()))
        var advance: AdvanceRecord? = null
        if (cursor.moveToFirst()) {
            val labourId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_LABOUR_ID))
            val labourName = getAllLabours().find { it.id == labourId }?.name ?: "Unknown"
            val sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_SOURCE_ID))
            val sourceName = getAllFundSources().find { it.id == sourceId }?.name ?: "Unknown"
            advance = AdvanceRecord(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_ID)),
                labourId = labourId,
                amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_AMOUNT)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_DATE)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_DESCRIPTION)),
                labourName = labourName,
                sourceId = sourceId,
                sourceName = sourceName,
                farmId = getCurrentFarmId()
            )
        }
        cursor.close()
        return advance
    }

    fun addFundEntry(sourceId: Int, amount: Double, date: String, desc: String?): Long {
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val values = ContentValues().apply {
            put(COLUMN_FUND_SOURCE_ID, sourceId)
            put(COLUMN_FUND_AMOUNT, amount)
            put(COLUMN_FUND_DATE, date)
            put(COLUMN_FUND_DESC, desc?.trim())
            put(COLUMN_FARM_ID, farmId)
            put(COLUMN_SEASON_LINK_ID, seasonId)
        }
        val id = writableDatabase.insert(TABLE_FUND_ENTRY, null, values)
        if (id != -1L) {
            val entry = getAllFundEntries().find { it.id == id.toInt() }
            if (entry != null) firestoreHelper.syncFundEntry(entry)
            
            // ACCOUNTS LINK
            val type = if (amount >= 0) "Credit" else "Debit"
            addAccountTransaction(sourceId, "Settlement", id.toInt(), type, Math.abs(amount), desc, date, farmId)
        }
        return id
    }

    fun deleteFundEntry(id: Int) {
        if (writableDatabase.delete(TABLE_FUND_ENTRY, "$COLUMN_FUND_ID = ?", arrayOf(id.toString())) > 0) {
            firestoreHelper.deleteFundEntry(id)
            writableDatabase.delete(TABLE_ACCOUNT_TX, "($COLUMN_TX_SOURCE = 'Funds' OR $COLUMN_TX_SOURCE = 'Settlement') AND $COLUMN_TX_REF_ID = ?", arrayOf(id.toString()))
        }
    }

    fun updateFundEntry(id: Int, sourceId: Int, amount: Double, date: String, desc: String?) {
        val values = ContentValues().apply {
            put(COLUMN_FUND_SOURCE_ID, sourceId)
            put(COLUMN_FUND_AMOUNT, amount)
            put(COLUMN_FUND_DATE, date)
            put(COLUMN_FUND_DESC, desc?.trim())
        }
        if (writableDatabase.update(TABLE_FUND_ENTRY, values, "$COLUMN_FUND_ID = ?", arrayOf(id.toString())) > 0) {
            val entry = getAllFundEntries().find { it.id == id }
            if (entry != null) firestoreHelper.syncFundEntry(entry)
            
            // ACCOUNTS LINK
            val type = if (amount >= 0) "Credit" else "Debit"
            updateAccountTransaction("Settlement", id, sourceId, Math.abs(amount), desc, date, type)
        }
    }

    fun getAllFundEntries(): List<FundEntry> {
        val list = mutableListOf<FundEntry>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val query = "SELECT f.*, s.$COLUMN_SOURCE_NAME FROM $TABLE_FUND_ENTRY f JOIN $TABLE_FUND_SOURCE s ON f.$COLUMN_FUND_SOURCE_ID = s.$COLUMN_SOURCE_ID WHERE f.$COLUMN_FARM_ID = ? AND f.$COLUMN_SEASON_LINK_ID = ? ORDER BY f.$COLUMN_FUND_DATE DESC, f.$COLUMN_FUND_ID DESC"
        val cursor = readableDatabase.rawQuery(query, arrayOf(farmId.toString(), seasonId.toString()))
        while (cursor.moveToNext()) {
            list.add(FundEntry(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_FUND_ID)),
                sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_FUND_SOURCE_ID)),
                sourceName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
                amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FUND_AMOUNT)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FUND_DATE)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FUND_DESC)),
                farmId = farmId
            ))
        }
        cursor.close()
        return list
    }

    // --- CLOUD DATA RECOVERY ---

    fun clearAndRestoreGroups(groups: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_GROUP, null, null)
        groups.forEach { map ->
            val values = ContentValues().apply {
                put(COLUMN_GROUP_ID, (map["id"] as? Long)?.toInt() ?: 0)
                put(COLUMN_GROUP_NAME, map["name"] as? String ?: "General")
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_GROUP, null, values)
        }
    }

    fun clearAndRestoreLabours(labours: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_LABOUR, null, null)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        
        labours.forEach { map ->
            val endDate = map["endDate"] as? String
            val isEffectiveActive = endDate.isNullOrBlank() || endDate >= today
            
            val values = ContentValues().apply {
                put(COLUMN_LABOUR_ID, (map["id"] as? Long)?.toInt() ?: 0)
                put(COLUMN_LABOUR_NAME, map["name"] as? String ?: "")
                put(COLUMN_LABOUR_GROUP_ID, (map["groupId"] as? Long)?.toInt() ?: 1)
                put(COLUMN_LABOUR_WAGE, map["wage"] as? Double ?: 0.0)
                put(COLUMN_DISPLAY_ORDER, (map["displayOrder"] as? Long)?.toInt() ?: 0)
                put(COLUMN_LABOUR_JOIN_DATE, map["joinDate"] as? String)
                put(COLUMN_LABOUR_END_DATE, endDate)
                put(COLUMN_LABOUR_STATUS, if (isEffectiveActive) "active" else "inactive")
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
                put(COLUMN_LABOUR_TYPE, map["labourType"] as? String ?: "DAILY_WAGE")
                put(COLUMN_LABOUR_REMARKS, map["remarks"] as? String)
            }
            db.insert(TABLE_LABOUR, null, values)
        }
    }

    fun clearAndRestoreAttendance(attendance: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_ATTENDANCE, null, null)
        attendance.forEach { map ->
            val values = ContentValues().apply {
                put(COLUMN_ATTENDANCE_LABOUR_ID, (map["labourId"] as? Long)?.toInt() ?: 0)
                put(COLUMN_ATTENDANCE_DATE, map["date"] as? String ?: "")
                put(COLUMN_ATTENDANCE_STATUS, map["status"] as? String ?: "")
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_ATTENDANCE, null, values)
        }
    }

    fun clearAndRestoreAdvances(advances: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_ADVANCE, null, null)
        advances.forEach { map ->
            val values = ContentValues().apply {
                put(COLUMN_ADVANCE_ID, (map["id"] as? Long)?.toInt() ?: 0)
                put(COLUMN_ADVANCE_LABOUR_ID, (map["labourId"] as? Long)?.toInt() ?: 0)
                put(COLUMN_ADVANCE_AMOUNT, map["amount"] as? Double ?: 0.0)
                put(COLUMN_ADVANCE_DATE, map["date"] as? String ?: "")
                put(COLUMN_ADVANCE_DESCRIPTION, map["description"] as? String ?: "")
                put(COLUMN_ADVANCE_SOURCE_ID, (map["sourceId"] as? Long)?.toInt() ?: 0)
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_ADVANCE, null, values)
        }
    }

    fun clearAndRestoreExpenditure(vouchers: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_EXP_VOUCHER, null, null)
        db.delete(TABLE_EXP_ITEM, null, null)
        vouchers.forEach { map ->
            val vId = (map["id"] as? Long)?.toInt() ?: 0
            val vValues = ContentValues().apply {
                put(COLUMN_VOUCHER_ID, vId)
                put(COLUMN_VOUCHER_NUMBER, map["voucherNumber"] as? String ?: "")
                put(COLUMN_VOUCHER_DATE, map["date"] as? String ?: "")
                put(COLUMN_VOUCHER_TOTAL, map["totalAmount"] as? Double ?: 0.0)
                put(COLUMN_VOUCHER_BY, map["recordedBy"] as? String ?: "")
                put(COLUMN_VOUCHER_SOURCE_ID, (map["sourceId"] as? Long)?.toInt() ?: 0)
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_EXP_VOUCHER, null, vValues)

            val items = map["items"] as? List<Map<String, Any>> ?: emptyList()
            items.forEach { iMap ->
                val iValues = ContentValues().apply {
                    put(COLUMN_EXP_VOUCHER_ID, vId)
                    put(COLUMN_EXP_CATEGORY, iMap["category"] as? String ?: "")
                    put(COLUMN_EXP_AMOUNT, iMap["amount"] as? Double ?: 0.0)
                    put(COLUMN_EXP_DESC, iMap["description"] as? String ?: "")
                }
                db.insert(TABLE_EXP_ITEM, null, iValues)
            }
        }
    }

    fun clearAndRestoreFundSources(sources: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_FUND_SOURCE, null, null)
        sources.forEach { map ->
            val values = ContentValues().apply {
                put(COLUMN_SOURCE_ID, (map["id"] as? Long)?.toInt() ?: 0)
                put(COLUMN_SOURCE_NAME, map["name"] as? String ?: "")
                put(COLUMN_SOURCE_DESC, map["description"] as? String ?: "")
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_FUND_SOURCE, null, values)
        }
    }

    fun clearAndRestoreFundEntries(entries: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_FUND_ENTRY, null, null)
        entries.forEach { map ->
            val values = ContentValues().apply {
                put(COLUMN_FUND_ID, (map["id"] as? Long)?.toInt() ?: 0)
                put(COLUMN_FUND_SOURCE_ID, (map["sourceId"] as? Long)?.toInt() ?: 0)
                put(COLUMN_FUND_AMOUNT, map["amount"] as? Double ?: 0.0)
                put(COLUMN_FUND_DATE, map["date"] as? String ?: "")
                put(COLUMN_FUND_DESC, map["description"] as? String ?: "")
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_FUND_ENTRY, null, values)
        }
    }

    fun clearAndRestoreVehicles(vehicles: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_VEHICLE, null, null)
        vehicles.forEach { map ->
            val vValues = ContentValues().apply {
                put(COLUMN_VEHICLE_ID, (map["id"] as? Long)?.toInt() ?: 0)
                put(COLUMN_VEHICLE_NUMBER, map["number"] as? String ?: "")
                put(COLUMN_VEHICLE_DRIVER_NAME, map["driverName"] as? String ?: "")
                put(COLUMN_VEHICLE_DRIVER_PHONE, map["driverPhone"] as? String ?: "")
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_VEHICLE, null, vValues)
        }
    }

    fun clearAndRestoreDateTypes(types: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_DATE_TYPE, null, null)
        types.forEach { map ->
            val vValues = ContentValues().apply {
                put(COLUMN_DATE_TYPE_ID, (map["id"] as? Long)?.toInt() ?: 0)
                put(COLUMN_DATE_TYPE_NAME, map["name"] as? String ?: "")
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_DATE_TYPE, null, vValues)
        }
    }

    fun clearAndRestoreDispatches(dispatches: List<Map<String, Any>>) {
        val db = writableDatabase
        db.delete(TABLE_DISPATCH, null, null)
        db.delete(TABLE_DISPATCH_ITEM, null, null)
        dispatches.forEach { map ->
            val dId = (map["id"] as? Long)?.toInt() ?: 0
            val vValues = ContentValues().apply {
                put(COLUMN_DISPATCH_ID, dId)
                put(COLUMN_DISPATCH_VEHICLE_ID, (map["vehicleId"] as? Long)?.toInt() ?: 0)
                put(COLUMN_DISPATCH_DATE, map["date"] as? String ?: "")
                put(COLUMN_FARM_ID, (map["farmId"] as? Long)?.toInt() ?: 1)
            }
            db.insert(TABLE_DISPATCH, null, vValues)

            val items = map["items"] as? List<Map<String, Any>> ?: emptyList()
            items.forEach { iMap ->
                val iValues = ContentValues().apply {
                    put(COLUMN_ITEM_DISPATCH_ID, dId)
                    put(COLUMN_ITEM_DATE_TYPE_ID, (iMap["dateTypeId"] as? Long)?.toInt() ?: 0)
                    put(COLUMN_ITEM_COUNT, (iMap["cartonCount"] as? Long)?.toInt() ?: 0)
                }
                db.insert(TABLE_DISPATCH_ITEM, null, iValues)
            }
        }
    }

    fun addAdvance(labourId: Int, amount: Double, date: String, description: String?, sourceId: Int = 0): Long {
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val resultId = writableDatabase.insert(TABLE_ADVANCE, null, ContentValues().apply {
            put(COLUMN_ADVANCE_LABOUR_ID, labourId)
            put(COLUMN_ADVANCE_AMOUNT, amount)
            put(COLUMN_ADVANCE_DATE, date)
            put(COLUMN_ADVANCE_DESCRIPTION, description)
            put(COLUMN_ADVANCE_SOURCE_ID, sourceId)
            put(COLUMN_FARM_ID, farmId)
            put(COLUMN_SEASON_LINK_ID, seasonId)
        })
        if (resultId != -1L) {
            firestoreHelper.syncAdvance(AdvanceRecord(resultId.toInt(), labourId, amount, date, description, null, sourceId, null, farmId))
            
            // ACCOUNTS LINK
            val labourName = getAllLabours().find { it.id == labourId }?.name ?: "Labour"
            val type = if (isAccountPayable(sourceId)) "Credit" else "Debit"
            addAccountTransaction(sourceId, "Advance", resultId.toInt(), type, amount, labourName, date, farmId)
        }
        return resultId
    }

    fun getAllAdvances(): List<AdvanceRecord> {
        val advances = mutableListOf<AdvanceRecord>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val query = "SELECT a.*, l.name as labour_name, s.$COLUMN_SOURCE_NAME FROM $TABLE_ADVANCE a JOIN $TABLE_LABOUR l ON a.$COLUMN_ADVANCE_LABOUR_ID = l.$COLUMN_LABOUR_ID LEFT JOIN $TABLE_FUND_SOURCE s ON a.$COLUMN_ADVANCE_SOURCE_ID = s.$COLUMN_SOURCE_ID WHERE a.$COLUMN_FARM_ID = ? AND a.$COLUMN_SEASON_LINK_ID = ? ORDER BY a.$COLUMN_ADVANCE_DATE DESC, a.$COLUMN_ADVANCE_ID DESC"
        val cursor = readableDatabase.rawQuery(query, arrayOf(farmId.toString(), seasonId.toString()))
        while (cursor.moveToNext()) {
            advances.add(AdvanceRecord(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_ID)),
                labourId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_LABOUR_ID)),
                amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_AMOUNT)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_DATE)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_DESCRIPTION)),
                labourName = cursor.getString(cursor.getColumnIndexOrThrow("labour_name")),
                sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_SOURCE_ID)),
                sourceName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
                farmId = farmId
            ))
        }
        cursor.close()
        return advances
    }

    fun deleteAdvance(advanceId: Int): Int {
        val rows = writableDatabase.delete(TABLE_ADVANCE, "$COLUMN_ADVANCE_ID = ?", arrayOf(advanceId.toString()))
        if (rows > 0) {
            firestoreHelper.deleteAdvance(advanceId)
            writableDatabase.delete(TABLE_ACCOUNT_TX, "$COLUMN_TX_SOURCE = 'Advance' AND $COLUMN_TX_REF_ID = ?", arrayOf(advanceId.toString()))
        }
        return rows
    }

    fun updateAdvance(id: Int, amount: Double, date: String, description: String?, sourceId: Int = 0): Int {
        val rows = writableDatabase.update(TABLE_ADVANCE, ContentValues().apply {
            put(COLUMN_ADVANCE_AMOUNT, amount)
            put(COLUMN_ADVANCE_DATE, date)
            put(COLUMN_ADVANCE_DESCRIPTION, description)
            put(COLUMN_ADVANCE_SOURCE_ID, sourceId)
        }, "$COLUMN_ADVANCE_ID = ?", arrayOf(id.toString()))
        if (rows > 0) {
            firestoreHelper.syncAdvance(AdvanceRecord(id, 0, amount, date, description, null, sourceId))
            
            // ACCOUNTS LINK
            val labourName = getAllAdvances().find { it.id == id }?.labourName ?: "Labour"
            val type = if (isAccountPayable(sourceId)) "Credit" else "Debit"
            updateAccountTransaction("Advance", id, sourceId, amount, labourName, date, type)
        }
        return rows
    }

    fun deleteAttendance(labourId: Int, date: String): Int {
        val rows = writableDatabase.delete(TABLE_ATTENDANCE, "$COLUMN_ATTENDANCE_LABOUR_ID = ? AND $COLUMN_ATTENDANCE_DATE = ?", arrayOf(labourId.toString(), date))
        if (rows > 0) firestoreHelper.deleteAttendance(labourId, date)
        return rows
    }

    fun getAdvancesInRange(fromDate: String, toDate: String): List<AdvanceRecord> {
        val advances = mutableListOf<AdvanceRecord>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val query = "SELECT a.*, s.$COLUMN_SOURCE_NAME FROM $TABLE_ADVANCE a LEFT JOIN $TABLE_FUND_SOURCE s ON a.$COLUMN_ADVANCE_SOURCE_ID = s.$COLUMN_SOURCE_ID WHERE a.$COLUMN_FARM_ID = ? AND a.$COLUMN_SEASON_LINK_ID = ? AND a.$COLUMN_ADVANCE_DATE BETWEEN ? AND ? ORDER BY a.$COLUMN_ADVANCE_DATE ASC"
        val cursor = readableDatabase.rawQuery(query, arrayOf(farmId.toString(), seasonId.toString(), fromDate, toDate))
        while (cursor.moveToNext()) {
            advances.add(AdvanceRecord(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_ID)),
                labourId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_LABOUR_ID)),
                amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_AMOUNT)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_DATE)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_DESCRIPTION)),
                sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ADVANCE_SOURCE_ID)),
                sourceName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
                farmId = farmId
            ))
        }
        cursor.close()
        return advances
    }

    fun getTotalAdvanceForLabour(labourId: Int): Double {
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val cursor = readableDatabase.rawQuery("SELECT SUM($COLUMN_ADVANCE_AMOUNT) FROM $TABLE_ADVANCE WHERE $COLUMN_ADVANCE_LABOUR_ID = ? AND $COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ?", arrayOf(labourId.toString(), farmId.toString(), seasonId.toString()))
        var total = 0.0
        if (cursor.moveToFirst()) total = cursor.getDouble(0)
        cursor.close()
        return total
    }

    fun addGroup(name: String): Long {
        val farmId = getCurrentFarmId()
        val id = writableDatabase.insert(TABLE_GROUP, null, ContentValues().apply {
            put(COLUMN_GROUP_NAME, name.trim())
            put(COLUMN_FARM_ID, farmId)
        })
        if (id != -1L) firestoreHelper.syncGroup(Group(id.toInt(), name.trim(), farmId))
        return id
    }

    fun getAllGroups(): List<Group> {
        val groups = mutableListOf<Group>()
        val farmId = getCurrentFarmId()
        val cursor = readableDatabase.query(TABLE_GROUP, null, "$COLUMN_FARM_ID = ?", arrayOf(farmId.toString()), null, null, "$COLUMN_GROUP_NAME ASC")
        while (cursor.moveToNext()) {
            groups.add(Group(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_GROUP_ID)), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GROUP_NAME)), farmId))
        }
        cursor.close()
        return groups
    }

    fun deleteGroup(groupId: Int) {
        if (groupId == 1) return
        writableDatabase.update(TABLE_LABOUR, ContentValues().apply { put(COLUMN_LABOUR_GROUP_ID, 1) }, "$COLUMN_LABOUR_GROUP_ID = ?", arrayOf(groupId.toString()))
        val rows = writableDatabase.delete(TABLE_GROUP, "$COLUMN_GROUP_ID = ?", arrayOf(groupId.toString()))
        if (rows > 0) firestoreHelper.deleteGroup(groupId)
    }

    fun addLabour(name: String, groupId: Int = 1, wage: Double = 0.0, joinDate: String? = null, labourType: String = "DAILY_WAGE", remarks: String? = null): Long {
        val farmId = getCurrentFarmId()
        val nextOrder = getNextDisplayOrder()
        val id = writableDatabase.insert(TABLE_LABOUR, null, ContentValues().apply {
            put(COLUMN_LABOUR_NAME, name.trim())
            put(COLUMN_LABOUR_GROUP_ID, groupId)
            put(COLUMN_LABOUR_WAGE, wage)
            put(COLUMN_DISPLAY_ORDER, nextOrder)
            put(COLUMN_LABOUR_JOIN_DATE, joinDate)
            put(COLUMN_LABOUR_STATUS, "active")
            put(COLUMN_FARM_ID, farmId)
            put(COLUMN_LABOUR_TYPE, labourType)
            put(COLUMN_LABOUR_REMARKS, remarks?.trim())
        })
        if (id != -1L) firestoreHelper.syncLabour(Labour(id.toInt(), name.trim(), groupId, wage, nextOrder, joinDate, null, "active", farmId, labourType, remarks))
        return id
    }

    private fun getNextDisplayOrder(): Int {
        val farmId = getCurrentFarmId()
        val cursor = readableDatabase.rawQuery("SELECT MAX($COLUMN_DISPLAY_ORDER) FROM $TABLE_LABOUR WHERE $COLUMN_FARM_ID = ?", arrayOf(farmId.toString()))
        var nextOrder = 1
        if (cursor.moveToFirst()) nextOrder = cursor.getInt(0) + 1
        cursor.close()
        return nextOrder
    }

    fun getAllLabours(): List<Labour> {
        val list = mutableListOf<Labour>()
        val farmId = getCurrentFarmId()
        val cursor = readableDatabase.query(TABLE_LABOUR, null, "$COLUMN_FARM_ID = ?", arrayOf(farmId.toString()), null, null, "$COLUMN_DISPLAY_ORDER ASC")
        while (cursor.moveToNext()) {
            list.add(Labour(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_NAME)),
                groupId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_GROUP_ID)),
                wage = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_WAGE)),
                displayOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DISPLAY_ORDER)),
                joinDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_JOIN_DATE)),
                endDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_END_DATE)),
                status = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_STATUS)) ?: "active",
                farmId = farmId,
                labourType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_TYPE)) ?: "DAILY_WAGE",
                remarks = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_REMARKS))
            ))
        }
        cursor.close()
        return list
    }

    fun getLaboursByGroup(groupId: Int): List<Labour> {
        val list = mutableListOf<Labour>()
        val farmId = getCurrentFarmId()
        val cursor = readableDatabase.query(TABLE_LABOUR, null, "$COLUMN_LABOUR_GROUP_ID = ? AND $COLUMN_FARM_ID = ?", arrayOf(groupId.toString(), farmId.toString()), null, null, "$COLUMN_DISPLAY_ORDER ASC")
        while (cursor.moveToNext()) {
            list.add(Labour(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_NAME)),
                groupId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_GROUP_ID)),
                wage = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_WAGE)),
                displayOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DISPLAY_ORDER)),
                joinDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_JOIN_DATE)),
                endDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_END_DATE)),
                status = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_STATUS)) ?: "active",
                farmId = farmId,
                labourType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_TYPE)) ?: "DAILY_WAGE",
                remarks = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABOUR_REMARKS))
            ))
        }
        cursor.close()
        return list
    }

    fun updateLabour(labourId: Int, newName: String, groupId: Int, wage: Double, joinDate: String?, endDate: String?, status: String, labourType: String = "DAILY_WAGE", remarks: String? = null): Int {
        val rows = writableDatabase.update(TABLE_LABOUR, ContentValues().apply {
            put(COLUMN_LABOUR_NAME, newName.trim())
            put(COLUMN_LABOUR_GROUP_ID, groupId)
            put(COLUMN_LABOUR_WAGE, wage)
            put(COLUMN_LABOUR_JOIN_DATE, joinDate)
            put(COLUMN_LABOUR_END_DATE, endDate)
            put(COLUMN_LABOUR_STATUS, status)
            put(COLUMN_LABOUR_TYPE, labourType)
            put(COLUMN_LABOUR_REMARKS, remarks?.trim())
        }, "$COLUMN_LABOUR_ID = ?", arrayOf(labourId.toString()))
        if (rows > 0) {
            val labour = getAllLabours().find { it.id == labourId }
            if (labour != null) firestoreHelper.syncLabour(labour)
        }
        return rows
    }

    fun getActiveLaboursForDate(date: String, groupId: Int = 0): List<Labour> {
        val all = if (groupId == 0) getAllLabours() else getLaboursByGroup(groupId)
        return all.filter { labour ->
            val joined = labour.joinDate.isNullOrBlank() || labour.joinDate!! <= date
            val notLeft = labour.endDate.isNullOrBlank() || labour.endDate == "null" || labour.endDate!!.length < 10 || labour.endDate!! >= date
            joined && notLeft
        }
    }

    fun deleteLabour(labourId: Int): Int {
        writableDatabase.delete(TABLE_ATTENDANCE, "$COLUMN_ATTENDANCE_LABOUR_ID = ?", arrayOf(labourId.toString()))
        writableDatabase.delete(TABLE_ADVANCE, "$COLUMN_ADVANCE_LABOUR_ID = ?", arrayOf(labourId.toString()))
        val rows = writableDatabase.delete(TABLE_LABOUR, "$COLUMN_LABOUR_ID = ?", arrayOf(labourId.toString()))
        if (rows > 0) firestoreHelper.deleteLabour(labourId)
        normalizeDisplayOrder()
        return rows
    }

    fun moveLabour(labourId: Int, moveUp: Boolean): Boolean {
        val list = getAllLabours()
        val index = list.indexOfFirst { it.id == labourId }
        val target = if (moveUp) index - 1 else index + 1
        if (index == -1 || target !in list.indices) return false
        val current = list[index]
        val targetLabour = list[target]
        writableDatabase.update(TABLE_LABOUR, ContentValues().apply { put(COLUMN_DISPLAY_ORDER, targetLabour.displayOrder) }, "$COLUMN_LABOUR_ID = ?", arrayOf(current.id.toString()))
        writableDatabase.update(TABLE_LABOUR, ContentValues().apply { put(COLUMN_DISPLAY_ORDER, current.displayOrder) }, "$COLUMN_LABOUR_ID = ?", arrayOf(targetLabour.id.toString()))
        firestoreHelper.syncLabour(current.copy(displayOrder = targetLabour.displayOrder))
        firestoreHelper.syncLabour(targetLabour.copy(displayOrder = current.displayOrder))
        return true
    }

    fun getMonthlySummary(yearMonth: String): List<MonthlySummary> {
        val summaryList = mutableListOf<MonthlySummary>()
        val allLabours = getAllLabours()
        val fromDate = "$yearMonth-01"
        val toDate = "$yearMonth-31"
        val attendanceEntries = getAttendanceEntries(fromDate, toDate)
        val groupedEntries = attendanceEntries.groupBy { it.labourId }
        val advances = getAdvancesInRange(fromDate, toDate)
        val groupedAdvances = advances.groupBy { it.labourId }
        
        val seasonId = getCurrentSeasonId()
        
        for (labour in allLabours) {
            // Re-fetch counts considering seasonId indirectly via getAttendanceEntries and getAdvancesInRange
            // Actually groupedEntries and groupedAdvances are already filtered by getAttendanceEntries and getAdvancesInRange
            // and those methods now use getCurrentSeasonId().
            // So we don't need to manually filter here, but we should make sure those pre-fetched lists ARE seasonal.
            val labourEntries = groupedEntries[labour.id].orEmpty()
            val labourAdvances = groupedAdvances[labour.id].orEmpty()
            
            val hasData = labourEntries.isNotEmpty() || labourAdvances.isNotEmpty()
            val wasActiveDuringMonth = (labour.joinDate == null || labour.joinDate <= toDate) && (labour.endDate == null || labour.endDate >= fromDate)
            
            if (hasData || wasActiveDuringMonth) {
                summaryList.add(
                    MonthlySummary(
                        labourId = labour.id,
                        labourName = labour.name,
                        presentCount = labourEntries.count { it.status == "P" },
                        absentCount = labourEntries.count { it.status == "A" },
                        halfDayCount = labourEntries.count { it.status == "H" },
                        totalAdvance = labourAdvances.sumOf { it.amount }
                    )
                )
            }
        }
        return summaryList
    }

    fun getAttendanceEntries(fromDate: String, toDate: String): List<AttendanceEntry> {
        val list = mutableListOf<AttendanceEntry>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val cursor = readableDatabase.query(TABLE_ATTENDANCE, null, "$COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ? AND $COLUMN_ATTENDANCE_DATE BETWEEN ? AND ?", arrayOf(farmId.toString(), seasonId.toString(), fromDate, toDate), null, null, "$COLUMN_ATTENDANCE_DATE ASC, $COLUMN_ATTENDANCE_LABOUR_ID ASC")
        while (cursor.moveToNext()) {
            list.add(AttendanceEntry(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ATTENDANCE_LABOUR_ID)), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ATTENDANCE_DATE)), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ATTENDANCE_STATUS)), farmId))
        }
        cursor.close()
        return list
    }

    fun getAttendanceStatus(labourId: Int, date: String): String? {
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val cursor = readableDatabase.query(TABLE_ATTENDANCE, arrayOf(COLUMN_ATTENDANCE_STATUS), "$COLUMN_ATTENDANCE_LABOUR_ID = ? AND $COLUMN_ATTENDANCE_DATE = ? AND $COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ?", arrayOf(labourId.toString(), date, farmId.toString(), seasonId.toString()), null, null, null)
        var status: String? = null
        if (cursor.moveToFirst()) status = cursor.getString(0)
        cursor.close()
        return status
    }

    fun saveAttendance(labourId: Int, date: String, status: String) {
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val values = ContentValues().apply { 
            put(COLUMN_ATTENDANCE_LABOUR_ID, labourId)
            put(COLUMN_ATTENDANCE_DATE, date)
            put(COLUMN_ATTENDANCE_STATUS, status)
            put(COLUMN_FARM_ID, farmId)
            put(COLUMN_SEASON_LINK_ID, seasonId)
        }
        if (writableDatabase.update(TABLE_ATTENDANCE, values, "$COLUMN_ATTENDANCE_LABOUR_ID = ? AND $COLUMN_ATTENDANCE_DATE = ? AND $COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ?", arrayOf(labourId.toString(), date, farmId.toString(), seasonId.toString())) == 0) {
            writableDatabase.insert(TABLE_ATTENDANCE, null, values)
        }
        firestoreHelper.syncAttendance(AttendanceEntry(labourId, date, status, farmId))
    }

    private fun normalizeDisplayOrder() {
        getAllLabours().forEachIndexed { i, l ->
            writableDatabase.update(TABLE_LABOUR, ContentValues().apply { put(COLUMN_DISPLAY_ORDER, i + 1) }, "$COLUMN_LABOUR_ID = ?", arrayOf(l.id.toString()))
            firestoreHelper.syncLabour(l.copy(displayOrder = i + 1))
        }
    }

    fun addVehicle(n: String, d: String, p: String): Long {
        val farmId = getCurrentFarmId()
        val id = writableDatabase.insert(TABLE_VEHICLE, null, ContentValues().apply { 
            put(COLUMN_VEHICLE_NUMBER, n.trim())
            put(COLUMN_VEHICLE_DRIVER_NAME, d.trim())
            put(COLUMN_VEHICLE_DRIVER_PHONE, p.trim())
            put(COLUMN_FARM_ID, farmId)
        })
        if (id != -1L) firestoreHelper.syncVehicle(Vehicle(id.toInt(), n.trim(), d.trim(), p.trim(), farmId))
        return id
    }

    fun getAllVehicles(): List<Vehicle> {
        val list = mutableListOf<Vehicle>()
        val farmId = getCurrentFarmId()
        val cursor = readableDatabase.query(TABLE_VEHICLE, null, "$COLUMN_FARM_ID = ?", arrayOf(farmId.toString()), null, null, "$COLUMN_VEHICLE_NUMBER ASC")
        while (cursor.moveToNext()) list.add(Vehicle(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), farmId))
        cursor.close()
        return list
    }

    fun updateVehicle(id: Int, n: String, d: String, p: String): Int {
        val rows = writableDatabase.update(TABLE_VEHICLE, ContentValues().apply {
            put(COLUMN_VEHICLE_NUMBER, n.trim())
            put(COLUMN_VEHICLE_DRIVER_NAME, d.trim())
            put(COLUMN_VEHICLE_DRIVER_PHONE, p.trim())
        }, "$COLUMN_VEHICLE_ID = ?", arrayOf(id.toString()))
        if (rows > 0) firestoreHelper.syncVehicle(Vehicle(id, n.trim(), d.trim(), p.trim()))
        return rows
    }

    fun deleteVehicle(id: Int) { 
        writableDatabase.delete(TABLE_VEHICLE, "id=?", arrayOf(id.toString()))
        firestoreHelper.deleteVehicle(id) 
    }

    fun addDateType(n: String): Long {
        val farmId = getCurrentFarmId()
        val id = writableDatabase.insert(TABLE_DATE_TYPE, null, ContentValues().apply { 
            put(COLUMN_DATE_TYPE_NAME, n.trim())
            put(COLUMN_FARM_ID, farmId)
        })
        if (id != -1L) firestoreHelper.syncDateType(DateType(id.toInt(), n.trim(), farmId))
        return id
    }

    fun getAllDateTypes(): List<DateType> {
        val list = mutableListOf<DateType>()
        val farmId = getCurrentFarmId()
        val cursor = readableDatabase.query(TABLE_DATE_TYPE, null, "$COLUMN_FARM_ID = ?", arrayOf(farmId.toString()), null, null, "$COLUMN_DATE_TYPE_NAME ASC")
        while (cursor.moveToNext()) list.add(DateType(cursor.getInt(0), cursor.getString(1), farmId))
        cursor.close()
        return list
    }
    fun deleteDateType(id: Int) { 
        writableDatabase.delete(TABLE_DATE_TYPE, "id=?", arrayOf(id.toString()))
        firestoreHelper.deleteDateType(id)
    }

    fun addDispatch(vId: Int, d: String, items: List<DispatchItem>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val farmId = getCurrentFarmId()
            val seasonId = getCurrentSeasonId()
            val dispatchId = db.insert(TABLE_DISPATCH, null, ContentValues().apply { 
                put(COLUMN_DISPATCH_VEHICLE_ID, vId)
                put(COLUMN_DISPATCH_DATE, d)
                put(COLUMN_FARM_ID, farmId)
                put(COLUMN_SEASON_LINK_ID, seasonId)
            })
            if (dispatchId == -1L) return -1L
            items.forEach { db.insert(TABLE_DISPATCH_ITEM, null, ContentValues().apply { put(COLUMN_ITEM_DISPATCH_ID, dispatchId); put(COLUMN_ITEM_DATE_TYPE_ID, it.dateTypeId); put(COLUMN_ITEM_COUNT, it.cartonCount) }) }
            db.setTransactionSuccessful()
            
            val record = getAllDispatches().find { it.id == dispatchId.toInt() }
            if (record != null) firestoreHelper.syncDispatch(record)

            return dispatchId
        } finally { db.endTransaction() }
    }

    fun getAllDispatches(): List<DispatchRecord> {
        val list = mutableListOf<DispatchRecord>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val cursor = readableDatabase.rawQuery("SELECT d.*, v.$COLUMN_VEHICLE_NUMBER, v.$COLUMN_VEHICLE_DRIVER_NAME FROM $TABLE_DISPATCH d JOIN $TABLE_VEHICLE v ON d.$COLUMN_DISPATCH_VEHICLE_ID = v.$COLUMN_VEHICLE_ID WHERE d.$COLUMN_FARM_ID = ? AND d.$COLUMN_SEASON_LINK_ID = ? ORDER BY d.$COLUMN_DISPATCH_DATE DESC", arrayOf(farmId.toString(), seasonId.toString()))
        while (cursor.moveToNext()) {
            val id = cursor.getInt(0)
            list.add(DispatchRecord(id, cursor.getInt(1), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VEHICLE_NUMBER)), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VEHICLE_DRIVER_NAME)), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISPATCH_DATE)), null, getDispatchItems(id), farmId))
        }
        cursor.close()
        return list
    }

    fun getDispatchesInRange(from: String, to: String): List<DispatchRecord> {
        val list = mutableListOf<DispatchRecord>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val cursor = readableDatabase.rawQuery("SELECT d.*, v.$COLUMN_VEHICLE_NUMBER, v.$COLUMN_VEHICLE_DRIVER_NAME FROM $TABLE_DISPATCH d JOIN $TABLE_VEHICLE v ON d.$COLUMN_DISPATCH_VEHICLE_ID = v.$COLUMN_VEHICLE_ID WHERE d.$COLUMN_FARM_ID = ? AND d.$COLUMN_SEASON_LINK_ID = ? AND d.$COLUMN_DISPATCH_DATE BETWEEN ? AND ? ORDER BY d.$COLUMN_DISPATCH_DATE ASC", arrayOf(farmId.toString(), seasonId.toString(), from, to))
        while (cursor.moveToNext()) {
            val id = cursor.getInt(0)
            list.add(DispatchRecord(id, cursor.getInt(1), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VEHICLE_NUMBER)), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VEHICLE_DRIVER_NAME)), cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISPATCH_DATE)), null, getDispatchItems(id), farmId))
        }
        cursor.close()
        return list
    }

    fun getDispatchItems(dispatchId: Int): List<DispatchItem> {
        val list = mutableListOf<DispatchItem>()
        val cursor = readableDatabase.rawQuery("SELECT i.*, t.$COLUMN_DATE_TYPE_NAME FROM $TABLE_DISPATCH_ITEM i JOIN $TABLE_DATE_TYPE t ON i.$COLUMN_ITEM_DATE_TYPE_ID = t.$COLUMN_DATE_TYPE_ID WHERE i.$COLUMN_ITEM_DISPATCH_ID = ?", arrayOf(dispatchId.toString()))
        while (cursor.moveToNext()) list.add(DispatchItem(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getString(4), cursor.getInt(3)))
        cursor.close()
        return list
    }

    fun deleteDispatch(id: Int) { 
        writableDatabase.delete(TABLE_DISPATCH_ITEM, "$COLUMN_ITEM_DISPATCH_ID=?", arrayOf(id.toString()))
        writableDatabase.delete(TABLE_DISPATCH, "$COLUMN_DISPATCH_ID=?", arrayOf(id.toString()))
        firestoreHelper.deleteDispatch(id)
    }

    // --- SALES & INVENTORY ---

    fun getInventoryStatus(): List<StockStatus> {
        val list = mutableListOf<StockStatus>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        // This query joins Dispatches with Vehicles and then subtracts summed Sales per dispatch+type
        val query = """
            SELECT 
                d.$COLUMN_DISPATCH_ID, 
                v.$COLUMN_VEHICLE_NUMBER, 
                d.$COLUMN_DISPATCH_DATE, 
                dt.$COLUMN_DATE_TYPE_ID, 
                dt.$COLUMN_DATE_TYPE_NAME, 
                di.$COLUMN_ITEM_COUNT as dispatched,
                IFNULL((SELECT SUM(si.$COLUMN_SALE_ITEM_QTY) 
                        FROM $TABLE_SALE_ITEM si 
                        WHERE si.$COLUMN_SALE_ITEM_DISPATCH_ID = d.$COLUMN_DISPATCH_ID 
                        AND si.$COLUMN_SALE_ITEM_DATE_TYPE_ID = dt.$COLUMN_DATE_TYPE_ID), 0) as sold
            FROM $TABLE_DISPATCH_ITEM di
            JOIN $TABLE_DISPATCH d ON di.$COLUMN_ITEM_DISPATCH_ID = d.$COLUMN_DISPATCH_ID
            JOIN $TABLE_VEHICLE v ON d.$COLUMN_DISPATCH_VEHICLE_ID = v.$COLUMN_VEHICLE_ID
            JOIN $TABLE_DATE_TYPE dt ON di.$COLUMN_ITEM_DATE_TYPE_ID = dt.$COLUMN_DATE_TYPE_ID
            WHERE d.$COLUMN_FARM_ID = ? AND d.$COLUMN_SEASON_LINK_ID = ?
            ORDER BY d.$COLUMN_DISPATCH_DATE DESC, d.$COLUMN_DISPATCH_ID DESC
        """.trimIndent()

        val cursor = readableDatabase.rawQuery(query, arrayOf(farmId.toString(), seasonId.toString()))
        while (cursor.moveToNext()) {
            val dispatched = cursor.getInt(cursor.getColumnIndexOrThrow("dispatched"))
            val sold = cursor.getInt(cursor.getColumnIndexOrThrow("sold"))
            list.add(StockStatus(
                dispatchId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DISPATCH_ID)),
                vehicleNumber = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VEHICLE_NUMBER)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISPATCH_DATE)),
                dateTypeId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DATE_TYPE_ID)),
                dateTypeName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE_TYPE_NAME)),
                dispatchedCount = dispatched,
                soldCount = sold,
                remainingCount = dispatched - sold
            ))
        }
        cursor.close()
        return list
    }

    fun addSale(buyer: String, date: String, items: List<SaleItem>, sourceId: Int = 0): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val farmId = getCurrentFarmId()
            val seasonId = getCurrentSeasonId()
            val total = items.sumOf { it.quantity * it.unitPrice }
            val saleId = db.insert(TABLE_SALE, null, ContentValues().apply {
                put(COLUMN_SALE_BUYER, buyer)
                put(COLUMN_SALE_DATE, date)
                put(COLUMN_SALE_TOTAL, total)
                put(COLUMN_SALE_SOURCE_ID, sourceId)
                put(COLUMN_FARM_ID, farmId)
                put(COLUMN_SEASON_LINK_ID, seasonId)
            })
            if (saleId == -1L) return -1L

            items.forEach { item ->
                db.insert(TABLE_SALE_ITEM, null, ContentValues().apply {
                    put(COLUMN_SALE_ITEM_SALE_ID, saleId)
                    put(COLUMN_SALE_ITEM_DISPATCH_ID, item.dispatchId)
                    put(COLUMN_SALE_ITEM_DATE_TYPE_ID, item.dateTypeId)
                    put(COLUMN_SALE_ITEM_QTY, item.quantity)
                    put(COLUMN_SALE_ITEM_PRICE, item.unitPrice)
                })
            }

            // ACCOUNTS LINK
            val type = if (isAccountPayable(sourceId)) "Debit" else "Credit"
            addAccountTransaction(sourceId, "Sales", saleId.toInt(), type, total, buyer, date, farmId)

            db.setTransactionSuccessful()
            // Cloud sync can be added here if firestoreHelper is updated
            return saleId
        } finally {
            db.endTransaction()
        }
    }

    fun getAllSales(): List<Sale> {
        val list = mutableListOf<Sale>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val query = "SELECT s.*, src.$COLUMN_SOURCE_NAME FROM $TABLE_SALE s LEFT JOIN $TABLE_FUND_SOURCE src ON s.$COLUMN_SALE_SOURCE_ID = src.$COLUMN_SOURCE_ID WHERE s.$COLUMN_FARM_ID = ? AND s.$COLUMN_SEASON_LINK_ID = ? ORDER BY s.$COLUMN_SALE_DATE DESC, s.$COLUMN_SALE_ID DESC"
        val cursor = readableDatabase.rawQuery(query, arrayOf(farmId.toString(), seasonId.toString()))
        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_ID))
            list.add(Sale(
                id = id,
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SALE_DATE)),
                buyerName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SALE_BUYER)),
                totalAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SALE_TOTAL)),
                sourceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_SOURCE_ID)),
                sourceName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
                items = getSaleItems(id),
                farmId = farmId
            ))
        }
        cursor.close()
        return list
    }

    private fun getSaleItems(saleId: Int): List<SaleItem> {
        val list = mutableListOf<SaleItem>()
        val query = """
            SELECT si.*, dt.$COLUMN_DATE_TYPE_NAME, v.$COLUMN_VEHICLE_NUMBER
            FROM $TABLE_SALE_ITEM si
            JOIN $TABLE_DATE_TYPE dt ON si.$COLUMN_SALE_ITEM_DATE_TYPE_ID = dt.$COLUMN_DATE_TYPE_ID
            JOIN $TABLE_DISPATCH d ON si.$COLUMN_SALE_ITEM_DISPATCH_ID = d.$COLUMN_DISPATCH_ID
            JOIN $TABLE_VEHICLE v ON d.$COLUMN_DISPATCH_VEHICLE_ID = v.$COLUMN_VEHICLE_ID
            WHERE si.$COLUMN_SALE_ITEM_SALE_ID = ?
        """.trimIndent()
        val cursor = readableDatabase.rawQuery(query, arrayOf(saleId.toString()))
        while (cursor.moveToNext()) {
            list.add(SaleItem(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_ITEM_ID)),
                saleId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_ITEM_SALE_ID)),
                dispatchId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_ITEM_DISPATCH_ID)),
                vehicleNumber = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VEHICLE_NUMBER)),
                dateTypeId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_ITEM_DATE_TYPE_ID)),
                dateTypeName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE_TYPE_NAME)),
                quantity = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SALE_ITEM_QTY)),
                unitPrice = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SALE_ITEM_PRICE))
            ))
        }
        cursor.close()
        return list
    }

    fun deleteSale(saleId: Int) {
        writableDatabase.delete(TABLE_SALE_ITEM, "$COLUMN_SALE_ITEM_SALE_ID = ?", arrayOf(saleId.toString()))
        writableDatabase.delete(TABLE_SALE, "$COLUMN_SALE_ID = ?", arrayOf(saleId.toString()))
        // Also delete transaction
        writableDatabase.delete(TABLE_ACCOUNT_TX, "$COLUMN_TX_SOURCE = 'Sales' AND $COLUMN_TX_REF_ID = ?", arrayOf(saleId.toString()))
    }

    // --- ACCOUNTS MODULE CORE LOGIC ---

    fun addAccountTransaction(accountId: Int, source: String, refId: Int, type: String, amount: Double, remarks: String?, date: String, farmId: Int? = null, seasonId: Int? = null): Long {
        val targetFarmId = farmId ?: getCurrentFarmId()
        val targetSeasonId = seasonId ?: getCurrentSeasonId()
        
        // If accountId is 0, try to find the "Cash" account for this farm
        var targetAccountId = accountId
        if (targetAccountId == 0) {
            val cursor = readableDatabase.rawQuery("SELECT $COLUMN_SOURCE_ID FROM $TABLE_FUND_SOURCE WHERE $COLUMN_SOURCE_NAME = 'Cash' AND $COLUMN_FARM_ID = ?", arrayOf(targetFarmId.toString()))
            if (cursor.moveToFirst()) {
                targetAccountId = cursor.getInt(0)
            }
            cursor.close()
        }

        val values = ContentValues().apply {
            put(COLUMN_TX_ACCOUNT_ID, targetAccountId)
            put(COLUMN_TX_SOURCE, source)
            put(COLUMN_TX_REF_ID, refId)
            put(COLUMN_TX_TYPE, type)
            put(COLUMN_TX_AMOUNT, amount)
            put(COLUMN_TX_REMARKS, remarks)
            put(COLUMN_TX_DATE, date)
            put(COLUMN_FARM_ID, targetFarmId)
            put(COLUMN_SEASON_LINK_ID, targetSeasonId)
        }
        return writableDatabase.insert(TABLE_ACCOUNT_TX, null, values)
    }

    fun updateAccountTransaction(source: String, refId: Int, accountId: Int, amount: Double, remarks: String?, date: String, type: String? = null) {
        var targetAccountId = accountId
        if (targetAccountId == 0) {
            val farmId = getCurrentFarmId()
            val cursor = readableDatabase.rawQuery("SELECT $COLUMN_SOURCE_ID FROM $TABLE_FUND_SOURCE WHERE $COLUMN_SOURCE_NAME = 'Cash' AND $COLUMN_FARM_ID = ?", arrayOf(farmId.toString()))
            if (cursor.moveToFirst()) {
                targetAccountId = cursor.getInt(0)
            }
            cursor.close()
        }

        val values = ContentValues().apply {
            put(COLUMN_TX_ACCOUNT_ID, targetAccountId)
            put(COLUMN_TX_AMOUNT, amount)
            put(COLUMN_TX_REMARKS, remarks)
            put(COLUMN_TX_DATE, date)
            if (type != null) put(COLUMN_TX_TYPE, type)
        }
        writableDatabase.update(TABLE_ACCOUNT_TX, values, "$COLUMN_TX_SOURCE = ? AND $COLUMN_TX_REF_ID = ?", arrayOf(source, refId.toString()))
    }

    fun getAllAccountsWithBalance(): List<Account> {
        val list = mutableListOf<Account>()
        val farmId = getCurrentFarmId()
        val sources = getAllFundSources() // Reusing existing source table
        
        sources.forEach { src ->
            val balance = getAccountBalance(src.id)
            list.add(Account(src.id, src.name, src.description, balance, farmId))
        }
        return list
    }

    fun getAccountBalance(accountId: Int, seasonId: Int? = null): Double {
        val db = readableDatabase
        val farmId = getCurrentFarmId()
        val targetSeasonId = seasonId ?: getCurrentSeasonId()
        val query = "SELECT SUM(CASE WHEN $COLUMN_TX_TYPE = 'Credit' THEN $COLUMN_TX_AMOUNT ELSE -$COLUMN_TX_AMOUNT END) FROM $TABLE_ACCOUNT_TX WHERE $COLUMN_TX_ACCOUNT_ID = ? AND $COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ?"
        val cursor = db.rawQuery(query, arrayOf(accountId.toString(), farmId.toString(), targetSeasonId.toString()))
        var balance = 0.0
        if (cursor.moveToFirst()) balance = cursor.getDouble(0)
        cursor.close()
        return balance
    }

    fun getAccountTransactions(accountId: Int): List<AccountTransaction> {
        val list = mutableListOf<AccountTransaction>()
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        val query = "SELECT * FROM $TABLE_ACCOUNT_TX WHERE $COLUMN_TX_ACCOUNT_ID = ? AND $COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ? ORDER BY $COLUMN_TX_DATE DESC, $COLUMN_TX_ID DESC"
        val cursor = readableDatabase.rawQuery(query, arrayOf(accountId.toString(), farmId.toString(), seasonId.toString()))
        while (cursor.moveToNext()) {
            list.add(AccountTransaction(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TX_ID)),
                accountId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TX_ACCOUNT_ID)),
                moduleSource = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TX_SOURCE)),
                referenceId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TX_REF_ID)),
                transactionType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TX_TYPE)),
                amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TX_AMOUNT)),
                remarks = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TX_REMARKS)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TX_DATE)),
                farmId = farmId
            ))
        }
        cursor.close()
        return list
    }

    fun getPLSummary(fromDate: String, toDate: String): PLSummary {
        val db = readableDatabase
        val farmId = getCurrentFarmId()
        val seasonId = getCurrentSeasonId()
        
        fun getSum(sources: List<String>): Double {
            val placeholders = sources.joinToString(",") { "?" }
            val query = "SELECT SUM($COLUMN_TX_AMOUNT) FROM $TABLE_ACCOUNT_TX WHERE $COLUMN_TX_SOURCE IN ($placeholders) AND $COLUMN_TX_DATE BETWEEN ? AND ? AND $COLUMN_FARM_ID = ? AND $COLUMN_SEASON_LINK_ID = ?"
            val args = sources.toMutableList().apply { add(fromDate); add(toDate); add(farmId.toString()); add(seasonId.toString()) }.toTypedArray()
            val cursor = db.rawQuery(query, args)
            var sum = 0.0
            if (cursor.moveToFirst()) sum = cursor.getDouble(0)
            cursor.close()
            return sum
        }

        val sales = getSum(listOf("Sales"))
        val expenses = getSum(listOf("Expense", "Advance"))
        val funds = getSum(listOf("Funds", "Settlement"))

        return PLSummary(sales, expenses, funds, sales - expenses)
    }

    fun syncExistingToTransactions() {
        val db = writableDatabase
        // Check if transactions are already populated. If we want to re-sync, we could remove this check or clear the table.
        // Given the user report, let's clear and re-populate once to ensure integrity.
        
        db.beginTransaction()
        try {
            db.delete(TABLE_ACCOUNT_TX, null, null)

            // 1. Settlements (formerly Funds)
            val fundCursor = db.rawQuery("SELECT * FROM $TABLE_FUND_ENTRY", null)
            while (fundCursor.moveToNext()) {
                val sourceId = fundCursor.getInt(fundCursor.getColumnIndexOrThrow(COLUMN_FUND_SOURCE_ID))
                val id = fundCursor.getInt(fundCursor.getColumnIndexOrThrow(COLUMN_FUND_ID))
                val amount = fundCursor.getDouble(fundCursor.getColumnIndexOrThrow(COLUMN_FUND_AMOUNT))
                val date = fundCursor.getString(fundCursor.getColumnIndexOrThrow(COLUMN_FUND_DATE))
                val desc = fundCursor.getString(fundCursor.getColumnIndexOrThrow(COLUMN_FUND_DESC))
                val fId = fundCursor.getInt(fundCursor.getColumnIndexOrThrow(COLUMN_FARM_ID))
                
                val type = if (amount >= 0) "Credit" else "Debit"
                addAccountTransaction(sourceId, "Settlement", id, type, Math.abs(amount), desc, date, fId)
            }
            fundCursor.close()

            // 2. Expenses (Vouchers)
            val voucherCursor = db.rawQuery("SELECT * FROM $TABLE_EXP_VOUCHER", null)
            while (voucherCursor.moveToNext()) {
                val sourceId = voucherCursor.getInt(voucherCursor.getColumnIndexOrThrow(COLUMN_VOUCHER_SOURCE_ID))
                val id = voucherCursor.getInt(voucherCursor.getColumnIndexOrThrow(COLUMN_VOUCHER_ID))
                val total = voucherCursor.getDouble(voucherCursor.getColumnIndexOrThrow(COLUMN_VOUCHER_TOTAL))
                val date = voucherCursor.getString(voucherCursor.getColumnIndexOrThrow(COLUMN_VOUCHER_DATE))
                val num = voucherCursor.getString(voucherCursor.getColumnIndexOrThrow(COLUMN_VOUCHER_NUMBER))
                val fId = voucherCursor.getInt(voucherCursor.getColumnIndexOrThrow(COLUMN_FARM_ID))
                
                val type = if (isAccountPayable(sourceId)) "Credit" else "Debit"
                addAccountTransaction(sourceId, "Expense", id, type, total, num, date, fId)
            }
            voucherCursor.close()

            // 3. Sales
            val saleCursor = db.rawQuery("SELECT * FROM $TABLE_SALE", null)
            while (saleCursor.moveToNext()) {
                val sourceId = saleCursor.getInt(saleCursor.getColumnIndexOrThrow(COLUMN_SALE_SOURCE_ID))
                val id = saleCursor.getInt(saleCursor.getColumnIndexOrThrow(COLUMN_SALE_ID))
                val total = saleCursor.getDouble(saleCursor.getColumnIndexOrThrow(COLUMN_SALE_TOTAL))
                val date = saleCursor.getString(saleCursor.getColumnIndexOrThrow(COLUMN_SALE_DATE))
                val buyer = saleCursor.getString(saleCursor.getColumnIndexOrThrow(COLUMN_SALE_BUYER))
                val fId = saleCursor.getInt(saleCursor.getColumnIndexOrThrow(COLUMN_FARM_ID))
                
                val type = if (isAccountPayable(sourceId)) "Debit" else "Credit"
                addAccountTransaction(sourceId, "Sales", id, type, total, buyer, date, fId)
            }
            saleCursor.close()

            // 4. Advances
            val advCursor = db.rawQuery("SELECT a.*, l.name as labour_name FROM $TABLE_ADVANCE a LEFT JOIN $TABLE_LABOUR l ON a.$COLUMN_ADVANCE_LABOUR_ID = l.$COLUMN_LABOUR_ID", null)
            while (advCursor.moveToNext()) {
                val sourceId = advCursor.getInt(advCursor.getColumnIndexOrThrow(COLUMN_ADVANCE_SOURCE_ID))
                val id = advCursor.getInt(advCursor.getColumnIndexOrThrow(COLUMN_ADVANCE_ID))
                val amount = advCursor.getDouble(advCursor.getColumnIndexOrThrow(COLUMN_ADVANCE_AMOUNT))
                val date = advCursor.getString(advCursor.getColumnIndexOrThrow(COLUMN_ADVANCE_DATE))
                val labourName = advCursor.getString(advCursor.getColumnIndexOrThrow("labour_name"))
                val fId = advCursor.getInt(advCursor.getColumnIndexOrThrow(COLUMN_FARM_ID))
                
                val type = if (isAccountPayable(sourceId)) "Credit" else "Debit"
                addAccountTransaction(sourceId, "Advance", id, type, amount, labourName, date, fId)
            }
            advCursor.close()

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getLabourStats(labourId: Int): LabourStats? {
        val farmId = getCurrentFarmId()
        val labour = getAllLabours().find { it.id == labourId } ?: return null
        val cursor = readableDatabase.rawQuery("SELECT $COLUMN_ATTENDANCE_STATUS, COUNT(*) FROM $TABLE_ATTENDANCE WHERE $COLUMN_ATTENDANCE_LABOUR_ID = ? AND $COLUMN_FARM_ID = ? GROUP BY $COLUMN_ATTENDANCE_STATUS", arrayOf(labourId.toString(), farmId.toString()))
        var p = 0; var h = 0; var a = 0
        while (cursor.moveToNext()) when (cursor.getString(0)) { "P" -> p = cursor.getInt(1); "H" -> h = cursor.getInt(1); "A" -> a = cursor.getInt(1) }
        cursor.close()
        val earnings = when (labour.labourType) {
            "DAILY_WAGE" -> (p.toDouble() + (h * 0.5)) * labour.wage
            "MONTHLY" -> {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val joinDate = labour.joinDate?.let { sdf.parse(it) } ?: Date()
                    val endDate = labour.endDate?.let { sdf.parse(it) } ?: Date()
                    
                    val cal = Calendar.getInstance()
                    val startCal = Calendar.getInstance().apply { time = joinDate }
                    val endCal = Calendar.getInstance().apply { time = endDate }
                    
                    var totalMonths = 0.0
                    
                    // Simple calculation for months between dates
                    val diffMillis = endCal.timeInMillis - startCal.timeInMillis
                    val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toDouble() + 1
                    totalMonths = diffDays / 30.44 // Average month length
                    
                    labour.wage * totalMonths
                } catch (e: Exception) {
                    labour.wage
                }
            }
            else -> 0.0
        }
        val adv = getTotalAdvanceForLabour(labourId)
        return LabourStats(labour, p, h, a, adv, earnings, earnings - adv)
    }

    companion object {
        private const val DATABASE_NAME = "labour_attendance.db"
        private const val DATABASE_VERSION = 20
        
        private const val TABLE_FARM = "farms"
        private const val COLUMN_FARM_ID_PK = "id"
        private const val COLUMN_FARM_NAME = "farm_name"
        private const val COLUMN_FARM_LOCATION = "location"
        private const val COLUMN_FARM_OWNER = "owner"
        private const val COLUMN_FARM_REMARKS = "remarks"
        private const val COLUMN_FARM_STATUS = "active_status"
        private const val COLUMN_FARM_CREATED_BY = "created_by"
        private const val COLUMN_FARM_TIMESTAMP = "timestamp"

        private const val COLUMN_FARM_ID = "farm_id"

        private const val TABLE_GROUP = "labour_group"
        private const val COLUMN_GROUP_ID = "id"
        private const val COLUMN_GROUP_NAME = "name"
        private const val TABLE_LABOUR = "labour"
        private const val COLUMN_LABOUR_ID = "id"
        private const val COLUMN_LABOUR_NAME = "name"
        private const val COLUMN_LABOUR_GROUP_ID = "group_id"
        private const val COLUMN_LABOUR_WAGE = "daily_wage"
        private const val COLUMN_DISPLAY_ORDER = "display_order"
        private const val COLUMN_LABOUR_JOIN_DATE = "join_date"
        private const val COLUMN_LABOUR_END_DATE = "end_date"
        private const val COLUMN_LABOUR_STATUS = "status"
        private const val COLUMN_LABOUR_TYPE = "labour_type"
        private const val COLUMN_LABOUR_REMARKS = "remarks"
        private const val TABLE_ATTENDANCE = "attendance"
        private const val COLUMN_ATTENDANCE_ID = "id"
        private const val COLUMN_ATTENDANCE_LABOUR_ID = "labour_id"
        private const val COLUMN_ATTENDANCE_DATE = "date"
        private const val COLUMN_ATTENDANCE_STATUS = "status"
        private const val TABLE_ADVANCE = "advance"
        private const val COLUMN_ADVANCE_ID = "id"
        private const val COLUMN_ADVANCE_LABOUR_ID = "labour_id"
        private const val COLUMN_ADVANCE_AMOUNT = "amount"
        private const val COLUMN_ADVANCE_DATE = "date"
        private const val COLUMN_ADVANCE_DESCRIPTION = "description"
        private const val COLUMN_ADVANCE_SOURCE_ID = "source_id"
        private const val TABLE_VEHICLE = "vehicle"
        private const val COLUMN_VEHICLE_ID = "id"
        private const val COLUMN_VEHICLE_NUMBER = "number"
        private const val COLUMN_VEHICLE_DRIVER_NAME = "driver_name"
        private const val COLUMN_VEHICLE_DRIVER_PHONE = "driver_phone"
        private const val TABLE_DATE_TYPE = "date_type"
        private const val COLUMN_DATE_TYPE_ID = "id"
        private const val COLUMN_DATE_TYPE_NAME = "name"
        private const val TABLE_DISPATCH = "dispatch"
        private const val COLUMN_DISPATCH_ID = "id"
        private const val COLUMN_DISPATCH_VEHICLE_ID = "vehicle_id"
        private const val COLUMN_DISPATCH_DATE = "date"
        private const val TABLE_DISPATCH_ITEM = "dispatch_item"
        private const val COLUMN_ITEM_ID = "id"
        private const val COLUMN_ITEM_DISPATCH_ID = "dispatch_id"
        private const val COLUMN_ITEM_DATE_TYPE_ID = "date_type_id"
        private const val COLUMN_ITEM_COUNT = "carton_count"

        // Expenditure constants
        private const val TABLE_EXP_VOUCHER = "exp_voucher"
        private const val COLUMN_VOUCHER_ID = "id"
        private const val COLUMN_VOUCHER_NUMBER = "voucher_number"
        private const val COLUMN_VOUCHER_DATE = "date"
        private const val COLUMN_VOUCHER_TOTAL = "total_amount"
        private const val COLUMN_VOUCHER_BY = "recorded_by"
        private const val COLUMN_VOUCHER_SOURCE_ID = "source_id" // Linked account ID

        private const val TABLE_EXP_ITEM = "exp_item"
        private const val COLUMN_EXP_ITEM_ID = "id"
        private const val COLUMN_EXP_VOUCHER_ID = "voucher_id"
        private const val COLUMN_EXP_CATEGORY = "category"
        private const val COLUMN_EXP_AMOUNT = "amount"
        private const val COLUMN_EXP_DESC = "description"

        private const val TABLE_EXP_CAT = "exp_category"
        private const val COLUMN_CAT_ID = "id"
        private const val COLUMN_CAT_NAME = "name"

        // Funds constants
        private const val TABLE_FUND_SOURCE = "fund_source"
        private const val COLUMN_SOURCE_ID = "id"
        private const val COLUMN_SOURCE_NAME = "name"
        private const val COLUMN_SOURCE_DESC = "description"

        private const val TABLE_FUND_ENTRY = "fund_entry"
        private const val COLUMN_FUND_ID = "id"
        private const val COLUMN_FUND_SOURCE_ID = "source_id"
        private const val COLUMN_FUND_AMOUNT = "amount"
        private const val COLUMN_FUND_DATE = "date"
        private const val COLUMN_FUND_DESC = "description"

        // Sales constants
        private const val TABLE_SALE = "sale"
        private const val COLUMN_SALE_ID = "id"
        private const val COLUMN_SALE_DATE = "date"
        private const val COLUMN_SALE_BUYER = "buyer_name"
        private const val COLUMN_SALE_TOTAL = "total_amount"
        private const val COLUMN_SALE_SOURCE_ID = "source_id" // Linking to fund account

        private const val TABLE_SALE_ITEM = "sale_item"
        private const val COLUMN_SALE_ITEM_ID = "id"
        private const val COLUMN_SALE_ITEM_SALE_ID = "sale_id"
        private const val COLUMN_SALE_ITEM_DISPATCH_ID = "dispatch_id"
        private const val COLUMN_SALE_ITEM_DATE_TYPE_ID = "date_type_id"
        private const val COLUMN_SALE_ITEM_QTY = "quantity"
        private const val COLUMN_SALE_ITEM_PRICE = "unit_price"

        // Account Transaction constants
        private const val TABLE_ACCOUNT_TX = "account_transaction"
        private const val COLUMN_TX_ID = "id"
        private const val COLUMN_TX_ACCOUNT_ID = "account_id"
        private const val COLUMN_TX_SOURCE = "module_source"
        private const val COLUMN_TX_REF_ID = "reference_id"
        private const val COLUMN_TX_TYPE = "transaction_type"
        private const val COLUMN_TX_AMOUNT = "amount"
        private const val COLUMN_TX_REMARKS = "remarks"
        private const val COLUMN_TX_DATE = "date"

        const val TABLE_SEASON = "seasons"
        const val COLUMN_SEASON_ID = "id"
        const val COLUMN_SEASON_NAME = "name"
        const val COLUMN_SEASON_YEAR = "year"
        const val COLUMN_SEASON_IS_ACTIVE = "is_active"
        const val COLUMN_SEASON_IS_CLOSED = "is_closed"
        const val COLUMN_SEASON_START_DATE = "start_date"
        const val COLUMN_SEASON_END_DATE = "end_date"

        const val COLUMN_SEASON_LINK_ID = "season_id"
    }
}

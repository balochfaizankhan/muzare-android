package com.example.labourattendance

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SyncManager private constructor(private val context: Context) {
    private val dbHelper = DatabaseHelper(context)
    private val firestoreHelper = FirestoreHelper()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    companion object {
        @Volatile
        private var INSTANCE: SyncManager? = null
        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        object Synced : SyncStatus()
        object Offline : SyncStatus()
        data class Failed(val error: String) : SyncStatus()
        data class Pending(val count: Int) : SyncStatus()
    }

    fun startAutoSync() {
        scope.launch {
            while (isActive) {
                if (isNetworkAvailable()) {
                    performFullSync()
                } else {
                    _syncStatus.value = SyncStatus.Offline
                }
                delay(60000) // Retry every minute
            }
        }
    }

    fun triggerSync() {
        scope.launch {
            if (isNetworkAvailable()) {
                performFullSync()
            } else {
                _syncStatus.value = SyncStatus.Offline
            }
        }
    }

    private suspend fun performFullSync() {
        _syncStatus.value = SyncStatus.Syncing // This means Pulling/Restoring
        try {
            // 1. Pull from Cloud
            pullFromCloud()
            
            // 2. Push Pending Changes
            pushPendingChanges()

            // Check if there are still items (e.g. failed ones)
            val remaining = dbHelper.getPendingSyncItems().size
            if (remaining > 0) {
                _syncStatus.value = SyncStatus.Pending(remaining)
            } else {
                _syncStatus.value = SyncStatus.Synced
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync failed: ${e.message}")
            _syncStatus.value = SyncStatus.Failed(e.message ?: "Unknown error")
        }
    }

    private suspend fun pullFromCloud() {
        val lastSyncAt = getLastSyncTimestamp()
        val currentFarmId = dbHelper.getCurrentFarmId()
        val currentSeasonId = dbHelper.getCurrentSeasonId()
        val collections = listOf(
            "farms", "seasons", "groups", "labours", "attendance", 
            "advances", "exp_categories", "expenditure", "fund_sources", 
            "fund_entries", "sales", "transactions", "vehicles", "dateTypes",
        )

        for (collection in collections) {
            val updatedRecords = firestoreHelper.fetchUpdatedRecords(collection, lastSyncAt, currentFarmId, currentSeasonId)
            for (record in updatedRecords) {
                mergeIntoLocal(collection, record)
            }
        }
        setLastSyncTimestamp(System.currentTimeMillis())
    }

    private suspend fun pushPendingChanges() {
        val pendingItems: List<DatabaseHelper.SyncQueueItem> = dbHelper.getPendingSyncItems()
        if (pendingItems.isEmpty()) return

        _syncStatus.value = SyncStatus.Pending(pendingItems.size)
        
        for (item in pendingItems) {
            val success = when (item.operation) {
                "CREATE", "UPDATE" -> {
                    val collection = getCollectionName(item.module)
                    val data = dbHelper.getRecordData(item.module, item.recordId)
                    if (data != null) {
                        firestoreHelper.syncRecord(collection, item.recordId, data)
                    } else true 
                }
                "DELETE" -> {
                    val collection = getCollectionName(item.module)
                    firestoreHelper.deleteRecord(collection, item.recordId)
                }
                else -> true
            }

            if (success) {
                dbHelper.markAsSynced(item.id)
            } else {
                dbHelper.markAsFailed(item.id)
            }
        }
    }

    private fun mergeIntoLocal(collection: String, cloudData: Map<String, Any>) {
        val module = getModuleNameFromCollection(collection)
        val recordId = cloudData["id"].toString()
        val cloudUpdatedAt = (cloudData["updatedAt"] as? Long) ?: 0L
        
        val localMetadata = dbHelper.getRecordMetadata(module, recordId)
        val localUpdatedAt = (localMetadata?.get("updated_at") as? Long) ?: 0L
        val isPending = dbHelper.isRecordPendingSync(module, recordId)
        
        if (!isPending && cloudUpdatedAt > localUpdatedAt) {
            dbHelper.upsertFromCloud(module, cloudData)
        }
    }

    private fun getCollectionName(module: String): String {
        return when (module) {
            "farms" -> "farms"
            "seasons" -> "seasons"
            "labour_group" -> "groups"
            "labour" -> "labours"
            "attendance" -> "attendance"
            "advance" -> "advances"
            "exp_voucher" -> "expenditure"
            "exp_category" -> "exp_categories"
            "fund_source" -> "fund_sources"
            "fund_entry" -> "fund_entries"
            "sale" -> "sales"
            "account_transaction" -> "transactions"
            "vehicle" -> "vehicles"
            "date_type" -> "dateTypes"
            else -> module
        }
    }

    private fun getModuleNameFromCollection(collection: String): String {
        return when (collection) {
            "farms" -> "farms"
            "seasons" -> "seasons"
            "groups" -> "labour_group"
            "labours" -> "labour"
            "attendance" -> "attendance"
            "advances" -> "advance"
            "expenditure" -> "exp_voucher"
            "exp_categories" -> "exp_category"
            "fund_sources" -> "fund_source"
            "fund_entries" -> "fund_entry"
            "sales" -> "sale"
            "transactions" -> "account_transaction"
            "vehicles" -> "vehicle"
            "dateTypes" -> "date_type"
            else -> collection
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    private fun getLastSyncTimestamp(): Long {
        return context.getSharedPreferences("SyncPrefs", Context.MODE_PRIVATE).getLong("last_sync_at", 0)
    }

    private fun setLastSyncTimestamp(ts: Long) {
        context.getSharedPreferences("SyncPrefs", Context.MODE_PRIVATE).edit().apply {
            putLong("last_sync_at", ts)
            apply()
        }
    }
}

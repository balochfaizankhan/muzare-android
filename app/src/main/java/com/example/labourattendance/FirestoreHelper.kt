package com.example.labourattendance

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreHelper {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun syncRecord(collection: String, documentId: String, data: Map<String, Any?>): Boolean {
        return try {
            db.collection(collection).document(documentId)
                .set(data, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreHelper", "Error syncing record: ${e.message}")
            false
        }
    }

    suspend fun deleteRecord(collection: String, documentId: String): Boolean {
        return try {
            db.collection(collection).document(documentId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreHelper", "Error deleting record: ${e.message}")
            false
        }
    }

    suspend fun fetchUpdatedRecords(collection: String, since: Long, farmId: Int = -1, seasonId: Int = -1): List<Map<String, Any>> {
        return try {
            var query: Query = db.collection(collection)
                .whereGreaterThan("updatedAt", since)
            
            if (farmId != -1 && collection != "farms" && collection != "seasons") {
                query = query.whereEqualTo("farmId", farmId)
            }

            if (seasonId != -1 && isSeasonalCollection(collection)) {
                query = query.whereEqualTo("seasonId", seasonId)
            }

            val result = query.get().await()
            result.map { it.data }
        } catch (e: Exception) {
            Log.e("FirestoreHelper", "Error fetching updated records: ${e.message}")
            emptyList()
        }
    }

    private fun isSeasonalCollection(collection: String): Boolean {
        return listOf("attendance", "advances", "expenditure", "fund_entries", "sales", "transactions").contains(collection)
    }

    // Keep legacy methods for backward compatibility during migration if needed
    fun syncLabour(labour: DatabaseHelper.Labour) {
        val data = hashMapOf(
            "id" to labour.id,
            "name" to labour.name,
            "groupId" to labour.groupId,
            "wage" to labour.wage,
            "displayOrder" to labour.displayOrder,
            "joinDate" to (labour.joinDate ?: ""),
            "endDate" to (labour.endDate ?: ""),
            "status" to labour.status,
            "farmId" to labour.farmId,
            "labourType" to labour.labourType,
            "remarks" to (labour.remarks ?: ""),
            "createdAt" to labour.createdAt,
            "updatedAt" to labour.updatedAt,
            "deletedAt" to (labour.deletedAt ?: 0)
        )
        db.collection("labours").document(labour.id.toString()).set(data, SetOptions.merge())
    }

    fun syncAttendance(entry: DatabaseHelper.AttendanceEntry) {
        val docId = "${entry.labourId}_${entry.date}"
        val data = hashMapOf(
            "labourId" to entry.labourId,
            "date" to entry.date,
            "status" to entry.status,
            "farmId" to entry.farmId,
            "createdAt" to entry.createdAt,
            "updatedAt" to entry.updatedAt,
            "deletedAt" to (entry.deletedAt ?: 0)
        )
        db.collection("attendance").document(docId).set(data, SetOptions.merge())
    }

    fun syncAdvance(record: DatabaseHelper.AdvanceRecord) {
        val data = hashMapOf(
            "id" to record.id,
            "labourId" to record.labourId,
            "labourName" to (record.labourName ?: ""),
            "amount" to record.amount,
            "date" to record.date,
            "description" to (record.description ?: ""),
            "sourceId" to record.sourceId,
            "farmId" to record.farmId,
            "createdAt" to record.createdAt,
            "updatedAt" to record.updatedAt,
            "deletedAt" to (record.deletedAt ?: 0)
        )
        db.collection("advances").document(record.id.toString()).set(data, SetOptions.merge())
    }

    fun syncGroup(group: DatabaseHelper.Group) {
        val data = hashMapOf(
            "id" to group.id,
            "name" to group.name,
            "farmId" to group.farmId,
            "createdAt" to group.createdAt,
            "updatedAt" to group.updatedAt,
            "deletedAt" to (group.deletedAt ?: 0)
        )
        db.collection("groups").document(group.id.toString()).set(data, SetOptions.merge())
    }

    fun syncVoucher(voucher: DatabaseHelper.Voucher) {
        val items = voucher.items.map {
            hashMapOf(
                "category" to it.category,
                "amount" to it.amount,
                "description" to (it.description ?: "")
            )
        }
        val data = hashMapOf(
            "id" to voucher.id,
            "voucherNumber" to voucher.voucherNumber,
            "date" to voucher.date,
            "totalAmount" to voucher.totalAmount,
            "recordedBy" to voucher.recordedBy,
            "sourceId" to voucher.sourceId,
            "sourceName" to (voucher.sourceName ?: ""),
            "items" to items,
            "farmId" to voucher.farmId,
            "createdAt" to voucher.createdAt,
            "updatedAt" to voucher.updatedAt,
            "deletedAt" to (voucher.deletedAt ?: 0)
        )
        db.collection("expenditure").document(voucher.id.toString()).set(data, SetOptions.merge())
    }

    fun syncFundSource(source: DatabaseHelper.FundSource) {
        val data = hashMapOf(
            "id" to source.id,
            "name" to source.name,
            "description" to (source.description ?: ""),
            "farmId" to source.farmId,
            "createdAt" to source.createdAt,
            "updatedAt" to source.updatedAt,
            "deletedAt" to (source.deletedAt ?: 0)
        )
        db.collection("fund_sources").document(source.id.toString()).set(data, SetOptions.merge())
    }

    fun syncFundEntry(entry: DatabaseHelper.FundEntry) {
        val data = hashMapOf(
            "id" to entry.id,
            "sourceId" to entry.sourceId,
            "sourceName" to (entry.sourceName ?: ""),
            "amount" to entry.amount,
            "date" to entry.date,
            "description" to (entry.description ?: ""),
            "farmId" to entry.farmId,
            "createdAt" to entry.createdAt,
            "updatedAt" to entry.updatedAt,
            "deletedAt" to (entry.deletedAt ?: 0)
        )
        db.collection("fund_entries").document(entry.id.toString()).set(data, SetOptions.merge())
    }

    fun syncVehicle(vehicle: DatabaseHelper.Vehicle) {
        val data = hashMapOf(
            "id" to vehicle.id,
            "number" to vehicle.number,
            "driverName" to vehicle.driverName,
            "driverPhone" to vehicle.driverPhone,
            "farmId" to vehicle.farmId,
            "createdAt" to vehicle.createdAt,
            "updatedAt" to vehicle.updatedAt,
            "deletedAt" to (vehicle.deletedAt ?: 0)
        )
        db.collection("vehicles").document(vehicle.id.toString()).set(data, SetOptions.merge())
    }

    fun syncDateType(type: DatabaseHelper.DateType) {
        val data = hashMapOf(
            "id" to type.id,
            "name" to type.name,
            "farmId" to type.farmId,
            "createdAt" to type.createdAt,
            "updatedAt" to type.updatedAt,
            "deletedAt" to (type.deletedAt ?: 0)
        )
        db.collection("dateTypes").document(type.id.toString()).set(data, SetOptions.merge())
    }

    fun syncDispatch(dispatch: DatabaseHelper.DispatchRecord) {
        val items = dispatch.items.map {
            hashMapOf(
                "dateTypeId" to it.dateTypeId,
                "dateTypeName" to it.dateTypeName,
                "cartonCount" to it.cartonCount
            )
        }
        val data = hashMapOf(
            "id" to dispatch.id,
            "vehicleId" to dispatch.vehicleId,
            "vehicleNumber" to dispatch.vehicleNumber,
            "driverName" to dispatch.driverName,
            "date" to dispatch.date,
            "items" to items,
            "farmId" to dispatch.farmId,
            "createdAt" to dispatch.createdAt,
            "updatedAt" to dispatch.updatedAt,
            "deletedAt" to (dispatch.deletedAt ?: 0)
        )
        db.collection("dispatches").document(dispatch.id.toString()).set(data, SetOptions.merge())
    }

    fun syncFarm(farm: DatabaseHelper.Farm) {
        val data = hashMapOf(
            "id" to farm.id,
            "name" to farm.name,
            "location" to (farm.location ?: ""),
            "owner" to (farm.owner ?: ""),
            "remarks" to (farm.remarks ?: ""),
            "activeStatus" to farm.activeStatus,
            "createdBy" to (farm.createdBy ?: ""),
            "timestamp" to farm.timestamp,
            "createdAt" to farm.createdAt,
            "updatedAt" to farm.updatedAt,
            "deletedAt" to (farm.deletedAt ?: 0)
        )
        db.collection("farms").document(farm.id.toString()).set(data, SetOptions.merge())
    }

    fun syncSeason(season: DatabaseHelper.Season) {
        val data = hashMapOf(
            "id" to season.id,
            "name" to season.name,
            "year" to season.year,
            "isActive" to season.isActive,
            "isClosed" to season.isClosed,
            "startDate" to season.startDate,
            "endDate" to (season.endDate ?: ""),
            "createdAt" to season.createdAt,
            "updatedAt" to season.updatedAt,
            "deletedAt" to (season.deletedAt ?: 0)
        )
        db.collection("seasons").document(season.id.toString()).set(data, SetOptions.merge())
    }

    fun syncSale(sale: DatabaseHelper.Sale) {
        val items = sale.items.map {
            hashMapOf(
                "dispatchId" to it.dispatchId,
                "dateTypeId" to it.dateTypeId,
                "quantity" to it.quantity,
                "unitPrice" to it.unitPrice
            )
        }
        val data = hashMapOf(
            "id" to sale.id,
            "date" to sale.date,
            "buyerName" to sale.buyerName,
            "totalAmount" to sale.totalAmount,
            "sourceId" to sale.sourceId,
            "items" to items,
            "farmId" to sale.farmId,
            "createdAt" to sale.createdAt,
            "updatedAt" to sale.updatedAt,
            "deletedAt" to (sale.deletedAt ?: 0)
        )
        db.collection("sales").document(sale.id.toString()).set(data, SetOptions.merge())
    }

    fun syncAccountTransaction(tx: DatabaseHelper.AccountTransaction) {
        val data = hashMapOf(
            "id" to tx.id,
            "accountId" to tx.accountId,
            "moduleSource" to tx.moduleSource,
            "referenceId" to tx.referenceId,
            "transactionType" to tx.transactionType,
            "amount" to tx.amount,
            "remarks" to (tx.remarks ?: ""),
            "date" to tx.date,
            "farmId" to tx.farmId,
            "createdAt" to tx.createdAt,
            "updatedAt" to tx.updatedAt,
            "deletedAt" to (tx.deletedAt ?: 0)
        )
        db.collection("transactions").document(tx.id.toString()).set(data, SetOptions.merge())
    }

    // Keep all fetchAll methods as they are used for manual restore
    fun fetchAllFarms(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("farms").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllGroups(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("groups").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllLabours(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("labours").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllAdvances(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("advances").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllAttendance(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("attendance").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllExpenditure(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("expenditure").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllFundSources(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("fund_sources").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllFundEntries(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("fund_entries").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllVehicles(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("vehicles").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllDateTypes(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("dateTypes").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllSeasons(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("seasons").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
    fun fetchAllDispatches(onSuccess: (List<Map<String, Any>>) -> Unit) { db.collection("dispatches").get().addOnSuccessListener { result -> onSuccess(result.map { it.data }) } }
}

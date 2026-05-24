package com.example.labourattendance

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class FirestoreHelper {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
            "remarks" to (labour.remarks ?: "")
        )
        db.collection("labours").document(labour.id.toString()).set(data, SetOptions.merge())
    }

    fun deleteLabour(labourId: Int) {
        db.collection("labours").document(labourId.toString()).delete()
    }

    fun syncAttendance(entry: DatabaseHelper.AttendanceEntry) {
        val docId = "${entry.labourId}_${entry.date}"
        val data = hashMapOf(
            "labourId" to entry.labourId,
            "date" to entry.date,
            "status" to entry.status,
            "farmId" to entry.farmId
        )
        db.collection("attendance").document(docId).set(data, SetOptions.merge())
    }

    fun deleteAttendance(labourId: Int, date: String) {
        val docId = "${labourId}_${date}"
        db.collection("attendance").document(docId).delete()
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
            "farmId" to record.farmId
        )
        db.collection("advances").document(record.id.toString()).set(data, SetOptions.merge())
    }

    fun deleteAdvance(advanceId: Int) {
        db.collection("advances").document(advanceId.toString()).delete()
    }

    fun syncGroup(group: DatabaseHelper.Group) {
        val data = hashMapOf(
            "id" to group.id,
            "name" to group.name,
            "farmId" to group.farmId
        )
        db.collection("groups").document(group.id.toString()).set(data, SetOptions.merge())
    }

    fun deleteGroup(groupId: Int) {
        db.collection("groups").document(groupId.toString()).delete()
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
            "farmId" to voucher.farmId
        )
        db.collection("expenditure").document(voucher.id.toString()).set(data, SetOptions.merge())
    }

    fun deleteVoucher(voucherId: Int) {
        db.collection("expenditure").document(voucherId.toString()).delete()
    }

    fun syncFundSource(source: DatabaseHelper.FundSource) {
        val data = hashMapOf(
            "id" to source.id,
            "name" to source.name,
            "description" to (source.description ?: ""),
            "farmId" to source.farmId
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
            "farmId" to entry.farmId
        )
        db.collection("fund_entries").document(entry.id.toString()).set(data, SetOptions.merge())
    }

    fun deleteFundEntry(id: Int) {
        db.collection("fund_entries").document(id.toString()).delete()
    }

    fun deleteVehicle(id: Int) {
        db.collection("vehicles").document(id.toString()).delete()
    }

    fun deleteDateType(id: Int) {
        db.collection("dateTypes").document(id.toString()).delete()
    }

    fun deleteDispatch(id: Int) {
        db.collection("dispatches").document(id.toString()).delete()
    }

    fun deleteExpCategory(id: Int) {
        db.collection("exp_categories").document(id.toString()).delete()
    }

    fun syncExpCategory(cat: DatabaseHelper.ExpCategory) {
        val data = hashMapOf(
            "id" to cat.id,
            "name" to cat.name,
            "farmId" to cat.farmId
        )
        db.collection("exp_categories").document(cat.id.toString()).set(data, SetOptions.merge())
    }

    fun syncVehicle(vehicle: DatabaseHelper.Vehicle) {
        val data = hashMapOf(
            "id" to vehicle.id,
            "number" to vehicle.number,
            "driverName" to vehicle.driverName,
            "driverPhone" to vehicle.driverPhone,
            "farmId" to vehicle.farmId
        )
        db.collection("vehicles").document(vehicle.id.toString()).set(data, SetOptions.merge())
    }

    fun syncDateType(type: DatabaseHelper.DateType) {
        val data = hashMapOf(
            "id" to type.id,
            "name" to type.name,
            "farmId" to type.farmId
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
            "farmId" to dispatch.farmId
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
            "timestamp" to farm.timestamp
        )
        db.collection("farms").document(farm.id.toString()).set(data, SetOptions.merge())
    }

    fun deleteFarm(farmId: Int) {
        db.collection("farms").document(farmId.toString()).delete()
    }

    fun fetchAllFarms(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("farms").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllGroups(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("groups").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllLabours(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("labours").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllAdvances(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("advances").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllAttendance(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("attendance").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllExpenditure(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("expenditure").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllFundSources(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("fund_sources").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllFundEntries(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("fund_entries").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllVehicles(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("vehicles").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllDateTypes(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("dateTypes").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }

    fun fetchAllDispatches(onSuccess: (List<Map<String, Any>>) -> Unit) {
        db.collection("dispatches").get().addOnSuccessListener { result ->
            onSuccess(result.map { it.data })
        }
    }
}

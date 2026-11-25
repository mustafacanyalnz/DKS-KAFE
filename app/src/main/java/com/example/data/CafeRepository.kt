package com.example.data

import android.content.Context
import com.example.R
import com.example.security.PasswordHasher
import com.example.util.Formatters
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CafeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Devam etmek için Google hesabı ile oturum açmalısınız.")
    }

    private fun verifySession(userId: String) {
        val uid = requireUserId()
        check(uid == userId) { "Oturum değişti; işlem güvenlik nedeniyle reddedildi." }
    }

    fun observeStaff(userId: String): Flow<List<StaffAccount>> = flow {
        verifySession(userId)
        val path = "users/$userId/staff"
        emitAll(
            db.collection("users").document(userId).collection("staff")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StaffAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.username }
                }
                .catch { error ->
                    logStreamError(error, path, userId)
                    throw error
                }
        )
    }

    fun observeProducts(userId: String): Flow<List<MenuProduct>> = flow {
        verifySession(userId)
        val path = "users/$userId/products"
        emitAll(
            db.collection("users").document(userId).collection("products")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MenuProduct::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<MenuProduct> { it.sortOrder }.thenBy { it.name })
                }
                .catch { error ->
                    logStreamError(error, path, userId)
                    throw error
                }
        )
    }

    fun observeTables(userId: String): Flow<List<CafeTable>> = flow {
        verifySession(userId)
        val path = "users/$userId/tables"
        emitAll(
            db.collection("users").document(userId).collection("tables")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CafeTable::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.tableNumber }
                }
                .catch { error ->
                    logStreamError(error, path, userId)
                    throw error
                }
        )
    }

    fun observeOrders(userId: String): Flow<List<CompletedOrder>> = flow {
        verifySession(userId)
        val path = "users/$userId/orders"
        emitAll(
            db.collection("users").document(userId).collection("orders")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CompletedOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.closedAtMillis }
                }
                .catch { error ->
                    logStreamError(error, path, userId)
                    throw error
                }
        )
    }

    private fun logStreamError(error: Throwable, path: String, userId: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }

    suspend fun ensureSeedData(userId: String): Result<Unit> {
        val basePath = "users/$userId"
        return try {
            verifySession(userId)
            val userDoc = db.collection("users").document(userId)

            val existingStaff = userDoc.collection("staff").get().await()
            val existingStaffIds = existingStaff.documents.map { it.id }.toSet()

            val existingTables = userDoc.collection("tables").get().await()
            val existingTableIds = existingTables.documents.map { it.id }.toSet()

            val existingProducts = userDoc.collection("products").get().await()

            val batch = db.batch()
            var hasWrites = false

            // 1. Seed initial 3 staff members if missing
            PasswordHasher.INITIAL_STAFF_CREDENTIALS.forEach { cred ->
                if (cred.username !in existingStaffIds) {
                    val staffRef = userDoc.collection("staff").document(cred.username)
                    batch.set(
                        staffRef,
                        mapOf(
                            "username" to cred.username,
                            "displayName" to cred.displayName,
                            "role" to cred.role,
                            "passwordHash" to cred.hashBase64,
                            "passwordSalt" to cred.saltBase64,
                            "active" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    hasWrites = true
                }
            }

            // 2. Seed all 20 tables (Masa 1 .. Masa 20) if missing
            for (num in 1..20) {
                val docId = "table_$num"
                if (docId !in existingTableIds) {
                    val tableRef = userDoc.collection("tables").document(docId)
                    batch.set(
                        tableRef,
                        mapOf(
                            "tableNumber" to num.toLong(),
                            "name" to "Masa $num",
                            "occupied" to false,
                            "waiterName" to "",
                            "customerNote" to "",
                            "items" to emptyList<Map<String, Any>>(),
                            "totalAmount" to 0.0,
                            "openedAtMillis" to 0L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    hasWrites = true
                }
            }

            // 3. Seed default cafe menu (only when products collection is completely empty)
            if (existingProducts.isEmpty) {
                val defaultProducts = listOf(
                    Triple("prod_cay", "Çay", 25.0) to ("Sıcak İçecekler" to 1L),
                    Triple("prod_fincan_cay", "Fincan Çay", 40.0) to ("Sıcak İçecekler" to 2L),
                    Triple("prod_turk_kahvesi", "Türk Kahvesi", 65.0) to ("Sıcak İçecekler" to 3L),
                    Triple("prod_americano", "Americano", 80.0) to ("Sıcak İçecekler" to 4L),
                    Triple("prod_latte", "Cafe Latte", 95.0) to ("Sıcak İçecekler" to 5L),
                    Triple("prod_sicak_cikolata", "Sıcak Çikolata", 90.0) to ("Sıcak İçecekler" to 6L),
                    Triple("prod_salep", "Salep", 90.0) to ("Sıcak İçecekler" to 7L),
                    Triple("prod_limonata", "Ev Yapımı Limonata", 80.0) to ("Soğuk İçecekler" to 8L),
                    Triple("prod_ice_latte", "Ice Latte", 100.0) to ("Soğuk İçecekler" to 9L),
                    Triple("prod_su", "Su (0.5 L)", 15.0) to ("Soğuk İçecekler" to 10L),
                    Triple("prod_soda", "Maden Suyu", 30.0) to ("Soğuk İçecekler" to 11L),
                    Triple("prod_tost", "Kaşarlı Tost", 110.0) to ("Tatlı & Atıştırmalık" to 12L),
                    Triple("prod_karisik_tost", "Karışık Tost", 130.0) to ("Tatlı & Atıştırmalık" to 13L),
                    Triple("prod_cheesecake", "San Sebastian Cheesecake", 145.0) to ("Tatlı & Atıştırmalık" to 14L),
                    Triple("prod_brownie", "Sıcak Brownie", 135.0) to ("Tatlı & Atıştırmalık" to 15L)
                )
                defaultProducts.forEach { (item, meta) ->
                    val (docId, name, price) = item
                    val (category, sortOrder) = meta
                    val prodRef = userDoc.collection("products").document(docId)
                    batch.set(
                        prodRef,
                        mapOf(
                            "name" to name,
                            "category" to category,
                            "price" to price,
                            "available" to true,
                            "sortOrder" to sortOrder,
                            "updatedBy" to "ErdiBasaran",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    hasWrites = true
                }
            }

            if (hasWrites) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    suspend fun authenticateStaff(
        userId: String,
        username: String,
        password: String
    ): Result<StaffAccount> {
        val cleanUsername = username.trim()
        val path = "users/$userId/staff/$cleanUsername"
        return try {
            verifySession(userId)
            require(cleanUsername.isNotEmpty()) { "Kullanıcı adı boş olamaz." }
            require(password.isNotEmpty()) { "Şifre boş olamaz." }

            val snapshot = db.collection("users").document(userId)
                .collection("staff").document(cleanUsername)
                .get()
                .await()

            val account = snapshot.toObject(
                StaffAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            if (account == null || !account.active) {
                return Result.failure(IllegalArgumentException("Kullanıcı bulunamadı veya pasif."))
            }

            val valid = PasswordHasher.verifyPassword(
                password = password,
                saltBase64 = account.passwordSalt,
                expectedHashBase64 = account.passwordHash
            )
            if (!valid) {
                return Result.failure(IllegalArgumentException("Kullanıcı adı veya şifre hatalı."))
            }

            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun updateStaffPassword(
        userId: String,
        targetUsername: String,
        newPassword: String
    ): Result<Unit> {
        val path = "users/$userId/staff/$targetUsername"
        return try {
            verifySession(userId)
            require(targetUsername.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "Geçersiz kullanıcı adı." }
            require(newPassword.length in 4..64) { "Yeni şifre 4 ile 64 karakter arasında olmalıdır." }
            val (salt, hash) = PasswordHasher.hashPassword(newPassword)
            db.collection("users").document(userId)
                .collection("staff").document(targetUsername)
                .update(
                    mapOf(
                        "passwordSalt" to salt,
                        "passwordHash" to hash,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addProduct(
        userId: String,
        name: String,
        category: String,
        price: Double,
        sortOrder: Long,
        updatedBy: String
    ): Result<Unit> {
        val cleanName = name.trim()
        val cleanCategory = category.trim()
        val cleanActor = updatedBy.trim()
        val productId = "prod_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "users/$userId/products/$productId"
        return try {
            verifySession(userId)
            require(cleanName.length in 1..60) { "Ürün adı 1-60 karakter olmalıdır." }
            require(cleanCategory.length in 1..40) { "Kategori adı 1-40 karakter olmalıdır." }
            require(price in 0.0..100_000.0) { "Fiyat 0 ile 100.000 TL arasında olmalıdır." }
            require(cleanActor.length in 1..40) { "Güncelleyen kullanıcı bilgisi eksik." }

            db.collection("users").document(userId)
                .collection("products").document(productId)
                .set(
                    mapOf(
                        "name" to cleanName,
                        "category" to cleanCategory,
                        "price" to price,
                        "available" to true,
                        "sortOrder" to sortOrder.coerceIn(0L, 10_000L),
                        "updatedBy" to cleanActor,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProduct(
        userId: String,
        productId: String,
        name: String,
        category: String,
        price: Double,
        available: Boolean,
        updatedBy: String
    ): Result<Unit> {
        val cleanName = name.trim()
        val cleanCategory = category.trim()
        val cleanActor = updatedBy.trim()
        val path = "users/$userId/products/$productId"
        return try {
            verifySession(userId)
            require(productId.isNotBlank()) { "Ürün kimliği geçersiz." }
            require(cleanName.length in 1..60) { "Ürün adı 1-60 karakter olmalıdır." }
            require(cleanCategory.length in 1..40) { "Kategori adı 1-40 karakter olmalıdır." }
            require(price in 0.0..100_000.0) { "Fiyat 0 ile 100.000 TL arasında olmalıdır." }
            require(cleanActor.length in 1..40) { "Güncelleyen kullanıcı bilgisi eksik." }

            db.collection("users").document(userId)
                .collection("products").document(productId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "category" to cleanCategory,
                        "price" to price,
                        "available" to available,
                        "updatedBy" to cleanActor,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(userId: String, productId: String): Result<Unit> {
        val path = "users/$userId/products/$productId"
        return try {
            verifySession(userId)
            require(productId.isNotBlank()) { "Ürün kimliği geçersiz." }
            db.collection("users").document(userId)
                .collection("products").document(productId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTableOrder(
        userId: String,
        table: CafeTable,
        items: List<OrderLineItem>,
        waiterName: String,
        customerNote: String
    ): Result<Unit> {
        val tableId = table.id.ifBlank { "table_${table.tableNumber}" }
        val path = "users/$userId/tables/$tableId"
        return try {
            verifySession(userId)
            val boundedItems = items.filter { it.quantity > 0L }.take(50)
            val occupied = boundedItems.isNotEmpty()
            val total = boundedItems.sumOf { it.lineTotal }
            val cleanNote = customerNote.trim().take(200)
            val effectiveWaiter = if (occupied) waiterName.trim().take(40) else ""
            val openedMillis = when {
                !occupied -> 0L
                table.openedAtMillis > 0L -> table.openedAtMillis
                else -> System.currentTimeMillis()
            }

            db.collection("users").document(userId)
                .collection("tables").document(tableId)
                .update(
                    mapOf(
                        "occupied" to occupied,
                        "waiterName" to effectiveWaiter,
                        "customerNote" to if (occupied) cleanNote else "",
                        "items" to boundedItems.map { it.toMap() },
                        "totalAmount" to total,
                        "openedAtMillis" to openedMillis,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun closeTableAndRecordOrder(
        userId: String,
        table: CafeTable,
        paymentMethod: PaymentMethod,
        closedByUsername: String
    ): Result<CompletedOrder> {
        val tableId = table.id.ifBlank { "table_${table.tableNumber}" }
        val orderId = "ord_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val orderPath = "users/$userId/orders/$orderId"
        return try {
            verifySession(userId)
            val validItems = table.items.filter { it.quantity > 0L }.take(50)
            require(validItems.isNotEmpty()) { "Boş masa için hesap kapatılamaz." }
            val totalAmount = validItems.sumOf { it.lineTotal }
            require(totalAmount > 0.0) { "Toplam tutar 0 TL'den büyük olmalıdır." }

            val itemCount = validItems.sumOf { it.quantity }
            val itemsSummary = validItems.joinToString(", ") { "${it.quantity}x ${it.productName}" }.take(500)
            val waiter = table.waiterName.ifBlank { closedByUsername }.take(40)
            val closedAtMillis = System.currentTimeMillis()
            val businessDate = Formatters.todayBusinessDate()

            val userRef = db.collection("users").document(userId)
            val orderRef = userRef.collection("orders").document(orderId)
            val tableRef = userRef.collection("tables").document(tableId)

            val orderPayload = mapOf(
                "tableNumber" to table.tableNumber,
                "tableName" to table.name,
                "waiterUsername" to waiter,
                "closedByUsername" to closedByUsername.take(40),
                "paymentMethod" to paymentMethod.wireValue,
                "totalAmount" to totalAmount,
                "itemCount" to itemCount,
                "itemsSummary" to itemsSummary,
                "items" to validItems.map { it.toMap() },
                "businessDate" to businessDate,
                "closedAtMillis" to closedAtMillis,
                "status" to "COMPLETED",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(orderRef, orderPayload)
            batch.update(
                tableRef,
                mapOf(
                    "occupied" to false,
                    "waiterName" to "",
                    "customerNote" to "",
                    "items" to emptyList<Map<String, Any>>(),
                    "totalAmount" to 0.0,
                    "openedAtMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            Result.success(
                CompletedOrder(
                    id = orderId,
                    tableNumber = table.tableNumber,
                    tableName = table.name,
                    waiterUsername = waiter,
                    closedByUsername = closedByUsername,
                    paymentMethod = paymentMethod.wireValue,
                    totalAmount = totalAmount,
                    itemCount = itemCount,
                    itemsSummary = itemsSummary,
                    items = validItems,
                    businessDate = businessDate,
                    closedAtMillis = closedAtMillis,
                    status = "COMPLETED"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, orderPath)
            Result.failure(e)
        }
    }
}

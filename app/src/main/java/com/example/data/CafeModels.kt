package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StaffRole(val labelTr: String) {
    ADMIN("Yönetici"),
    WAITER("Garson");

    companion object {
        fun fromRaw(raw: String?): StaffRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WAITER
    }
}

enum class PaymentMethod(val wireValue: String, val labelTr: String) {
    NAKIT("NAKİT", "Nakit"),
    KART("KART", "Kredi / Banka Kartı"),
    HAVALE("HAVALE", "Havale / EFT");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.wireValue == raw || it.name == raw } ?: NAKIT
    }
}

@Keep
@IgnoreExtraProperties
data class StaffAccount(
    @DocumentId val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = "WAITER",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val staffRole: StaffRole
        get() = StaffRole.fromRaw(role)

    val isAdmin: Boolean
        get() = staffRole == StaffRole.ADMIN
}

@Keep
@IgnoreExtraProperties
data class MenuProduct(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = "",
    val price: Double = 0.0,
    val available: Boolean = true,
    val sortOrder: Long = 0L,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val productId: String = "",
    val productName: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Long = 1L,
    val lineTotal: Double = 0.0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "productName" to productName,
        "unitPrice" to unitPrice,
        "quantity" to quantity,
        "lineTotal" to lineTotal
    )
}

@Keep
@IgnoreExtraProperties
data class CafeTable(
    @DocumentId val id: String = "",
    val tableNumber: Long = 1L,
    val name: String = "",
    val occupied: Boolean = false,
    val waiterName: String = "",
    val customerNote: String = "",
    val items: List<OrderLineItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val openedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val totalItemCount: Int
        get() = items.sumOf { it.quantity.toInt() }
}

@Keep
@IgnoreExtraProperties
data class CompletedOrder(
    @DocumentId val id: String = "",
    val tableNumber: Long = 1L,
    val tableName: String = "",
    val waiterUsername: String = "",
    val closedByUsername: String = "",
    val paymentMethod: String = "NAKİT",
    val totalAmount: Double = 0.0,
    val itemCount: Long = 0L,
    val itemsSummary: String = "",
    val items: List<OrderLineItem> = emptyList(),
    val businessDate: String = "",
    val closedAtMillis: Long = 0L,
    val status: String = "COMPLETED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ProductSalesRow(
    val productName: String,
    val quantitySold: Int,
    val totalRevenue: Double
)

data class StaffSalesRow(
    val username: String,
    val orderCount: Int,
    val totalRevenue: Double
)

data class DailyReportSummary(
    val dateLabel: String,
    val totalRevenue: Double,
    val orderCount: Int,
    val totalItemsSold: Int,
    val averageOrderValue: Double,
    val cashTotal: Double,
    val cardTotal: Double,
    val transferTotal: Double,
    val productBreakdowns: List<ProductSalesRow>,
    val staffBreakdowns: List<StaffSalesRow>,
    val orders: List<CompletedOrder>
)

data class LiveOccupancySummary(
    val occupiedCount: Int,
    val totalTables: Int,
    val occupancyPercent: Int,
    val openRevenue: Double,
    val occupiedTables: List<CafeTable>
)

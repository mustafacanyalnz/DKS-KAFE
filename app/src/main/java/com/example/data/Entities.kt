package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class UserRole(val labelTr: String) {
    ADMIN("Yönetici"),
    WAITER("Garson")
}

enum class PaymentMethod(val labelTr: String) {
    NAKIT("Nakit"),
    KART("Kredi Kartı"),
    HAVALE("Havale / QR")
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val displayName: String,
    val role: String, // UserRole.name
    val passwordSalt: String,
    val passwordHash: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    val userRole: UserRole
        get() = UserRole.entries.firstOrNull { it.name == role } ?: UserRole.WAITER
}

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: String,
    val priceTl: Double,
    val isAvailable: Boolean = true,
    val sortOrder: Int = 100,
    val updatedBy: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cafe_tables")
data class CafeTableEntity(
    @PrimaryKey val tableNumber: Int,
    val isOccupied: Boolean = false,
    val openedByUsername: String? = null,
    val openedAt: Long? = null,
    val note: String = ""
)

@Entity(
    tableName = "active_order_items",
    foreignKeys = [
        ForeignKey(
            entity = CafeTableEntity::class,
            parentColumns = ["tableNumber"],
            childColumns = ["tableNumber"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tableNumber")]
)
data class ActiveOrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tableNumber: Int,
    val menuItemId: Long,
    val productName: String,
    val unitPriceTl: Double,
    val quantity: Int,
    val addedByUsername: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    val lineTotalTl: Double
        get() = unitPriceTl * quantity
}

@Entity(tableName = "order_history", indices = [Index("closedAt")])
data class OrderHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val orderCode: String,
    val tableNumber: Int,
    val waiterUsername: String,
    val closedByUsername: String,
    val paymentMethod: String, // PaymentMethod.name
    val totalAmountTl: Double,
    val itemCount: Int,
    val note: String = "",
    val openedAt: Long,
    val closedAt: Long = System.currentTimeMillis()
) {
    val paymentMethodEnum: PaymentMethod
        get() = PaymentMethod.entries.firstOrNull { it.name == paymentMethod } ?: PaymentMethod.NAKIT
}

@Entity(
    tableName = "order_history_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId")]
)
data class OrderHistoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val orderId: Long,
    val productName: String,
    val unitPriceTl: Double,
    val quantity: Int,
    val lineTotalTl: Double
)

@Entity(tableName = "audit_logs", indices = [Index("createdAt")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val actorUsername: String,
    val actorRole: String,
    val actionType: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class TableWithItems(
    @Embedded val table: CafeTableEntity,
    @Relation(
        parentColumn = "tableNumber",
        entityColumn = "tableNumber"
    )
    val items: List<ActiveOrderItemEntity>
) {
    val totalAmountTl: Double
        get() = items.sumOf { it.lineTotalTl }

    val totalItemCount: Int
        get() = items.sumOf { it.quantity }
}

data class OrderHistoryWithItems(
    @Embedded val order: OrderHistoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderHistoryItemEntity>
)

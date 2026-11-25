package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY role ASC, username ASC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu_items ORDER BY sortOrder ASC, category ASC, name ASC")
    fun observeMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun countMenuItems(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MenuItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMenuItem(item: MenuItemEntity): Long

    @Query("DELETE FROM menu_items WHERE id = :id")
    suspend fun deleteMenuItemById(id: Long)
}

@Dao
interface TableDao {
    @Transaction
    @Query("SELECT * FROM cafe_tables ORDER BY tableNumber ASC")
    fun observeTablesWithItems(): Flow<List<TableWithItems>>

    @Query("SELECT * FROM cafe_tables WHERE tableNumber = :tableNumber LIMIT 1")
    suspend fun getTable(tableNumber: Int): CafeTableEntity?

    @Query("SELECT COUNT(*) FROM cafe_tables")
    suspend fun countTables(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTables(tables: List<CafeTableEntity>)

    @Update
    suspend fun updateTable(table: CafeTableEntity)

    @Query("SELECT * FROM active_order_items WHERE tableNumber = :tableNumber ORDER BY addedAt ASC, id ASC")
    suspend fun getActiveItemsForTable(tableNumber: Int): List<ActiveOrderItemEntity>

    @Query(
        "SELECT * FROM active_order_items WHERE tableNumber = :tableNumber AND menuItemId = :menuItemId AND unitPriceTl = :unitPriceTl LIMIT 1"
    )
    suspend fun findMatchingActiveItem(
        tableNumber: Int,
        menuItemId: Long,
        unitPriceTl: Double
    ): ActiveOrderItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveItem(item: ActiveOrderItemEntity): Long

    @Update
    suspend fun updateActiveItem(item: ActiveOrderItemEntity)

    @Query("DELETE FROM active_order_items WHERE id = :itemId")
    suspend fun deleteActiveItemById(itemId: Long)

    @Query("DELETE FROM active_order_items WHERE tableNumber = :tableNumber")
    suspend fun clearActiveItemsForTable(tableNumber: Int)

    @Query("UPDATE active_order_items SET tableNumber = :toTable WHERE tableNumber = :fromTable")
    suspend fun moveActiveItems(fromTable: Int, toTable: Int)
}

@Dao
interface OrderHistoryDao {
    @Transaction
    @Query("SELECT * FROM order_history ORDER BY closedAt DESC")
    fun observeOrderHistory(): Flow<List<OrderHistoryWithItems>>

    @Insert
    suspend fun insertOrderHistory(order: OrderHistoryEntity): Long

    @Insert
    suspend fun insertOrderHistoryItems(items: List<OrderHistoryItemEntity>)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 200")
    fun observeRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertLog(log: AuditLogEntity)
}

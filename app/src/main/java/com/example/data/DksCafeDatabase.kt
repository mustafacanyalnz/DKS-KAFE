package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        MenuItemEntity::class,
        CafeTableEntity::class,
        ActiveOrderItemEntity::class,
        OrderHistoryEntity::class,
        OrderHistoryItemEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DksCafeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun menuDao(): MenuDao
    abstract fun tableDao(): TableDao
    abstract fun orderHistoryDao(): OrderHistoryDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: DksCafeDatabase? = null

        fun getInstance(context: Context): DksCafeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DksCafeDatabase::class.java,
                    "dks_sahne_kafe.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

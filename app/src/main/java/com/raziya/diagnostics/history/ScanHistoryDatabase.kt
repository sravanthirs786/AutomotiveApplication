package com.raziya.diagnostics.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {
    @Query("SELECT * FROM scan_records ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<ScanRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ScanRecord): Long
}

@Database(entities = [ScanRecord::class], version = 1, exportSchema = false)
abstract class ScanHistoryDatabase : RoomDatabase() {
    abstract fun scanRecords(): ScanRecordDao

    companion object {
        @Volatile private var instance: ScanHistoryDatabase? = null

        fun get(context: Context): ScanHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ScanHistoryDatabase::class.java,
                "raziya-scan-history.db",
            ).build().also { instance = it }
        }
    }
}

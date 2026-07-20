package com.febricahyaa.fluxlab.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "benchmark_sessions")
data class BenchmarkSessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val status: String,
    val label: String,
    val appVersion: String,
    val benchmarkSchemaVersion: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidFingerprint: String,
    val kernelVersion: String,
    val fluxInstalled: Boolean,
    val fluxEnabled: Boolean?,
    val fluxRuntimeAvailable: Boolean,
    val fluxDaemonAlive: Boolean?,
    val fluxVersionName: String?,
    val fluxVersionCode: Long?,
    val fluxActiveProfile: String?,
    val fluxKernelType: String?,
    val fluxConfigDirectory: String,
    val synthesisAvailable: Boolean,
    val rootState: String,
    val charging: Boolean?,
    val batteryLevel: Int?,
    val initialBatteryTemperatureC: Double?,
    val peakBatteryTemperatureC: Double?,
    val androidThermalStatus: Int?,
    val thermalHeadroomSamples: String,
    val refreshRateHz: Double?,
    val warnings: String,
    val failureReason: String?,
    val comparisonRole: String,
    val methodologyMetadata: String = "",
)

@Entity(
    tableName = "workload_results",
    primaryKeys = ["sessionId", "kind"],
    foreignKeys = [
        ForeignKey(
            entity = BenchmarkSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class WorkloadResultEntity(
    val sessionId: String,
    val kind: String,
    val workloadVersion: Int,
    val unit: String,
    val repetitions: String,
    val durationsNs: String,
    val median: Double,
    val minimum: Double,
    val maximum: Double,
    val standardDeviation: Double,
    val coefficientOfVariation: Double?,
    val validationChecksum: String,
    val threadCount: Int,
    val affinityForced: Boolean,
    val warnings: String,
)

data class SessionWithWorkloads(
    @Embedded val session: BenchmarkSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val workloads: List<WorkloadResultEntity>,
)

@Dao
interface BenchmarkDao {
    @Transaction
    @Query("SELECT * FROM benchmark_sessions ORDER BY startedAtEpochMs DESC")
    fun observeAll(): Flow<List<SessionWithWorkloads>>

    @Transaction
    @Query("SELECT * FROM benchmark_sessions WHERE id = :id")
    suspend fun get(id: String): SessionWithWorkloads?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: BenchmarkSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkloads(workloads: List<WorkloadResultEntity>)

    @Query("DELETE FROM workload_results WHERE sessionId = :sessionId")
    suspend fun clearWorkloads(sessionId: String)

    @Transaction
    suspend fun save(session: BenchmarkSessionEntity, workloads: List<WorkloadResultEntity>) {
        upsertSession(session)
        clearWorkloads(session.id)
        if (workloads.isNotEmpty()) upsertWorkloads(workloads)
    }

    @Query("UPDATE benchmark_sessions SET label = :label WHERE id = :id")
    suspend fun rename(id: String, label: String)

    @Query("UPDATE benchmark_sessions SET comparisonRole = 'NONE' WHERE comparisonRole = 'BASELINE'")
    suspend fun clearBaseline()

    @Query("UPDATE benchmark_sessions SET comparisonRole = 'BASELINE' WHERE id = :id AND status = 'COMPLETED'")
    suspend fun setBaseline(id: String)

    @Transaction
    suspend fun markBaseline(id: String) {
        clearBaseline()
        setBaseline(id)
    }

    @Query("DELETE FROM benchmark_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM benchmark_sessions")
    suspend fun deleteAll()
}

@Database(
    entities = [BenchmarkSessionEntity::class, WorkloadResultEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class FluxLabDatabase : RoomDatabase() {
    abstract fun benchmarkDao(): BenchmarkDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE benchmark_sessions ADD COLUMN label TEXT NOT NULL DEFAULT 'Quick Test'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE benchmark_sessions ADD COLUMN methodologyMetadata TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): FluxLabDatabase = Room.databaseBuilder(
            context.applicationContext,
            FluxLabDatabase::class.java,
            "fluxlab.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}

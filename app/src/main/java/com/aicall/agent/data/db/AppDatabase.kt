package com.aicall.agent.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aicall.agent.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_records ORDER BY startTime DESC")
    fun getAllRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getRecordById(sessionId: String): CallRecord?

    @Query("SELECT * FROM call_records WHERE callerNumber = :phoneNumber ORDER BY startTime DESC")
    fun getRecordsForCaller(phoneNumber: String): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE callerNumber = :phoneNumber ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestRecordForCaller(phoneNumber: String): CallRecord?

    @Query("SELECT * FROM call_records WHERE handlingStatus = 'AI_HANDLED' ORDER BY startTime DESC")
    fun getAiHandledRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE urgencyLevel IN ('HIGH', 'EMERGENCY') ORDER BY startTime DESC")
    fun getUrgentRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE riskLevel IN ('SUSPICIOUS', 'HIGH_RISK_FRAUD') OR category IN ('SPAM', 'FRAUD_SUSPECTED') ORDER BY startTime DESC")
    fun getSpamAndFraudRecords(): Flow<List<CallRecord>>

    @Query("SELECT COUNT(*) FROM call_records")
    fun getTotalCallCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_records WHERE handlingStatus = 'AI_HANDLED'")
    fun getAiHandledCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_records WHERE handlingStatus = 'USER_ANSWERED'")
    fun getUserAnsweredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_records WHERE handlingStatus = 'MISSED'")
    fun getMissedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_records WHERE urgencyLevel IN ('HIGH', 'EMERGENCY')")
    fun getUrgentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_records WHERE riskLevel IN ('SUSPICIOUS', 'HIGH_RISK_FRAUD') OR category IN ('SPAM', 'FRAUD_SUSPECTED')")
    fun getSpamCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CallRecord)

    @Update
    suspend fun updateRecord(record: CallRecord)

    @Delete
    suspend fun deleteRecord(record: CallRecord)

    @Query("DELETE FROM call_records")
    suspend fun clearAll()
}

@Dao
interface CallerProfileDao {
    @Query("SELECT * FROM caller_profiles ORDER BY lastInteractionTime DESC")
    fun getAllProfiles(): Flow<List<CallerProfile>>

    @Query("SELECT * FROM caller_profiles WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getProfile(phoneNumber: String): CallerProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: CallerProfile)

    @Delete
    suspend fun deleteProfile(profile: CallerProfile)

    @Query("DELETE FROM caller_profiles WHERE phoneNumber = :phoneNumber")
    suspend fun deleteProfileByNumber(phoneNumber: String)
}

@Dao
interface AgentRuleDao {
    @Query("SELECT * FROM agent_rules WHERE isEnabled = 1 ORDER BY priority DESC")
    fun getActiveRules(): Flow<List<AgentRule>>

    @Query("SELECT * FROM agent_rules ORDER BY priority DESC")
    fun getAllRules(): Flow<List<AgentRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AgentRule)

    @Update
    suspend fun updateRule(rule: AgentRule)

    @Delete
    suspend fun deleteRule(rule: AgentRule)
}

@Dao
interface AgentScheduleDao {
    @Query("SELECT * FROM agent_schedules WHERE isEnabled = 1")
    fun getActiveSchedules(): Flow<List<AgentSchedule>>

    @Query("SELECT * FROM agent_schedules")
    fun getAllSchedules(): Flow<List<AgentSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: AgentSchedule)

    @Update
    suspend fun updateSchedule(schedule: AgentSchedule)

    @Delete
    suspend fun deleteSchedule(schedule: AgentSchedule)
}

@Dao
interface ActionItemDao {
    @Query("SELECT * FROM action_items WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun getPendingActionItems(): Flow<List<ActionItem>>

    @Query("SELECT * FROM action_items ORDER BY createdAt DESC")
    fun getAllActionItems(): Flow<List<ActionItem>>

    @Query("SELECT COUNT(*) FROM action_items WHERE isCompleted = 0")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM action_items WHERE callSessionId = :sessionId")
    fun getItemsForCall(sessionId: String): Flow<List<ActionItem>>

    @Query("SELECT * FROM action_items WHERE callSessionId = :sessionId AND isCompleted = 0")
    fun getPendingItemsForCall(sessionId: String): Flow<List<ActionItem>>

    @Query("SELECT * FROM action_items WHERE id = :id LIMIT 1")
    suspend fun getActionItemById(id: Long): ActionItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionItem(item: ActionItem)

    @Update
    suspend fun updateActionItem(item: ActionItem)

    @Delete
    suspend fun deleteActionItem(item: ActionItem)

    @Query("DELETE FROM action_items WHERE callSessionId = :sessionId")
    suspend fun deleteItemsForCall(sessionId: String)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `action_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `callSessionId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `priority` TEXT NOT NULL, `dueDate` INTEGER, `isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_items_callSessionId` ON `action_items` (`callSessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_items_isCompleted` ON `action_items` (`isCompleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_items_createdAt` ON `action_items` (`createdAt`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `eventType` TEXT NOT NULL, `callSessionId` TEXT, `description` TEXT NOT NULL, `severity` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_callSessionId` ON `audit_logs` (`callSessionId`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_startTime` ON `call_records` (`startTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_callerNumber` ON `call_records` (`callerNumber`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_urgencyLevel` ON `call_records` (`urgencyLevel`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_riskLevel` ON `call_records` (`riskLevel`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_handlingStatus` ON `call_records` (`handlingStatus`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_caller_profiles_phoneNumber` ON `caller_profiles` (`phoneNumber`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_caller_profiles_lastInteractionTime` ON `caller_profiles` (`lastInteractionTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_caller_profiles_isVerified` ON `caller_profiles` (`isVerified`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_rules_isEnabled` ON `agent_rules` (`isEnabled`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_rules_priority` ON `agent_rules` (`priority`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_schedules_isEnabled` ON `agent_schedules` (`isEnabled`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `call_records` ADD COLUMN `processingError` TEXT DEFAULT NULL")
        } catch (e: Exception) {
            // Column may already exist
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_startTime` ON `call_records` (`startTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_callerNumber` ON `call_records` (`callerNumber`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_urgencyLevel` ON `call_records` (`urgencyLevel`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_riskLevel` ON `call_records` (`riskLevel`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_handlingStatus` ON `call_records` (`handlingStatus`)")
    }
}

@Database(
    entities = [
        CallRecord::class,
        CallerProfile::class,
        AgentRule::class,
        AgentSchedule::class,
        ActionItem::class,
        AuditLog::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callRecordDao(): CallRecordDao
    abstract fun callerProfileDao(): CallerProfileDao
    abstract fun agentRuleDao(): AgentRuleDao
    abstract fun agentScheduleDao(): AgentScheduleDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aicall_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}



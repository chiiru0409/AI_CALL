# AI Personal Call Agent — Startup Crash Diagnostic Report

## 1. Environment & Target Device
- Target Device Serial: `10BE7U31G8000R6`
- Model: iQOO Z9 5G (`I2302`)
- Android Version: Android 13 (API 33)
- Application Package: `com.aicall.agent.debug`
- Launcher Activity: `com.aicall.agent.ui.MainActivity`

## 2. Reproduced Crash Stack Trace (Extracted from Device Telemetry)
The following fatal exception was recorded during startup:

```
java.lang.IllegalStateException: Migration didn't properly handle: caller_profiles(com.aicall.agent.data.model.CallerProfile).
 Expected:
TableInfo{name='caller_profiles', columns={preferences=Column{name='preferences', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, isVerified=Column{name='isVerified', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, contactName=Column{name='contactName', type='TEXT', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, unresolvedRequests=Column{name='unresolvedRequests', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, totalCalls=Column{name='totalCalls', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, lastInteractionTime=Column{name='lastInteractionTime', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, phoneNumber=Column{name='phoneNumber', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=1, defaultValue='undefined'}, trustScore=Column{name='trustScore', type='REAL', affinity='4', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, memorySummary=Column{name='memorySummary', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, claimedName=Column{name='claimedName', type='TEXT', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, verificationMethod=Column{name='verificationMethod', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, category=Column{name='category', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, userNotes=Column{name='userNotes', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, customHandlingPolicy=Column{name='customHandlingPolicy', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}}, foreignKeys=[], indices=[Index{name='index_caller_profiles_phoneNumber', unique=false, columns=[phoneNumber], orders=[ASC]'}, Index{name='index_caller_profiles_lastInteractionTime', unique=false, columns=[lastInteractionTime], orders=[ASC]'}, Index{name='index_caller_profiles_isVerified', unique=false, columns=[isVerified], orders=[ASC]'}]}
 Found:
TableInfo{name='caller_profiles', columns={phoneNumber=Column{name='phoneNumber', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=1, defaultValue='undefined'}, contactName=Column{name='contactName', type='TEXT', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, claimedName=Column{name='claimedName', type='TEXT', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, isVerified=Column{name='isVerified', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, verificationMethod=Column{name='verificationMethod', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, category=Column{name='category', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, trustScore=Column{name='trustScore', type='REAL', affinity='4', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, totalCalls=Column{name='totalCalls', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, lastInteractionTime=Column{name='lastInteractionTime', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, userNotes=Column{name='userNotes', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, memorySummary=Column{name='memorySummary', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, customHandlingPolicy=Column{name='customHandlingPolicy', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}}, foreignKeys=[], indices=[Index{name='index_caller_profiles_isVerified', unique=false, columns=[isVerified], orders=[ASC]'}, Index{name='index_caller_profiles_lastInteractionTime', unique=false, columns=[lastInteractionTime], orders=[ASC]'}, Index{name='index_caller_profiles_phoneNumber', unique=false, columns=[phoneNumber], orders=[ASC]'}]}
	at androidx.room.RoomOpenHelper.onUpgrade(RoomOpenHelper.kt:93)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.onUpgrade(FrameworkSQLiteOpenHelper.kt:253)
	at android.database.sqlite.SQLiteOpenHelper.getDatabaseLocked(SQLiteOpenHelper.java:437)
	at android.database.sqlite.SQLiteOpenHelper.getWritableDatabase(SQLiteOpenHelper.java:336)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.getWritableOrReadableDatabase(FrameworkSQLiteOpenHelper.kt:232)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.innerGetDatabase(FrameworkSQLiteOpenHelper.kt:190)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.getSupportDatabase(FrameworkSQLiteOpenHelper.kt:151)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper.getWritableDatabase(FrameworkSQLiteOpenHelper.kt:104)
	at androidx.room.RoomDatabase.inTransaction(RoomDatabase.kt:632)
	at androidx.room.RoomDatabase.assertNotSuspendingTransaction(RoomDatabase.kt:451)
	at androidx.room.RoomDatabase.query(RoomDatabase.kt:480)
	at androidx.room.util.DBUtil.query(DBUtil.kt:75)
	at com.aicall.agent.data.db.CallRecordDao_Impl$9.call(CallRecordDao_Impl.java:266)
	at com.aicall.agent.data.db.CallRecordDao_Impl$9.call(CallRecordDao_Impl.java:262)
	at androidx.room.CoroutinesRoom$Companion$createFlow$1$1$1.invokeSuspend(CoroutinesRoom.kt:129)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
```

## 3. Root Cause Analysis
1. **Room Database Schema Migration Desynchronization**:
   The existing database on the target device was initialized at schema version 1.
   When the app was launched with Room database entity definitions at version 3:
   - The initial version upgrade had an incomplete `MIGRATION_1_2` and `MIGRATION_2_3` without adding `unresolvedRequests` and `preferences` columns to `caller_profiles`.
   - `MIGRATION_1_3` was previously absent from `.addMigrations()`.
   - When Room triggered `onUpgrade()` on the background coroutine driving the `recentCallsFlow` in `DialerScreen`, Room compared the real table columns with the compiled `TableInfo` entity definition.
   - The mismatch threw an `IllegalStateException`, which crashed the app immediately on startup before the Compose dashboard could stabilize.

2. **Migration Robustness Enhancement Needed**:
   - `safeAddColumn` in `AppDatabase.kt` should robustly inspect `PRAGMA table_info` before executing `ALTER TABLE` to avoid silent no-ops and ensure that schema upgrades from version 1, 2, or 3 succeed reliably with zero data loss.
   - The dashboard and navigation must remain fully accessible even if telephony roles or optional permissions are not yet granted.

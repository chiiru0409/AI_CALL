package com.aicall.agent.data.repository

import com.aicall.agent.data.db.ActionItemDao
import com.aicall.agent.data.db.AuditLogDao
import com.aicall.agent.data.db.CallRecordDao
import com.aicall.agent.data.model.ActionItem
import com.aicall.agent.data.model.AuditLog
import com.aicall.agent.data.model.CallRecord
import kotlinx.coroutines.flow.Flow

class CallRepository(
    private val callRecordDao: CallRecordDao,
    private val actionItemDao: ActionItemDao,
    private val auditLogDao: AuditLogDao
) {
    val allCalls: Flow<List<CallRecord>> = callRecordDao.getAllRecords()
    val aiHandledCalls: Flow<List<CallRecord>> = callRecordDao.getAiHandledRecords()
    val urgentCalls: Flow<List<CallRecord>> = callRecordDao.getUrgentRecords()
    val spamCalls: Flow<List<CallRecord>> = callRecordDao.getSpamAndFraudRecords()
    val pendingActionItems: Flow<List<ActionItem>> = actionItemDao.getPendingActionItems()
    val allActionItems: Flow<List<ActionItem>> = actionItemDao.getAllActionItems()
    val pendingActionItemsCount: Flow<Int> = actionItemDao.getPendingCount()

    val totalCount: Flow<Int> = callRecordDao.getTotalCallCount()
    val aiHandledCount: Flow<Int> = callRecordDao.getAiHandledCount()
    val userAnsweredCount: Flow<Int> = callRecordDao.getUserAnsweredCount()
    val missedCount: Flow<Int> = callRecordDao.getMissedCount()
    val urgentCount: Flow<Int> = callRecordDao.getUrgentCount()
    val spamCount: Flow<Int> = callRecordDao.getSpamCount()

    suspend fun getCallById(sessionId: String): CallRecord? {
        return callRecordDao.getRecordById(sessionId)
    }

    fun getCallsForNumber(phoneNumber: String): Flow<List<CallRecord>> {
        return callRecordDao.getRecordsForCaller(phoneNumber)
    }

    suspend fun getLatestCallForNumber(phoneNumber: String): CallRecord? {
        return callRecordDao.getLatestRecordForCaller(phoneNumber)
    }

    suspend fun saveCallRecord(record: CallRecord) {
        callRecordDao.insertRecord(record)
        auditLogDao.insertLog(
            AuditLog(
                eventType = "CALL_RECORD_SAVED",
                callSessionId = record.sessionId,
                description = "Call from ${record.callerNumber} saved with status ${record.handlingStatus}"
            )
        )
    }

    suspend fun updateCallRecord(record: CallRecord) {
        callRecordDao.updateRecord(record)
    }

    suspend fun deleteCallRecord(record: CallRecord) {
        callRecordDao.deleteRecord(record)
        actionItemDao.deleteItemsForCall(record.sessionId)
        auditLogDao.insertLog(
            AuditLog(
                eventType = "CALL_RECORD_DELETED",
                callSessionId = record.sessionId,
                description = "Call record ${record.sessionId} deleted"
            )
        )
    }

    suspend fun clearAllRecords() {
        callRecordDao.clearAll()
        auditLogDao.insertLog(
            AuditLog(
                eventType = "ALL_RECORDS_CLEARED",
                description = "All call intelligence records cleared from local database"
            )
        )
    }

    fun getActionItemsForCall(sessionId: String): Flow<List<ActionItem>> {
        return actionItemDao.getItemsForCall(sessionId)
    }

    fun getPendingActionItemsForCall(sessionId: String): Flow<List<ActionItem>> {
        return actionItemDao.getPendingItemsForCall(sessionId)
    }

    suspend fun getActionItemById(id: Long): ActionItem? {
        return actionItemDao.getActionItemById(id)
    }

    suspend fun addActionItem(item: ActionItem) {
        actionItemDao.insertActionItem(item)
    }

    suspend fun toggleActionItem(item: ActionItem) {
        actionItemDao.updateActionItem(item.copy(isCompleted = !item.isCompleted))
    }

    suspend fun deleteActionItem(item: ActionItem) {
        actionItemDao.deleteActionItem(item)
    }
}


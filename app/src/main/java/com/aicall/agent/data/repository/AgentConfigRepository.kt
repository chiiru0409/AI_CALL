package com.aicall.agent.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.aicall.agent.data.db.AgentRuleDao
import com.aicall.agent.data.db.AgentScheduleDao
import com.aicall.agent.data.model.AgentRule
import com.aicall.agent.data.model.AgentSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

class AgentConfigRepository(
    context: Context,
    private val agentRuleDao: AgentRuleDao,
    private val agentScheduleDao: AgentScheduleDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("agent_config_prefs", Context.MODE_PRIVATE)

    private val _isAgentModeEnabled = MutableStateFlow(prefs.getBoolean("key_agent_mode_enabled", true))
    val isAgentModeEnabled: StateFlow<Boolean> = _isAgentModeEnabled.asStateFlow()

    private val _isScheduleModeActive = MutableStateFlow(prefs.getBoolean("key_schedule_mode_active", false))
    val isScheduleModeActive: StateFlow<Boolean> = _isScheduleModeActive.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(prefs.getString("key_preferred_language", "Telugu-English") ?: "Telugu-English")
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    private val _approvedKnowledgeBase = MutableStateFlow(
        prefs.getString(
            "key_knowledge_base",
            "User is currently available for urgent calls only. Normal college updates and delivery updates can be recorded. Never reveal OTP, banking passwords, or home address."
        ) ?: ""
    )
    val approvedKnowledgeBase: StateFlow<String> = _approvedKnowledgeBase.asStateFlow()

    val activeRules: Flow<List<AgentRule>> = agentRuleDao.getActiveRules()
    val allRules: Flow<List<AgentRule>> = agentRuleDao.getAllRules()
    val activeSchedules: Flow<List<AgentSchedule>> = agentScheduleDao.getActiveSchedules()
    val allSchedules: Flow<List<AgentSchedule>> = agentScheduleDao.getAllSchedules()

    fun setAgentMode(enabled: Boolean) {
        prefs.edit().putBoolean("key_agent_mode_enabled", enabled).apply()
        _isAgentModeEnabled.value = enabled
    }

    fun setScheduleMode(active: Boolean) {
        prefs.edit().putBoolean("key_schedule_mode_active", active).apply()
        _isScheduleModeActive.value = active
    }

    fun setPreferredLanguage(lang: String) {
        prefs.edit().putString("key_preferred_language", lang).apply()
        _preferredLanguage.value = lang
    }

    fun updateKnowledgeBase(text: String) {
        prefs.edit().putString("key_knowledge_base", text).apply()
        _approvedKnowledgeBase.value = text
    }

    suspend fun saveRule(rule: AgentRule) = agentRuleDao.insertRule(rule)
    suspend fun deleteRule(rule: AgentRule) = agentRuleDao.deleteRule(rule)

    suspend fun saveSchedule(schedule: AgentSchedule) = agentScheduleDao.insertSchedule(schedule)
    suspend fun deleteSchedule(schedule: AgentSchedule) = agentScheduleDao.deleteSchedule(schedule)

    fun isAgentActiveNow(schedules: List<AgentSchedule>? = null): Boolean {
        return isAgentActiveAt(Calendar.getInstance(), schedules)
    }

    fun isAgentActiveAt(cal: Calendar, schedules: List<AgentSchedule>? = null): Boolean {
        if (!_isAgentModeEnabled.value) return false
        if (!_isScheduleModeActive.value) return true // Manual mode ON

        val currentDay = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1, Monday=2, ...
        val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val dayBit = dayOfWeekToBit(currentDay)
        val prevDayBit = dayOfWeekToBit(getPreviousDayOfWeek(currentDay))

        val activeList = schedules ?: emptyList()
        if (activeList.isEmpty()) {
            return true
        }

        return activeList.any { schedule ->
            if (!schedule.isEnabled) return@any false

            val isOvernight = schedule.crossesMidnight || schedule.startMinuteOfDay > schedule.endMinuteOfDay

            if (schedule.startMinuteOfDay == schedule.endMinuteOfDay) {
                // Equal start and end times represent 24-hour active window on configured days
                (schedule.daysOfWeekMask and dayBit) != 0
            } else if (isOvernight) {
                // Active if:
                // 1) Current day evening window: today's bit is selected AND time >= startMinuteOfDay
                val eveningActive = ((schedule.daysOfWeekMask and dayBit) != 0) && (currentMinute >= schedule.startMinuteOfDay)
                // 2) Previous day overnight morning window: yesterday's bit was selected AND time < endMinuteOfDay
                val morningActive = ((schedule.daysOfWeekMask and prevDayBit) != 0) && (currentMinute < schedule.endMinuteOfDay)
                eveningActive || morningActive
            } else {
                val dayMatches = (schedule.daysOfWeekMask and dayBit) != 0
                dayMatches && (currentMinute >= schedule.startMinuteOfDay) && (currentMinute < schedule.endMinuteOfDay)
            }
        }
    }

    companion object {
        fun dayOfWeekToBit(dayOfWeek: Int): Int {
            return when (dayOfWeek) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 4
                Calendar.THURSDAY -> 8
                Calendar.FRIDAY -> 16
                Calendar.SATURDAY -> 32
                Calendar.SUNDAY -> 64
                else -> 1
            }
        }

        fun getPreviousDayOfWeek(dayOfWeek: Int): Int {
            return when (dayOfWeek) {
                Calendar.SUNDAY -> Calendar.SATURDAY
                Calendar.MONDAY -> Calendar.SUNDAY
                Calendar.TUESDAY -> Calendar.MONDAY
                Calendar.WEDNESDAY -> Calendar.TUESDAY
                Calendar.THURSDAY -> Calendar.WEDNESDAY
                Calendar.FRIDAY -> Calendar.THURSDAY
                Calendar.SATURDAY -> Calendar.FRIDAY
                else -> Calendar.SUNDAY
            }
        }
    }
}


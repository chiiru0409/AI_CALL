package com.aicall.agent.data.db

import androidx.room.TypeConverter
import com.aicall.agent.data.model.*

class Converters {
    @TypeConverter
    fun fromCallDirection(value: CallDirection): String = value.name

    @TypeConverter
    fun toCallDirection(value: String): CallDirection = runCatching { CallDirection.valueOf(value) }.getOrDefault(CallDirection.INCOMING)

    @TypeConverter
    fun fromCallerCategory(value: CallerCategory): String = value.name

    @TypeConverter
    fun toCallerCategory(value: String): CallerCategory = runCatching { CallerCategory.valueOf(value) }.getOrDefault(CallerCategory.OTHER)

    @TypeConverter
    fun fromUrgencyLevel(value: UrgencyLevel): String = value.name

    @TypeConverter
    fun toUrgencyLevel(value: String): UrgencyLevel = runCatching { UrgencyLevel.valueOf(value) }.getOrDefault(UrgencyLevel.LOW)

    @TypeConverter
    fun fromRiskLevel(value: RiskLevel): String = value.name

    @TypeConverter
    fun toRiskLevel(value: String): RiskLevel = runCatching { RiskLevel.valueOf(value) }.getOrDefault(RiskLevel.NOT_ASSESSED)

    @TypeConverter
    fun fromHandlingStatus(value: HandlingStatus): String = value.name

    @TypeConverter
    fun toHandlingStatus(value: String): HandlingStatus = runCatching { HandlingStatus.valueOf(value) }.getOrDefault(HandlingStatus.USER_ANSWERED)

    @TypeConverter
    fun fromRuleConditionType(value: RuleConditionType): String = value.name

    @TypeConverter
    fun toRuleConditionType(value: String): RuleConditionType = runCatching { RuleConditionType.valueOf(value) }.getOrDefault(RuleConditionType.CALLER_CATEGORY)

    @TypeConverter
    fun fromRuleActionType(value: RuleActionType): String = value.name

    @TypeConverter
    fun toRuleActionType(value: String): RuleActionType = runCatching { RuleActionType.valueOf(value) }.getOrDefault(RuleActionType.ALWAYS_RING_USER)
}

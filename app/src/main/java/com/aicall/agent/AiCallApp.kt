package com.aicall.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.aicall.agent.ai.conversation.ConversationManager
import com.aicall.agent.ai.conversation.RuleBasedReasoningEngine
import com.aicall.agent.ai.policy.PolicyEnforcer
import com.aicall.agent.ai.policy.RiskAndUrgencyAssessor
import com.aicall.agent.ai.voice.AndroidTtsEngine
import com.aicall.agent.ai.voice.DefaultSpeechRecognizerEngine
import com.aicall.agent.data.db.AppDatabase
import com.aicall.agent.data.repository.AgentConfigRepository
import com.aicall.agent.data.repository.CallRepository
import com.aicall.agent.data.repository.CallerMemoryRepository
import com.aicall.agent.service.telecom.CallManager

class AiCallApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var callRepository: CallRepository
        private set

    lateinit var callerMemoryRepository: CallerMemoryRepository
        private set

    lateinit var agentConfigRepository: AgentConfigRepository
        private set

    lateinit var conversationManager: ConversationManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize local Room persistence
        database = AppDatabase.getDatabase(this)
        callRepository = CallRepository(database.callRecordDao(), database.actionItemDao(), database.auditLogDao())
        callerMemoryRepository = CallerMemoryRepository(database.callerProfileDao())
        agentConfigRepository = AgentConfigRepository(this, database.agentRuleDao(), database.agentScheduleDao())

        // 2. Initialize AI Engines & Policy Layer
        val speechRec = DefaultSpeechRecognizerEngine()
        val speechSyn = AndroidTtsEngine(this)
        val reasoning = RuleBasedReasoningEngine()
        val policy = PolicyEnforcer()
        val assessor = RiskAndUrgencyAssessor()

        conversationManager = ConversationManager(
            reasoningEngine = reasoning,
            policyEnforcer = policy,
            riskAssessor = assessor,
            callRepository = callRepository,
            callerMemoryRepository = callerMemoryRepository,
            speechRecognizer = speechRec,
            speechSynthesizer = speechSyn
        )

        // 3. Initialize Telecom CallManager
        CallManager.instance.initialize(this, callRepository)

        // 4. Create Notification Channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Active Phone Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows incoming and ongoing cellular phone calls"
            }

            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT_ALERTS,
                "Urgent AI Call Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority emergency and urgent alerts detected by the AI agent"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(urgentChannel)
        }
    }

    companion object {
        const val CHANNEL_CALLS = "channel_aicall_active"
        const val CHANNEL_URGENT_ALERTS = "channel_aicall_urgent"

        lateinit var instance: AiCallApp
            private set
    }
}

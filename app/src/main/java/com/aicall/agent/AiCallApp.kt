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
import kotlinx.coroutines.*

class AiCallApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var callRepository: CallRepository
        private set

    lateinit var callerMemoryRepository: CallerMemoryRepository
        private set

    lateinit var agentConfigRepository: AgentConfigRepository
        private set

    lateinit var knowledgeRepository: com.aicall.agent.data.repository.KnowledgeRepository
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
        knowledgeRepository = com.aicall.agent.data.repository.KnowledgeRepository(database.knowledgeDocumentDao())

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            knowledgeRepository.seedInitialKnowledgeIfEmpty(agentConfigRepository.approvedKnowledgeBase.value)
        }

        // 2. Initialize AI Engines & Policy Layer
        val whisperModelFile = java.io.File("/data/local/tmp/aicall/models/ggml-base-q8_0.bin")
        val altModelFile = java.io.File("/data/local/tmp/aicall/models/ggml-base.bin")
        val activeModelFile = when {
            whisperModelFile.exists() -> whisperModelFile
            altModelFile.exists() -> altModelFile
            else -> null
        }
        val whisperEngine = activeModelFile?.let {
            com.aicall.agent.ai.voice.WhisperSpeechRecognizerEngine(this, it.absolutePath)
        }
        val androidEngine = com.aicall.agent.ai.voice.AndroidSpeechRecognizerEngine(this)
        val speechRec: com.aicall.agent.ai.voice.SpeechRecognizerEngine = com.aicall.agent.ai.voice.HybridSpeechRecognizerEngine(
            context = this,
            whisperEngine = whisperEngine,
            androidSpeechEngine = androidEngine
        )
        val speechSyn = AndroidTtsEngine(this)

        val ragRetriever = com.aicall.agent.ai.rag.LocalRagRetriever(
            knowledgeDocumentDao = database.knowledgeDocumentDao(),
            callerProfileDao = database.callerProfileDao(),
            actionItemDao = database.actionItemDao()
        )

        val llamaModelFile = java.io.File("/data/local/tmp/aicall/models/tinyllama-1.1b-chat-q4_k_m.gguf")
        val conversationalEngine = com.aicall.agent.ai.conversation.EnglishConversationalReasoningEngine()
        val reasoning: com.aicall.agent.ai.conversation.LlmReasoningEngine = if (llamaModelFile.exists()) {
            com.aicall.agent.ai.conversation.EmbeddedLlamaEngine(llamaModelFile.absolutePath, conversationalEngine)
        } else {
            conversationalEngine
        }

        val policy = PolicyEnforcer()
        val assessor = RiskAndUrgencyAssessor()

        val audioStorage = com.aicall.agent.ai.voice.AudioStorageManager(this)
        conversationManager = ConversationManager(
            reasoningEngine = reasoning,
            policyEnforcer = policy,
            riskAssessor = assessor,
            callRepository = callRepository,
            callerMemoryRepository = callerMemoryRepository,
            speechRecognizer = speechRec,
            speechSynthesizer = speechSyn,
            audioStorageManager = audioStorage,
            localRagRetriever = ragRetriever
        )

        // 3. Initialize Telecom CallManager
        CallManager.instance.initialize(this, callRepository, conversationManager, agentConfigRepository)

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

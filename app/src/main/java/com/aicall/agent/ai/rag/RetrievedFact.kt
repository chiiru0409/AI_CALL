package com.aicall.agent.ai.rag

data class RetrievedFact(
    val id: Long,
    val title: String,
    val content: String,
    val source: String,
    val category: String,
    val confidence: Float,
    val provenance: String,
    val isCallerSpecific: Boolean,
    val timestamp: Long
)

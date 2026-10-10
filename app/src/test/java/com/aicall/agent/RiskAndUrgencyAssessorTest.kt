package com.aicall.agent

import com.aicall.agent.ai.policy.RiskAndUrgencyAssessor
import com.aicall.agent.data.model.CallerCategory
import com.aicall.agent.data.model.RiskLevel
import com.aicall.agent.data.model.UrgencyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RiskAndUrgencyAssessorTest {

    private lateinit var assessor: RiskAndUrgencyAssessor

    @Before
    fun setUp() {
        assessor = RiskAndUrgencyAssessor()
    }

    @Test
    fun testEmergencyHospitalCallDetected() {
        val transcript = "Caller: Hello, there is an emergency at the hospital. Please call back immediately."
        val result = assessor.assessConversation(
            callerNumber = "+919876543210",
            isContact = true,
            transcript = transcript
        )
        assertEquals(UrgencyLevel.EMERGENCY, result.urgency)
        assertTrue(result.shouldEscalate)
    }

    @Test
    fun testMultilingualUrgencyDetected() {
        val transcript = "Caller: Twaraga call cheyyandi chala urgent situation."
        val result = assessor.assessConversation(
            callerNumber = "+919876543210",
            isContact = false,
            transcript = transcript
        )
        assertEquals(UrgencyLevel.EMERGENCY, result.urgency)
    }

    @Test
    fun testFraudSuspectedDetected() {
        val transcript = "Caller: Your bank account KYC expired. Share OTP or install AnyDesk to verify."
        val result = assessor.assessConversation(
            callerNumber = "+919876543210",
            isContact = false,
            transcript = transcript
        )
        assertEquals(RiskLevel.HIGH_RISK_FRAUD, result.risk)
        assertEquals(CallerCategory.FRAUD_SUSPECTED, result.category)
    }

    @Test
    fun testDeliveryClassification() {
        val transcript = "Caller: Amazon parcel delivery has arrived."
        val result = assessor.assessConversation(
            callerNumber = "+919876543210",
            isContact = false,
            transcript = transcript
        )
        assertEquals(CallerCategory.DELIVERY, result.category)
        assertEquals(UrgencyLevel.LOW, result.urgency)
    }
}

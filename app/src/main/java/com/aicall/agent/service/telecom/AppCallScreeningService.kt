package com.aicall.agent.service.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import com.aicall.agent.data.util.PhoneNumberUtils

class AppCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: ""

        // Critical Safety: Emergency numbers and emergency callbacks must NEVER be blocked
        if (PhoneNumberUtils.isEmergencyNumber(phoneNumber) ||
            callDetails.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE) ||
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            val passThroughResponse = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
            respondToCall(callDetails, passThroughResponse)
            return
        }

        // Spam classification heuristic
        val isSpam = phoneNumber.startsWith("+1800") || phoneNumber.contains("0000000000")

        val response = CallResponse.Builder()
            .setDisallowCall(isSpam)
            .setRejectCall(isSpam)
            .setSkipCallLog(false)
            .setSkipNotification(isSpam)
            .build()

        respondToCall(callDetails, response)
    }
}

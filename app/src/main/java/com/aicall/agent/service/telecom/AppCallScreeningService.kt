package com.aicall.agent.service.telecom

import android.telecom.Call
import android.telecom.CallScreeningService

class AppCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val isSpam = phoneNumber.startsWith("+1800") || phoneNumber.contains("0000")

        val response = CallResponse.Builder()
            .setDisallowCall(isSpam)
            .setRejectCall(isSpam)
            .setSkipCallLog(false)
            .setSkipNotification(isSpam)
            .build()

        respondToCall(callDetails, response)
    }
}

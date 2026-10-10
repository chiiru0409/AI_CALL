package com.aicall.agent.service.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.aicall.agent.ui.incall.IncomingCallActivity
import com.aicall.agent.ui.incall.OngoingCallActivity

class AppInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.instance.onCallAdded(call)

        // Launch appropriate in-call or incoming UI activity based on call state
        if (call.state == Call.STATE_RINGING) {
            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, OngoingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.instance.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.instance.onCallAudioStateChanged(audioState)
    }
}

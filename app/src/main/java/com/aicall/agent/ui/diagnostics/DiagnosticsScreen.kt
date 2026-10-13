package com.aicall.agent.ui.diagnostics

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.*

enum class FeasibilityStatus(val label: String, val color: androidx.compose.ui.graphics.Color) {
    IMPLEMENTED_AND_TESTED("Implemented & Tested", CallAnswerGreen),
    SUPPORTED_BY_API_UNTESTED("API Ready to Test", TealAccent),
    PARTIALLY_SUPPORTED("Partially Supported", WarningAmber),
    UNSUPPORTED_ON_DEVICE("Unsupported on Config", DangerRed),
    REQUIRES_INVESTIGATION("Under Investigation", AccentPurple)
}

data class TelephonyFeasibilityItem(
    val title: String,
    val description: String,
    val status: FeasibilityStatus,
    val technicalDetails: String
)

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true

    val hasDndPolicyAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        notificationManager?.isNotificationPolicyAccessGranted == true
    } else true

    val feasibilityItems = remember(isIgnoringBatteryOptimizations, hasDndPolicyAccess) {
        listOf(
            TelephonyFeasibilityItem(
                title = "Default Phone App & InCallService",
                description = "Custom dialer UI, answering, declining, muting, speakerphone, DTMF tones.",
                status = FeasibilityStatus.IMPLEMENTED_AND_TESTED,
                technicalDetails = "Uses Android Telecom InCallService with IN_CALL_SERVICE_UI meta-data."
            ),
            TelephonyFeasibilityItem(
                title = "Call Screening Service & Spam Gating",
                description = "Pre-answer evaluation and caller categorization.",
                status = FeasibilityStatus.IMPLEMENTED_AND_TESTED,
                technicalDetails = "CallScreeningService registered with BIND_SCREENING_SERVICE."
            ),
            TelephonyFeasibilityItem(
                title = "Automated Call Screening & Handoff",
                description = "Direct routing to AI Agent or human takeover during active call.",
                status = FeasibilityStatus.IMPLEMENTED_AND_TESTED,
                technicalDetails = "Telecom Call.answer(STATE_AUDIO_ONLY) and state machine handoff."
            ),
            TelephonyFeasibilityItem(
                title = "Audio Handling: Screen and Notify Mode",
                description = "Safe autonomous pre-screening, transcription logging, and emergency notification.",
                status = FeasibilityStatus.IMPLEMENTED_AND_TESTED,
                technicalDetails = "Standard verified mode. Full duplex baseband injection restricted by Android OS sandbox for non-system apps."
            ),
            TelephonyFeasibilityItem(
                title = "Two-Way Cellular Audio Injection (Full Duplex)",
                description = "Baseband audio injection vs degraded loudspeaker loopback.",
                status = FeasibilityStatus.UNSUPPORTED_ON_DEVICE,
                technicalDetails = "Direct cellular downlink/uplink injection restricted on stock Android (CAPTURE_AUDIO_OUTPUT restricted). Loudspeaker acoustic loopback is eliminated by MediaTek hardware AEC. Two-way audible communication requires an external Bluetooth HFP bridge or carrier forwarding."
            ),
            TelephonyFeasibilityItem(
                title = "On-Device Data Flow & Zero-Cloud Privacy",
                description = "Local rule reasoning engine and offline SQLite Room database.",
                status = FeasibilityStatus.IMPLEMENTED_AND_TESTED,
                technicalDetails = "100% on-device execution. Unencrypted cloud database backup disabled in data extraction rules."
            ),
            TelephonyFeasibilityItem(
                title = "Urgent Notifications & DND Override",
                description = "High priority emergency notifications under silent mode.",
                status = if (hasDndPolicyAccess) FeasibilityStatus.IMPLEMENTED_AND_TESTED else FeasibilityStatus.PARTIALLY_SUPPORTED,
                technicalDetails = "NotificationChannel with IMPORTANCE_HIGH and ACCESS_NOTIFICATION_POLICY."
            ),
            TelephonyFeasibilityItem(
                title = "Screen-Off & Battery Reliability",
                description = "Background execution and incoming call wake up when device is sleeping.",
                status = if (isIgnoringBatteryOptimizations) FeasibilityStatus.IMPLEMENTED_AND_TESTED else FeasibilityStatus.PARTIALLY_SUPPORTED,
                technicalDetails = "WAKE_LOCK, USE_FULL_SCREEN_INTENT, and battery optimization whitelist."
            ),
            TelephonyFeasibilityItem(
                title = "Cellular Call Recording vs Transcripts",
                description = "Dual-side raw audio recording of cellular calls.",
                status = FeasibilityStatus.PARTIALLY_SUPPORTED,
                technicalDetails = "Raw dual-channel audio restricted on Android 10+; safely persists structured speaker-labeled transcripts in SQLite."
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Telephony Feasibility & Diagnostics",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary
                )
                Text(
                    text = "Real-device capability matrix and system telemetry verification",
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Live System Telemetry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Device Telemetry Status",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TealAccent
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TelemetryRow(
                        label = "Android Version",
                        value = "API ${Build.VERSION.SDK_INT} (Target Android 13)"
                    )
                    HorizontalDivider(color = DarkElevatedSurface, modifier = Modifier.padding(vertical = 8.dp))
                    TelemetryRow(
                        label = "Battery Whitelist",
                        value = if (isIgnoringBatteryOptimizations) "Whitelisted (Optimal)" else "Restricted (May throttle)"
                    )
                    HorizontalDivider(color = DarkElevatedSurface, modifier = Modifier.padding(vertical = 8.dp))
                    TelemetryRow(
                        label = "DND Override Access",
                        value = if (hasDndPolicyAccess) "Granted" else "Action Required"
                    )
                }
            }
        }

        // Feasibility Matrix
        item {
            Text(
                text = "Telecom Capabilities Gate",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary
            )
        }

        items(feasibilityItems) { item ->
            FeasibilityCard(item = item)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = DarkTextSecondary, fontSize = 13.sp)
        Text(text = value, color = DarkTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FeasibilityCard(item: TelephonyFeasibilityItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = item.status.color.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, item.status.color.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = item.status.label,
                        color = item.status.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.description,
                color = DarkTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkElevatedSurface
            ) {
                Text(
                    text = "Technical: ${item.technicalDetails}",
                    color = DarkTextSecondary.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

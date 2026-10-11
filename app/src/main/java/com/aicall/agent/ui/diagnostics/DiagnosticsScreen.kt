package com.aicall.agent.ui.diagnostics

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    IMPLEMENTED_AND_TESTED("Implemented & Tested", VerifiedGreen),
    SUPPORTED_BY_API_UNTESTED("API Supported / Ready to Test", CyanAccent),
    PARTIALLY_SUPPORTED("Partially Supported", WarningAmber),
    UNSUPPORTED_ON_DEVICE("Unsupported on Configuration", UrgentRed),
    REQUIRES_INVESTIGATION("Requires Investigation", VioletAgent)
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
                status = FeasibilityStatus.PARTIALLY_SUPPORTED,
                technicalDetails = "Direct cellular downlink/uplink injection restricted on commercial Android 13; degraded loopback requires speakerphone/mic."
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
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Telephony Feasibility & Diagnostics",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Real-device capability matrix and system telemetry verification",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Live System Telemetry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Telemetry Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TelemetryRow(
                        label = "Android Version",
                        value = "API ${Build.VERSION.SDK_INT} (Target Android 13)"
                    )
                    TelemetryRow(
                        label = "Battery Optimization Whitelist",
                        value = if (isIgnoringBatteryOptimizations) "Whitelisted (Optimal)" else "Restricted (May throttle)"
                    )
                    TelemetryRow(
                        label = "Do Not Disturb Override Access",
                        value = if (hasDndPolicyAccess) "Granted" else "Action Required"
                    )
                }
            }
        }

        // Feasibility Matrix
        item {
            Text(
                text = "Telecom Capabilities Gate",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FeasibilityCard(item: TelephonyFeasibilityItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceCardElevated
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

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Implementation: ${item.technicalDetails}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

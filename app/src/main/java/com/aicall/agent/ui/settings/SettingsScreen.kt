package com.aicall.agent.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.db.AppDatabase
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)
    } else null

    val isDefaultDialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: true

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

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
                    text = "Application Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Permissions, security policies, data retention, and privacy controls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Default Phone App Role Section
        item {
            SettingsCategoryHeader(title = "Telephony Integration")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Phone App Role",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isDefaultDialer) "Enabled: App acts as primary cellular dialer" else "Disabled: Click to grant role",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDefaultDialer) VerifiedGreen else WarningAmber,
                                fontSize = 12.sp
                            )
                        }
                        if (!isDefaultDialer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Button(
                                onClick = {
                                    val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                                    if (intent != null) roleLauncher.launch(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Text("Set Default", color = BackgroundDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Security & Privacy
        item {
            SettingsCategoryHeader(title = "Security & Privacy")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsRow(
                        icon = Icons.Default.Lock,
                        title = "Android Keystore Encryption",
                        subtitle = "Local records and API keys protected using hardware-backed keystore"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = SurfaceCardBorder)
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsRow(
                        icon = Icons.Default.Security,
                        title = "Deterministic Policy Enforcement",
                        subtitle = "Guarantees OTPs and confidential credentials are never spoken or leaked"
                    )
                }
            }
        }

        // Notifications & System
        item {
            SettingsCategoryHeader(title = "Notifications & Power")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsActionRow(
                        icon = Icons.Default.Notifications,
                        title = "Notification Channels",
                        subtitle = "Configure Active Calls & Urgent AI Alerts",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
        }

        // Data Management
        item {
            SettingsCategoryHeader(title = "Data Management")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsActionRow(
                        icon = Icons.Default.DeleteSweep,
                        title = "Clear All Call Intelligence History",
                        subtitle = "Permanently remove all local call records, transcripts, and logs",
                        isDestructive = true,
                        onClick = { showClearDialog = true }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Call Records?") },
            text = { Text("This will permanently delete all stored call intelligence records, transcripts, and action items.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            AppDatabase.getDatabase(context).callRecordDao().clearAll()
                        }
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = UrgentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = CyanAccent
    )
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontSize = 14.sp)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) UrgentRed else CyanAccent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDestructive) UrgentRed else TextPrimary,
                    fontSize = 14.sp
                )
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
    }
}

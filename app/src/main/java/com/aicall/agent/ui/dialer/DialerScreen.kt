package com.aicall.agent.ui.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.util.PhoneNumberUtils
import com.aicall.agent.ui.components.CallHistoryRow
import com.aicall.agent.ui.components.ContactAvatar
import com.aicall.agent.ui.components.GoogleSearchBar
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class DeviceContact(
    val name: String,
    val phoneNumber: String,
    val type: String = "Mobile"
)

/**
 * Google Phone-inspired Dialer Screen featuring pill search, horizontal favorites,
 * recents list, and a collapsible tactile dialpad with FAB toggle.
 */
@Composable
fun DialerScreen(
    recentCallsFlow: Flow<List<CallRecord>>,
    onCallInitiated: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var inputNumber by remember { mutableStateOf("") }
    var isDialpadOpen by remember { mutableStateOf(false) }

    val recentCalls by recentCallsFlow.collectAsState(initial = emptyList())
    var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }

    // Load actual device contacts when permission is present
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val contacts = loadDeviceContacts(context)
            withContext(Dispatchers.Main) {
                deviceContacts = contacts
            }
        }
    }

    // Role Manager check for default dialer banner
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)
    } else null
    val isDefaultDialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: true
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    // Filter recent calls based on search bar query
    val filteredRecentCalls = remember(recentCalls, searchQuery) {
        if (searchQuery.isBlank()) {
            recentCalls
        } else {
            recentCalls.filter { call ->
                call.callerNumber.contains(searchQuery, ignoreCase = true) ||
                        (call.callerName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    // Derive favorites: top device contacts or frequent verified recent callers
    val favorites = remember(deviceContacts, recentCalls) {
        if (deviceContacts.isNotEmpty()) {
            deviceContacts.take(8)
        } else {
            recentCalls.filter { !it.callerName.isNullOrBlank() }
                .distinctBy { it.callerNumber }
                .take(6)
                .map { DeviceContact(it.callerName!!, it.callerNumber, "Recent") }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Google Phone Pill Search Bar
            GoogleSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = "Search contacts & places..."
            )

            // Default Dialer Role Prompt Banner (if not already default)
            if (!isDefaultDialer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Set as default phone app",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Enables AI auto-screening and telecom dialer handling.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                                if (intent != null) roleLauncher.launch(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Set", color = BackgroundDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Main Content List: Favorites row + Recents list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 14.dp, bottom = if (isDialpadOpen) 380.dp else 90.dp)
            ) {
                // Favorites Section (Google Phone Style horizontal avatars)
                if (searchQuery.isBlank() && favorites.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Favorites",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(favorites) { contact ->
                                FavoriteContactItem(
                                    contact = contact,
                                    onClick = { onCallInitiated(contact.phoneNumber) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Section Header: Recents
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "Recent calls" else "Search results",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp, top = 2.dp)
                    )
                }

                // Recents Call List
                if (filteredRecentCalls.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isBlank()) "No recent calls" else "No matching calls found",
                                    color = TextSecondary,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Cellular calls screened by AI will appear here",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filteredRecentCalls) { call ->
                        CallHistoryRow(
                            call = call,
                            onClick = { onCallInitiated(call.callerNumber) },
                            onCallClick = { onCallInitiated(call.callerNumber) }
                        )
                    }
                }
            }
        }

        // Floating Action Button (FAB) to toggle Dialpad (when dialpad is closed)
        if (!isDialpadOpen) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isDialpadOpen = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .size(62.dp),
                shape = RoundedCornerShape(18.dp),
                containerColor = AccentPurple,
                contentColor = BackgroundDark
            ) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = "Open dialpad",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Collapsible Dialpad Sheet (Google Phone Style Bottom Slide-Up)
        AnimatedVisibility(
            visible = isDialpadOpen,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = SurfaceDark,
                tonalElevation = 12.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pull / dismiss handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TextMuted.copy(alpha = 0.5f))
                            .clickable { isDialpadOpen = false }
                    )

                    // Dialed Number Display Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp, start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (inputNumber.isEmpty()) "" else PhoneNumberUtils.normalize(inputNumber),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Backspace Button with click (single delete) & long-press (clear all)
                        if (inputNumber.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                inputNumber = inputNumber.dropLast(1)
                                            },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                inputNumber = ""
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Tactile Dialpad Grid
                    Dialpad(
                        onDigitClicked = { digit -> inputNumber += digit },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Call & Dismiss Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 24.dp, end = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dismiss Keypad Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isDialpadOpen = false
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardHide,
                                contentDescription = "Hide dialpad",
                                tint = TextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Large Green Call Button
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(CallActionGreen)
                                .clickable {
                                    if (inputNumber.isNotBlank()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCallInitiated(inputNumber)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Empty spacer for symmetric alignment
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

/**
 * Circular Favorite Contact Item matching Google Phone's Favorites row
 */
@Composable
fun FavoriteContactItem(
    contact: DeviceContact,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable { onClick() }
    ) {
        ContactAvatar(
            name = contact.name,
            phoneNumber = contact.phoneNumber,
            size = 58.dp,
            showPhoneBadge = true
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = contact.name,
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = contact.type,
            fontSize = 10.sp,
            color = TextMuted,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

private fun loadDeviceContacts(context: Context): List<DeviceContact> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val contacts = mutableListOf<DeviceContact>()
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE
    )

    try {
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val seenNumbers = mutableSetOf<String>()

            while (cursor.moveToNext() && contacts.size < 12) {
                val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                val number = if (numberIdx != -1) cursor.getString(numberIdx) else null
                val typeInt = if (typeIdx != -1) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                val type = when (typeInt) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                    else -> "Mobile"
                }

                if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                    val cleanNum = number.replace(Regex("[^0-9+]"), "")
                    if (!seenNumbers.contains(cleanNum)) {
                        seenNumbers.add(cleanNum)
                        contacts.add(DeviceContact(name, number, type))
                    }
                }
            }
        }
    } catch (_: Exception) {}

    return contacts
}

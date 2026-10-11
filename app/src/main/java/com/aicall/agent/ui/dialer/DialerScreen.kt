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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.ui.components.HeaderSearchBar
import com.aicall.agent.ui.components.RecentCallRow
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceContact(
    val name: String,
    val phoneNumber: String,
    val type: String = "Mobile"
)

/**
 * Secure Dialer-adapted Main Dialer Screen with Header Search Bar,
 * Sub-pager (Recents & Contacts), and tactile T9 squircle Dialpad sheet.
 */
@Composable
fun DialerScreen(
    recentCallsFlow: Flow<List<CallRecord>>,
    onCallInitiated: (String) -> Unit,
    onCallRecordDetailClicked: ((CallRecord) -> Unit)? = null,
    onSettingsClicked: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var dialpadInput by remember { mutableStateOf("") }
    var isDialpadVisible by remember { mutableStateOf(false) }

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

    // Filter recent calls
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

    // Filter contacts
    val filteredContacts = remember(deviceContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            deviceContacts
        } else {
            deviceContacts.filter { contact ->
                contact.name.contains(searchQuery, ignoreCase = true) ||
                        contact.phoneNumber.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val favorites = remember(deviceContacts, recentCalls) {
        if (deviceContacts.isNotEmpty()) {
            deviceContacts.take(6)
        } else {
            recentCalls.filter { !it.callerName.isNullOrBlank() }
                .distinctBy { it.callerNumber }
                .take(6)
                .map { DeviceContact(it.callerName!!, it.callerNumber, "Recent") }
        }
    }

    val tabs = listOf("Recents", "Contacts")
    val pagerState = rememberPagerState { tabs.size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(8.dp))

            // Secure Dialer Pill Search Bar
            HeaderSearchBar(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                onSettingsClick = { onSettingsClicked?.invoke() }
            )

            // Default Dialer Banner
            if (!isDefaultDialer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = AppShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
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
                                text = "Set as default dialer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Enables native cellular call screening & Telecom routing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = {
                                val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                                if (intent != null) roleLauncher.launch(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                            shape = AppShapes.Small,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Set", color = BackgroundDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Tab Selector Row
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = BackgroundDark,
                contentColor = AccentPurple,
                divider = { HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.3f), thickness = 0.8.dp) },
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        height = 3.dp,
                        color = AccentPurple
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (pagerState.currentPage == index) AccentPurple else TextSecondary
                            )
                        }
                    )
                }
            }

            // Pager Content: Recents & Contacts
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        // RECENTS TAB
                        if (filteredRecentCalls.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isBlank()) "No recent call history" else "No matching calls found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 10.dp,
                                    bottom = if (isDialpadVisible) 420.dp else 96.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = filteredRecentCalls,
                                    key = { it.sessionId }
                                ) { callRecord ->
                                    RecentCallRow(
                                        call = callRecord,
                                        onCallClick = { onCallInitiated(callRecord.callerNumber) },
                                        onDetailsClick = { onCallRecordDetailClicked?.invoke(callRecord) }
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // CONTACTS TAB
                        ContactsTab(
                            contacts = filteredContacts,
                            favorites = favorites,
                            onContactClick = { contact -> onCallInitiated(contact.phoneNumber) }
                        )
                    }
                }
            }
        }

        // Tactile T9 Dialpad Sheet (Sliding from bottom)
        AnimatedVisibility(
            visible = isDialpadVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(220)
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(180)
            ) + fadeOut(animationSpec = tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = AppShapes.BottomSheet,
                color = SurfaceDark,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TextMuted.copy(alpha = 0.5f))
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { isDialpadVisible = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Hide Dialpad",
                                tint = TextSecondary
                            )
                        }
                    }

                    Dialpad(
                        inputValue = dialpadInput,
                        onValueChange = { dialpadInput = it },
                        onCallClick = { number ->
                            isDialpadVisible = false
                            onCallInitiated(number)
                            dialpadInput = ""
                        }
                    )
                }
            }
        }

        // Floating Dialpad Action Button (when Dialpad is hidden)
        if (!isDialpadVisible) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isDialpadVisible = true
                },
                containerColor = AccentPurple,
                contentColor = BackgroundDark,
                shape = AppShapes.Keypad,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
                    .size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = "Open Dialpad",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun loadDeviceContacts(context: Context): List<DeviceContact> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }

    val contactsList = mutableListOf<DeviceContact>()
    val seenNumbers = mutableSetOf<String>()

    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE
    )

    try {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
        )

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: "Unknown"
                val number = it.getString(numIdx)?.replace(Regex("[^0-9+]"), "") ?: ""
                val typeInt = if (typeIdx >= 0) it.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE

                val typeStr = when (typeInt) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                    else -> "Mobile"
                }

                if (number.isNotBlank() && seenNumbers.add(number)) {
                    contactsList.add(DeviceContact(name, number, typeStr))
                }
            }
        }
    } catch (_: Exception) {}

    return contactsList
}

package com.example.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.data.GeneratedRadioShow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import com.example.api.SupabaseSessionDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AuraViewModel) {
    val activeScreen by viewModel.activeScreen.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 840 // Adaptive boundary

    AuraTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isWideScreen) {
                // Side-by-side tablet / desktop split view
                val isSidebarExpanded by viewModel.isSidebarExpanded.collectAsStateWithLifecycle()
                val sidebarWidth by animateDpAsState(
                    targetValue = if (isSidebarExpanded) 300.dp else 0.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "SidebarWidth"
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .clipToBounds()
                    ) {
                        Box(modifier = Modifier.width(300.dp)) {
                            SidebarContent(viewModel = viewModel, isDrawer = false, onCloseDrawer = {})
                        }
                    }
                    if (sidebarWidth > 0.dp) {
                        Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = Color(0x15FFFFFF))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        WorkspaceContent(viewModel = viewModel, isWideScreen = true, onOpenMenu = {})
                    }
                }
            } else {
                // Mobile layout with slide-out drawer
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = BackgroundDeepSpace,
                            modifier = Modifier.width(300.dp)
                        ) {
                            SidebarContent(
                                viewModel = viewModel,
                                isDrawer = true,
                                onCloseDrawer = {
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    WorkspaceContent(
                        viewModel = viewModel,
                        isWideScreen = false,
                        onOpenMenu = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AuraTheme(themeMode: String, content: @Composable () -> Unit) {
    MyApplicationTheme(themeMode = themeMode, dynamicColor = false, content = content)
}

// --- Responsive Sidebar Navigation ---
@Composable
fun SidebarContent(
    viewModel: AuraViewModel,
    isDrawer: Boolean,
    onCloseDrawer: () -> Unit
) {
    val activeScreen by viewModel.activeScreen.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val supabaseSessions by viewModel.supabaseSessions.collectAsStateWithLifecycle()
    val isFetchingSupabaseSessions by viewModel.isFetchingSupabaseSessions.collectAsStateWithLifecycle()
    val supabaseFetchError by viewModel.supabaseFetchError.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeepSpace)
            .padding(16.dp)
    ) {
        // App Identity Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryNeonViolet, SecondaryOrchid)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Aura AI Logo",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "AURA AI",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = TextPrimaryWhite,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "PRO EDITION",
                    fontSize = 10.sp,
                    color = TertiaryCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // New Chat Button
        Button(
            onClick = {
                viewModel.createNewChat()
                viewModel.activeScreen.value = Screen.Chat
                if (isDrawer) onCloseDrawer()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(PrimaryNeonViolet, SecondaryOrchid)
                        )
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, "New Chat", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Chat", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Group
        Text("WORKSPACE", fontSize = 11.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        SidebarNavItem(
            title = "Aura Chat",
            icon = Icons.Default.ChatBubbleOutline,
            isActive = activeScreen == Screen.Chat,
            onClick = {
                viewModel.activeScreen.value = Screen.Chat
                if (isDrawer) onCloseDrawer()
            }
        )
        SidebarNavItem(
            title = "Imagen Photos",
            icon = Icons.Default.Image,
            isActive = activeScreen == Screen.ImageGen,
            onClick = {
                viewModel.activeScreen.value = Screen.ImageGen
                if (isDrawer) onCloseDrawer()
            }
        )
        SidebarNavItem(
            title = "Video Studio",
            icon = Icons.Default.Videocam,
            isActive = activeScreen == Screen.VideoGen,
            onClick = {
                viewModel.activeScreen.value = Screen.VideoGen
                if (isDrawer) onCloseDrawer()
            }
        )
        SidebarNavItem(
            title = "Antigravity Radio",
            icon = Icons.Default.Headphones,
            isActive = activeScreen == Screen.RadioShow,
            onClick = {
                viewModel.activeScreen.value = Screen.RadioShow
                if (isDrawer) onCloseDrawer()
            }
        )
        SidebarNavItem(
            title = "Saved Prompts",
            icon = Icons.Default.BookmarkBorder,
            isActive = activeScreen == Screen.SavedPrompts,
            onClick = {
                viewModel.activeScreen.value = Screen.SavedPrompts
                if (isDrawer) onCloseDrawer()
            }
        )
        SidebarNavItem(
            title = "Stats Dashboard",
            icon = Icons.Default.BarChart,
            isActive = activeScreen == Screen.Dashboard,
            onClick = {
                viewModel.activeScreen.value = Screen.Dashboard
                if (isDrawer) onCloseDrawer()
            }
        )
        SidebarNavItem(
            title = "Platform Settings",
            icon = Icons.Default.Settings,
            isActive = activeScreen == Screen.Settings,
            onClick = {
                viewModel.activeScreen.value = Screen.Settings
                if (isDrawer) onCloseDrawer()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("Search local chats...", color = TextSecondarySlate, fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextSecondarySlate,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { viewModel.searchQuery.value = "" },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = TextSecondarySlate,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SurfaceObsidian,
                focusedBorderColor = SecondaryOrchid,
                unfocusedContainerColor = SurfaceObsidian,
                focusedContainerColor = SurfaceObsidian,
                focusedTextColor = TextPrimaryWhite,
                unfocusedTextColor = TextPrimaryWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = "LOCAL CHATS",
                    fontSize = 11.sp,
                    color = TextSecondarySlate,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        text = "No local chats. Start a new chat above!",
                        color = Color(0x60FFFFFF),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            } else {
                items(sessions) { session ->
                    ChatHistoryItem(
                        session = session,
                        isActive = session.id == activeSessionId,
                        onClick = {
                            viewModel.selectSession(session.id)
                            viewModel.activeScreen.value = Screen.Chat
                            if (isDrawer) onCloseDrawer()
                        },
                        onDelete = { viewModel.deleteSession(session) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SUPABASE CLOUD",
                        fontSize = 11.sp,
                        color = TextSecondarySlate,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = { viewModel.fetchSupabaseSessions() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Cloud Sessions",
                            tint = SecondaryOrchid,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (isFetchingSupabaseSessions) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = SecondaryOrchid,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else if (supabaseFetchError != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = supabaseFetchError ?: "An error occurred",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Tap to retry",
                            color = SecondaryOrchid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.fetchSupabaseSessions() }
                        )
                    }
                }
            } else if (supabaseSessions.isEmpty()) {
                item {
                    Text(
                        text = "No cloud sessions loaded. Sync in Platform Settings to backup first, then tap refresh above.",
                        color = Color(0x50FFFFFF),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            } else {
                items(supabaseSessions) { sessionDto ->
                    val isSessionActive = sessionDto.id == activeSessionId
                    SupabaseHistoryItem(
                        session = sessionDto,
                        isActive = isSessionActive,
                        onClick = {
                            viewModel.selectSupabaseSession(sessionDto)
                            if (isDrawer) onCloseDrawer()
                        }
                    )
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x15FFFFFF))

        // Profile & Sync Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryNeonViolet.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.take(1).uppercase(),
                    color = PrimaryNeonViolet,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = userName, color = TextPrimaryWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = userEmail, color = TextSecondarySlate, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(
                onClick = { viewModel.triggerCloudSync() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (syncStatus == SyncState.Syncing) Icons.Default.Sync else Icons.Default.CloudQueue,
                    contentDescription = "Sync database",
                    tint = if (syncStatus == SyncState.Synced) AccentSuccessGreen else PrimaryNeonViolet,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SidebarNavItem(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0x158B5CF6) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isActive) PrimaryNeonViolet else TextSecondarySlate,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = if (isActive) TextPrimaryWhite else TextSecondarySlate,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ChatHistoryItem(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) Color(0x10FFFFFF) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "ChatHistoryItemBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isActive) PrimaryNeonViolet else TextSecondarySlate,
        animationSpec = tween(durationMillis = 200),
        label = "ChatHistoryItemIcon"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) TextPrimaryWhite else TextSecondarySlate,
        animationSpec = tween(durationMillis = 200),
        label = "ChatHistoryItemText"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = session.title,
                color = textColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete chat",
                tint = Color(0x60FFFFFF),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun SupabaseHistoryItem(
    session: SupabaseSessionDto,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) Color(0x158B5CF6) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "SupabaseHistoryItemBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isActive) SecondaryOrchid else Color(0xFF10B981),
        animationSpec = tween(durationMillis = 200),
        label = "SupabaseHistoryItemIcon"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) TextPrimaryWhite else TextSecondarySlate,
        animationSpec = tween(durationMillis = 200),
        label = "SupabaseHistoryItemText"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudQueue,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = session.title,
            color = textColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0x30FFFFFF),
            modifier = Modifier.size(14.dp)
        )
    }
}

// --- Active Workspace Router ---
@Composable
fun WorkspaceContent(
    viewModel: AuraViewModel,
    isWideScreen: Boolean,
    onOpenMenu: () -> Unit
) {
    val activeScreen by viewModel.activeScreen.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeepSpace)
    ) {
        // Shared Screen Header
        WorkspaceHeader(
            viewModel = viewModel,
            title = when (activeScreen) {
                Screen.Chat -> "Aura Intelligence Chat"
                Screen.ImageGen -> "Imagen Creative Photos"
                Screen.VideoGen -> "Video Generation Studio"
                Screen.RadioShow -> "Antigravity Radio Generator"
                Screen.SavedPrompts -> "Aura Prompt Library"
                Screen.Dashboard -> "Enterprise Statistics"
                Screen.Settings -> "Platform settings"
            },
            isWideScreen = isWideScreen,
            onOpenMenu = onOpenMenu,
            activeScreen = activeScreen
        )

        Divider(modifier = Modifier.fillMaxWidth().height(1.dp), color = Color(0x10FFFFFF))

        // Inner Workspace
        Box(modifier = Modifier.weight(1f)) {
            when (activeScreen) {
                Screen.Chat -> ChatPane(viewModel = viewModel)
                Screen.ImageGen -> ImageGenPane(viewModel = viewModel)
                Screen.VideoGen -> VideoGenPane(viewModel = viewModel)
                Screen.RadioShow -> RadioShowPane(viewModel = viewModel)
                Screen.SavedPrompts -> SavedPromptsPane(viewModel = viewModel)
                Screen.Dashboard -> DashboardPane(viewModel = viewModel)
                Screen.Settings -> SettingsPane(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun WorkspaceHeader(
    viewModel: AuraViewModel,
    title: String,
    isWideScreen: Boolean,
    onOpenMenu: () -> Unit,
    activeScreen: Screen
) {
    val isSimulated by viewModel.isSimulatorMode.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isWideScreen) {
            val isSidebarExpanded by viewModel.isSidebarExpanded.collectAsStateWithLifecycle()
            IconButton(onClick = { viewModel.isSidebarExpanded.value = !isSidebarExpanded }) {
                Icon(
                    imageVector = if (isSidebarExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                    contentDescription = "Toggle Sidebar",
                    tint = TextPrimaryWhite
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Default.Menu, "Menu", tint = TextPrimaryWhite)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryWhite,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.weight(1f)
        )
        if (isSimulated) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0x15FFB300))
                    .border(1.dp, Color(0x60FFB300), RoundedCornerShape(50.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Simulated Mode",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SIMULATED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFB300)
                    )
                }
            }
        }
        if (activeScreen == Screen.Chat) {
            Spacer(modifier = Modifier.width(8.dp))
            val context = LocalContext.current
            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                uri?.let { viewModel.exportChatHistory(context, it) }
            }
            IconButton(onClick = { exportLauncher.launch("chat_history.txt") }) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Export Chat History",
                    tint = TextPrimaryWhite
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { viewModel.clearCurrentConversation() }) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Conversation",
                    tint = TextPrimaryWhite
                )
            }
        }
    }
}

// ==========================================
// SCREEN 1: CORE CHAT WORKSPACE
// ==========================================
@Composable
fun ChatPane(viewModel: AuraViewModel) {
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val currentInput by viewModel.currentChatInput.collectAsStateWithLifecycle()
    val selectedAttachment by viewModel.selectedAttachment.collectAsStateWithLifecycle()
    val isVoiceActive by viewModel.isVoiceInputActive.collectAsStateWithLifecycle()
    val isSimulated by viewModel.isSimulatorMode.collectAsStateWithLifecycle()
    val isImageMode by viewModel.isImageGenerationMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (spokenText != null) {
                viewModel.currentChatInput.value += " " + spokenText
                viewModel.currentChatInput.value = viewModel.currentChatInput.value.trim()
            }
        }
        viewModel.isVoiceInputActive.value = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Selector Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFF0F1222), RoundedCornerShape(24.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Standard Chat Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (!isImageMode) PrimaryNeonViolet else Color.Transparent)
                    .clickable { viewModel.isImageGenerationMode.value = false }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Chat,
                        contentDescription = "Standard Chat",
                        tint = if (!isImageMode) Color.White else TextSecondarySlate,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Standard Chat",
                        color = if (!isImageMode) Color.White else TextSecondarySlate,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Image Generation Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isImageMode) SecondaryOrchid else Color.Transparent)
                    .clickable { viewModel.isImageGenerationMode.value = true }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "Image Gen Mode",
                        tint = if (isImageMode) Color.White else TextSecondarySlate,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Aura Image Gen",
                        color = if (isImageMode) Color.White else TextSecondarySlate,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedContent(
            targetState = activeSessionId,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) + 
                 slideInHorizontally(animationSpec = tween(220), initialOffsetX = { 40 }))
                .togetherWith(
                 fadeOut(animationSpec = tween(90))
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "SessionSwitchTransition"
        ) { targetSessionId ->
            // Message Scroll area
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    ChatMessageRow(
                        message = message,
                        onCopy = {
                            clipboard.setText(AnnotatedString(message.content))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onRegenerate = { viewModel.regenerateMessage(message.id) },
                        onSpeak = { viewModel.speakText(message.content) },
                        onStopSpeak = { viewModel.stopSpeaking() }
                    )
                }
                if (isChatLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(
                                color = PrimaryNeonViolet,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Aura is typing...", color = TextSecondarySlate, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Active Attachment Bar
        if (selectedAttachment != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161A2B))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (selectedAttachment?.type) {
                            "image" -> Icons.Default.Image
                            "pdf" -> Icons.Default.PictureAsPdf
                            else -> Icons.Default.Code
                        },
                        contentDescription = null,
                        tint = TertiaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedAttachment!!.name, color = TextPrimaryWhite, fontSize = 13.sp)
                }
                IconButton(onClick = { viewModel.selectedAttachment.value = null }) {
                    Icon(Icons.Default.Close, "Remove attachment", tint = AccentErrorRed, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Voice waveforms
        if (isVoiceActive) {
            VoiceWaveform(modifier = Modifier.padding(bottom = 8.dp))
        }

        // Quick Warnings if using Mock keys
        if (isSimulated) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x15FBBF24))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⚠️ Running in Offline Simulated Mode. Set GEMINI_API_KEY in secrets to activate Live AI.",
                    color = AccentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Input Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceObsidian,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach button
                IconButton(
                    onClick = {
                        val options = arrayOf("Add Image Mock", "Add PDF Mock", "Add Code Mock")
                        android.app.AlertDialog.Builder(context)
                            .setTitle("Attach file mock")
                            .setItems(options) { _, index ->
                                when (index) {
                                    0 -> viewModel.addAttachment("image")
                                    1 -> viewModel.addAttachment("pdf")
                                    2 -> viewModel.addAttachment("text")
                                }
                            }.show()
                    }
                ) {
                    Icon(Icons.Default.AttachFile, "Attach file", tint = TextSecondarySlate)
                }

                // Main Field
                TextField(
                    value = currentInput,
                    onValueChange = { viewModel.currentChatInput.value = it },
                    placeholder = { 
                        Text(
                            if (isImageMode) "Describe the image to generate (Gemini)..." 
                            else "Ask Aura anything (Gemini)...", 
                            color = TextSecondarySlate
                        ) 
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (keyEvent.key == Key.Enter && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                                    if (currentInput.isNotBlank() || viewModel.selectedAttachment.value != null) {
                                        viewModel.sendChatMessage()
                                    }
                                    return@onPreviewKeyEvent true
                                } else if (keyEvent.key == Key.K && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                                    viewModel.clearCurrentConversation()
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite
                    ),
                    maxLines = 4
                )

                // Voice Mic Toggle
                IconButton(onClick = { 
                    viewModel.isVoiceInputActive.value = true
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        speechRecognizerLauncher.launch(intent)
                    } catch (e: Exception) {
                        viewModel.isVoiceInputActive.value = false
                        Toast.makeText(context, "Voice input not supported", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = if (isVoiceActive) Icons.Filled.Mic else Icons.Outlined.MicNone,
                        contentDescription = "Voice input",
                        tint = if (isVoiceActive) SecondaryOrchid else TextSecondarySlate
                    )
                }

                // Send Button
                IconButton(
                    onClick = { viewModel.sendChatMessage() },
                    enabled = currentInput.isNotBlank() || selectedAttachment != null
                ) {
                    Icon(
                        imageVector = if (isImageMode) Icons.Default.Palette else Icons.Default.Send,
                        contentDescription = if (isImageMode) "Generate Image" else "Send",
                        tint = if (currentInput.isNotBlank() || selectedAttachment != null) {
                            if (isImageMode) SecondaryOrchid else PrimaryNeonViolet
                        } else TextSecondarySlate
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageRow(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit
) {
    val isUser = message.role == "user"
    var isSpeaking by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Profile Bubble header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = if (isUser) "YOU" else "AURA AI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) SecondaryOrchid else TertiaryCyan,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp),
                    fontSize = 9.sp,
                    color = TextSecondarySlate
                )
            }

            // Message Body Container
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) Color(0x188B5CF6) else SurfaceObsidian
                ),
                border = BorderStroke(1.dp, if (isUser) Color(0x308B5CF6) else Color(0x10FFFFFF))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Attachment chip preview if present
                    if (message.attachmentPath != null) {
                        if (message.attachmentType == "image" && !isUser) {
                            val imageBytes = remember(message.attachmentPath) {
                                try {
                                    val cleanBase64 = if (message.attachmentPath.startsWith("data:image")) {
                                        message.attachmentPath.substringAfter("base64,")
                                    } else {
                                        message.attachmentPath
                                    }
                                    android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (imageBytes != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                ) {
                                    coil.compose.AsyncImage(
                                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                                            .data(imageBytes)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = message.content,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFF0C0E17), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (message.attachmentType) {
                                        "image" -> Icons.Default.Image
                                        "pdf" -> Icons.Default.PictureAsPdf
                                        else -> Icons.Default.Code
                                    },
                                    contentDescription = null,
                                    tint = SecondaryOrchid,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (message.attachmentPath.startsWith("data:image")) "generated_image.png" else message.attachmentPath.substringAfterLast("/"),
                                    color = TextPrimaryWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Text Content Render
                    MarkdownText(text = message.content)
                }
            }

            // Action footer
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Copy",
                    fontSize = 11.sp,
                    color = TextSecondarySlate,
                    modifier = Modifier.clickable { onCopy() }
                )
                if (!isUser) {
                    Text(
                        text = "Regenerate",
                        fontSize = 11.sp,
                        color = TextSecondarySlate,
                        modifier = Modifier.clickable { onRegenerate() }
                    )
                    Text(
                        text = if (isSpeaking) "Stop Speaking" else "Read Aloud",
                        fontSize = 11.sp,
                        color = TextSecondarySlate,
                        modifier = Modifier.clickable {
                            if (isSpeaking) {
                                onStopSpeak()
                                isSpeaking = false
                            } else {
                                onSpeak()
                                isSpeaking = true
                            }
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: IMAGEN CREATIVE GENERATOR
// ==========================================
@Composable
fun ImageGenPane(viewModel: AuraViewModel) {
    val prompt by viewModel.imagePrompt.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingImage.collectAsStateWithLifecycle()
    val progress by viewModel.imageGenerationProgress.collectAsStateWithLifecycle()
    val statusText by viewModel.imageGenerationStatus.collectAsStateWithLifecycle()
    val generatedImages by viewModel.generatedImages.collectAsStateWithLifecycle()
    val selectedAspectRatio by viewModel.imageAspectRatio.collectAsStateWithLifecycle()
    val selectedModel by viewModel.geminiModel.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    var selectedImageState by remember { mutableStateOf<com.example.data.GeneratedImage?>(null) }
    var isZoomed by remember { mutableStateOf(false) }
    val selectedImage = selectedImageState ?: generatedImages.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Prompt Input Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
            border = BorderStroke(1.dp, Color(0x10FFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CREATIVE IMAGE PROMPT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TertiaryCyan, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { viewModel.imagePrompt.value = it },
                    placeholder = { Text("Describe the masterpiece you want to create...", color = TextSecondarySlate) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite,
                        focusedBorderColor = PrimaryNeonViolet,
                        unfocusedBorderColor = Color(0x20FFFFFF)
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Image Settings Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Aspect ratio chips
                    Column {
                        Text("ASPECT RATIO", fontSize = 10.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1:1", "16:9", "9:16").forEach { ratio ->
                                val isSelected = selectedAspectRatio == ratio
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) PrimaryNeonViolet else Color(0x10FFFFFF))
                                        .clickable { viewModel.imageAspectRatio.value = ratio }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = ratio,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else TextSecondarySlate
                                    )
                                }
                            }
                        }
                    }

                    // Model picker
                    Column(horizontalAlignment = Alignment.End) {
                        Text("GENERATION ENGINE", fontSize = 10.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("gemini-2.5-flash-image", "gemini-3.1-flash-image-preview").forEach { engine ->
                                val isSelected = selectedModel == engine
                                val label = if (engine.contains("3.1")) "Aura Art v3" else "Aura Art v2"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) PrimaryNeonViolet else Color(0x10FFFFFF))
                                        .clickable { viewModel.geminiModel.value = engine }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else TextSecondarySlate
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Launch generate
                Button(
                    onClick = {
                        viewModel.generateImage()
                        // Reset local selection state so it automatically previews the upcoming latest render
                        selectedImageState = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isGenerating && prompt.isNotBlank()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (!isGenerating && prompt.isNotBlank()) {
                                    Brush.horizontalGradient(colors = listOf(PrimaryNeonViolet, SecondaryOrchid))
                                } else {
                                    Brush.horizontalGradient(colors = listOf(Color(0x20FFFFFF), Color(0x20FFFFFF)))
                                }
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Canvas art", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Generation Progress Box
        if (isGenerating) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x1506B6D4)),
                border = BorderStroke(1.dp, TertiaryCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("IMAGEN RENDERING CYCLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TertiaryCyan)
                        Text("${(progress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TertiaryCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = TertiaryCyan,
                        trackColor = Color(0x2006B6D4)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ------------------------------------------
        // MASTERPIECE DISPLAY CANVAS (MAIN PREVIEW AREA)
        // ------------------------------------------
        if (selectedImage != null || isGenerating) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
                border = BorderStroke(1.dp, Color(0x15FFFFFF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isGenerating) "RENDERING MASTERPIECE CANVAS..." else "ACTIVE MASTERPIECE CANVAS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNeonViolet,
                            letterSpacing = 1.sp
                        )
                        if (selectedImage != null && !isGenerating) {
                            val isLatest = selectedImage.id == (generatedImages.firstOrNull()?.id ?: "")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isLatest) Color(0x2010B981) else Color(0x15FFFFFF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isLatest) "LATEST RENDER" else "HISTORICAL VIEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLatest) Color(0xFF10B981) else TextSecondarySlate
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Image Display Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                if (isGenerating) {
                                    when (selectedAspectRatio) {
                                        "16:9" -> 1.77f
                                        "9:16" -> 0.56f
                                        else -> 1f
                                    }
                                } else {
                                    when (selectedImage?.aspectRatio) {
                                        "16:9" -> 1.77f
                                        "9:16" -> 0.56f
                                        else -> 1f
                                    }
                                }
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F111A))
                            .clickable(enabled = !isGenerating && selectedImage != null) {
                                isZoomed = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating) {
                            // Glowing cyber skeleton loader
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator(
                                    color = PrimaryNeonViolet,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Polishing pixels...",
                                    color = TextSecondarySlate,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (selectedImage != null) {
                            val imageBytes = remember(selectedImage.imageUrl) {
                                try {
                                    android.util.Base64.decode(selectedImage.imageUrl, android.util.Base64.DEFAULT)
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (imageBytes != null) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(imageBytes)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = selectedImage.prompt,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Image format error", color = AccentErrorRed)
                                }
                            }

                            // Tap to Zoom Overlay Hint
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Color(0x90000000))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Zoom In",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text("Tap to zoom", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Metadata details & parameters panel
                    if (selectedImage != null && !isGenerating) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Technical Specifications Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF07080E), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text("ENGINE", fontSize = 8.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (selectedImage.modelUsed.contains("3.1")) "Aura Art v3" else "Aura Art v2",
                                        fontSize = 11.sp,
                                        color = TextPrimaryWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF07080E), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text("ASPECT RATIO", fontSize = 8.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = selectedImage.aspectRatio,
                                        fontSize = 11.sp,
                                        color = TextPrimaryWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF07080E), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text("DATE", fontSize = 8.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(selectedImage.timestamp),
                                        fontSize = 11.sp,
                                        color = TextPrimaryWhite,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive Parameter Details
                        Text("PROMPT DETAILS", fontSize = 10.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF07080E), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = selectedImage.prompt,
                                color = TextPrimaryWhite,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Management Tools Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.downloadImage(selectedImage) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonViolet),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(selectedImage.prompt))
                                    Toast.makeText(context, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color(0x30FFFFFF)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryWhite),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Prompt", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.savePrompt(selectedImage.prompt, "Creative")
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color(0x30FFFFFF)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryWhite),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Prompt", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Gallery Header
        Text(
            text = "PREVIOUS GENERATIONS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondarySlate,
            letterSpacing = 1.sp
        )

        // Vertical Grid Gallery of past generations
        if (generatedImages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, null, tint = Color(0x20FFFFFF), modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your generated masterpieces will show up here.", color = TextSecondarySlate, fontSize = 13.sp)
                }
            }
        } else {
            // Renders images reactively in grid
            val chunks = generatedImages.chunked(2)
            chunks.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.forEach { image ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedImageState = image }
                        ) {
                            GeneratedImageCard(
                                image = image,
                                onDownload = { viewModel.downloadImage(it) },
                                onDelete = {
                                    // If deleted image is currently selected, clear selection to avoid stale displays
                                    if (selectedImage?.id == image.id) {
                                        selectedImageState = null
                                    }
                                    viewModel.deleteGeneratedImage(it)
                                }
                            )
                        }
                    }
                    if (row.size == 1) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Lightbox Dialog Overlay for extreme high-fidelity fullscreen view
    if (isZoomed && selectedImage != null) {
        AlertDialog(
            onDismissRequest = { isZoomed = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Black.copy(alpha = 0.95f),
            tonalElevation = 0.dp,
            text = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val imageBytes = remember(selectedImage.imageUrl) {
                        try {
                            android.util.Base64.decode(selectedImage.imageUrl, android.util.Base64.DEFAULT)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (imageBytes != null) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(imageBytes)
                                .crossfade(true)
                                .build(),
                            contentDescription = selectedImage.prompt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clickable { isZoomed = false }
                        )
                    }

                    // Controls overlaid on top
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isZoomed = false },
                                modifier = Modifier.background(Color(0x80000000), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, "Close", tint = Color.White)
                            }
                            IconButton(
                                onClick = { viewModel.downloadImage(selectedImage) },
                                modifier = Modifier.background(Color(0x80000000), CircleShape)
                            ) {
                                Icon(Icons.Default.Download, "Download", tint = Color.White)
                            }
                        }

                        // Bottom caption Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xCC0C0E17)),
                            border = BorderStroke(1.dp, Color(0x20FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "PROMPT PARAMETERS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNeonViolet,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = selectedImage.prompt,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// ==========================================
// TEMPORAL PROCEDURAL VIDEO STUDIO PLAYER
// ==========================================
@Composable
fun ProceduralVideoPlayer(
    effectType: String,
    isPlaying: Boolean,
    currentTimeMs: Long,
    durationSeconds: Int,
    aspectRatio: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val timeSec = currentTimeMs / 1000f

        when (effectType) {
            "procedural_starfield" -> {
                val starCount = 100
                val random = java.util.Random(101)
                for (i in 0 until starCount) {
                    val angle = random.nextFloat() * 2f * Math.PI
                    val speed = 0.2f + random.nextFloat() * 0.4f
                    val initialDistance = random.nextFloat() * 400f
                    val dist = (initialDistance + timeSec * speed * 250f) % 400f
                    val x = (width / 2) + Math.cos(angle) * dist
                    val y = (height / 2) + Math.sin(angle) * dist
                    val alpha = (dist / 400f).coerceIn(0f, 1f)
                    val radius = 1.5f + (dist / 400f) * 6f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
            "procedural_wave" -> {
                val waveCount = 3
                for (w in 0 until waveCount) {
                    val path = androidx.compose.ui.graphics.Path()
                    val waveColor = when (w) {
                        0 -> Color(0xFF8B5CF6).copy(alpha = 0.6f) // Violet
                        1 -> Color(0xFFEC4899).copy(alpha = 0.6f) // Orchid
                        else -> Color(0xFF00E5FF).copy(alpha = 0.5f) // Cyan
                    }
                    val frequency = 0.004f + w * 0.003f
                    val speed = 2.0f + w * 1.0f
                    val amplitude = 50f + w * 30f
                    val yOffset = height / 2f + (w - 1) * 40f

                    for (x in 0..width.toInt() step 6) {
                        val angle = (x * frequency) + (timeSec * speed)
                        val y = yOffset + Math.sin(angle.toDouble()).toFloat() * amplitude
                        if (x == 0) {
                            path.moveTo(0f, y)
                        } else {
                            path.lineTo(x.toFloat(), y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = waveColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                    )
                }
            }
            "procedural_matrix" -> {
                val columns = 24
                val random = java.util.Random(42)
                for (c in 0 until columns) {
                    val x = (width / columns) * c + (width / columns / 2)
                    val speed = 120f + random.nextFloat() * 150f
                    val initialY = random.nextFloat() * height
                    val y = (initialY + timeSec * speed) % height
                    val length = 10 + random.nextInt(10)
                    for (i in 0 until length) {
                        val charY = y - i * 20f
                        if (charY in 0f..height) {
                            val alpha = (1f - (i.toFloat() / length)).coerceIn(0f, 1f)
                            val color = if (i == 0) Color.White else Color(0xFF10B981).copy(alpha = alpha)
                            drawRect(
                                color = color,
                                topLeft = androidx.compose.ui.geometry.Offset(x - 3f, charY),
                                size = androidx.compose.ui.geometry.Size(6f, 15f)
                            )
                        }
                    }
                }
            }
            "procedural_fractal" -> {
                val rotationAngle = timeSec * 40f
                val shapeCount = 6
                for (s in 0 until shapeCount) {
                    val radius = (30f + s * 40f * (1f + 0.15f * Math.sin((timeSec + s).toDouble()).toFloat())).coerceAtLeast(10f)
                    val alpha = (1f - (s.toFloat() / shapeCount)) * 0.8f
                    val color = when (s % 3) {
                        0 -> Color(0xFFEC4899).copy(alpha = alpha)
                        1 -> Color(0xFF8B5CF6).copy(alpha = alpha)
                        else -> Color(0xFF00E5FF).copy(alpha = alpha)
                    }
                    val path = androidx.compose.ui.graphics.Path()
                    val points = 6
                    val angleStep = 360f / points
                    val center = androidx.compose.ui.geometry.Offset(width / 2, height / 2)
                    for (i in 0 until points) {
                        val currentAngle = Math.toRadians((i * angleStep + rotationAngle * (1f - s * 0.08f)).toDouble())
                        val px = center.x + Math.cos(currentAngle).toFloat() * radius
                        val py = center.y + Math.sin(currentAngle).toFloat() * radius
                        if (i == 0) {
                            path.moveTo(px, py)
                        } else {
                            path.lineTo(px, py)
                        }
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                    )
                }
            }
            else -> {
                val center = androidx.compose.ui.geometry.Offset(width / 2, height / 2)
                val baseRadius = 150f
                val pulse = baseRadius + 30f * Math.sin(timeSec.toDouble() * 3f).toFloat()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(PrimaryNeonViolet.copy(alpha = 0.4f), Color.Transparent),
                        center = center,
                        radius = pulse * 1.8f
                    ),
                    radius = pulse * 1.8f,
                    center = center
                )
                for (i in 0..3) {
                    val radius = pulse * (1f - i * 0.2f)
                    val angle = timeSec * (25f + i * 12f)
                    val path = androidx.compose.ui.graphics.Path()
                    for (pt in 0..360 step 45) {
                        val rad = Math.toRadians((pt + angle).toDouble())
                        val px = center.x + Math.cos(rad).toFloat() * radius
                        val py = center.y + Math.sin(rad).toFloat() * radius
                        if (pt == 0) {
                            path.moveTo(px, py)
                        } else {
                            path.lineTo(px, py)
                        }
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.3f + i * 0.15f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 6: VIDEO GENERATION PANE
// ==========================================
@Composable
fun VideoGenPane(viewModel: AuraViewModel) {
    val prompt by viewModel.videoPrompt.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingVideo.collectAsStateWithLifecycle()
    val progress by viewModel.videoGenerationProgress.collectAsStateWithLifecycle()
    val status by viewModel.videoGenerationStatus.collectAsStateWithLifecycle()
    val selectedAspectRatio by viewModel.videoAspectRatio.collectAsStateWithLifecycle()
    val selectedDuration by viewModel.videoDuration.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.videoStyle.collectAsStateWithLifecycle()
    val generatedVideos by viewModel.generatedVideos.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    var activeVideoState by remember { mutableStateOf<com.example.data.GeneratedVideo?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTimeMs by remember { mutableStateOf(0L) }
    var isLooping by remember { mutableStateOf(true) }

    val activeVideo = activeVideoState ?: generatedVideos.firstOrNull()

    // Automatic Frame ticker when playing
    LaunchedEffect(isPlaying, currentTimeMs, activeVideo) {
        if (isPlaying && activeVideo != null) {
            val limit = activeVideo.duration * 1000L
            if (currentTimeMs >= limit) {
                if (isLooping) {
                    currentTimeMs = 0L
                } else {
                    isPlaying = false
                }
            } else {
                kotlinx.coroutines.delay(30) // ~30 fps
                currentTimeMs += 30L
            }
        }
    }

    // Reset current time when selected video changes
    LaunchedEffect(activeVideo) {
        currentTimeMs = 0L
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Creative Studio Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
            border = BorderStroke(1.dp, Color(0x10FFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = PrimaryNeonViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TEMPORAL MOTION STUDIO",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryNeonViolet,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Generate high-fidelity cinematically interpolating local videos matching natural language instructions.",
                    color = TextSecondarySlate,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Generator Setup Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
            border = BorderStroke(1.dp, Color(0x10FFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Prompt Input Box
                Column {
                    Text(
                        text = "1. DESCRIBE MOTION STORYBOARD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondarySlate,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { viewModel.videoPrompt.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("video_prompt_input"),
                        placeholder = {
                            Text(
                                "e.g., Starfield traveling nebula flyby with slow motion solar flares and volumetric glowing dust particles...",
                                color = TextSecondarySlate,
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryWhite,
                            unfocusedTextColor = TextPrimaryWhite,
                            focusedBorderColor = PrimaryNeonViolet,
                            unfocusedBorderColor = Color(0x20FFFFFF)
                        )
                    )
                }

                // Sample Quick Prompts
                Column {
                    Text(
                        text = "POPULAR KINETIC BLUEPRINTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondarySlate
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Nebula Voyage" to "Deep space cosmic starfield traveling slowly through glowing dust clouds and nebulas in slow motion",
                            "Matrix Rain" to "Rain of cascading glowing matrix code drops on black screen cyber style",
                            "Synthwave Surf" to "Dynamic sunset ocean waves glowing neon purple cyberpunk styled wireframes",
                            "Abstract Plasma" to "Rotating psychedelic vortex swirling kaleidoscope of pastel fluids and glowing liquid light"
                        ).forEach { (label, fullText) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x15FFFFFF))
                                    .clickable { viewModel.videoPrompt.value = fullText }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(label, color = TextPrimaryWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Configuration selections (Aspect Ratio, Duration, Style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Aspect ratio Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ASPECT RATIO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondarySlate
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("16:9", "9:16", "1:1").forEach { ratio ->
                                val active = selectedAspectRatio == ratio
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) Color(0x258B5CF6) else Color(0x10FFFFFF))
                                        .border(1.dp, if (active) PrimaryNeonViolet else Color.Transparent, RoundedCornerShape(6.dp))
                                        .clickable { viewModel.videoAspectRatio.value = ratio }
                                        .padding(vertical = 6.dp)
                                        .testTag("video_ratio_select_$ratio"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ratio, color = if (active) TextPrimaryWhite else TextSecondarySlate, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Duration Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DURATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondarySlate
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(4, 8, 12).forEach { secs ->
                                val active = selectedDuration == secs
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) Color(0x25EC4899) else Color(0x10FFFFFF))
                                        .border(1.dp, if (active) SecondaryOrchid else Color.Transparent, RoundedCornerShape(6.dp))
                                        .clickable { viewModel.videoDuration.value = secs }
                                        .padding(vertical = 6.dp)
                                        .testTag("video_duration_select_$secs"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${secs}s", color = if (active) TextPrimaryWhite else TextSecondarySlate, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Style select row
                Column {
                    Text(
                        text = "MOTION ART STYLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondarySlate
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Cinematic", "Cyberpunk", "Anime", "Abstract", "3D Render").forEach { style ->
                            val active = selectedStyle == style
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (active) Color(0x2000E5FF) else Color(0x10FFFFFF))
                                    .border(1.dp, if (active) TertiaryCyan else Color.Transparent, RoundedCornerShape(20.dp))
                                    .clickable { viewModel.videoStyle.value = style }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(style, color = if (active) TextPrimaryWhite else TextSecondarySlate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Launch generate Button
                Button(
                    onClick = {
                        viewModel.generateVideo()
                        activeVideoState = null // Clear manual selection to focus on newly generated
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_generate_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isGenerating && prompt.isNotBlank()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (prompt.isNotBlank() && !isGenerating) {
                                        listOf(PrimaryNeonViolet, SecondaryOrchid)
                                    } else {
                                        listOf(Color(0x30FFFFFF), Color(0x30FFFFFF))
                                    }
                                )
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Movie, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isGenerating) "RENDERING TEMPORAL MATRIX..." else "GENERATE KINETIC VIDEO",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Generation progress info panel
                if (isGenerating) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF07080E), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(status, color = TextPrimaryWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("${(progress * 100).toInt()}%", color = TertiaryCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = PrimaryNeonViolet,
                            trackColor = Color(0x10FFFFFF)
                        )
                    }
                }
            }
        }

        // Active Masterpiece Video Canvas Player
        if (activeVideo != null || isGenerating) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
                border = BorderStroke(1.dp, Color(0x15FFFFFF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isGenerating) "TEMPORAL MOTION COMPILING..." else "ACTIVE MASTERPIECE RENDER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNeonViolet,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Player Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                if (isGenerating) {
                                    when (selectedAspectRatio) {
                                        "16:9" -> 1.77f
                                        "9:16" -> 0.56f
                                        else -> 1f
                                    }
                                } else {
                                    when (activeVideo?.aspectRatio) {
                                        "16:9" -> 1.77f
                                        "9:16" -> 0.56f
                                        else -> 1f
                                    }
                                }
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF05060A)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryNeonViolet, strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Encoding temporal vectors...", color = TextSecondarySlate, fontSize = 12.sp)
                            }
                        } else if (activeVideo != null) {
                            // Run the custom procedural render Canvas
                            ProceduralVideoPlayer(
                                effectType = activeVideo.videoUrl,
                                isPlaying = isPlaying,
                                currentTimeMs = currentTimeMs,
                                durationSeconds = activeVideo.duration,
                                aspectRatio = activeVideo.aspectRatio,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Glassmorphic control Overlay when tapped or hovered, showing controls
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (!isPlaying) Color(0x50000000) else Color.Transparent)
                            ) {
                                // Overlay info or play large button
                                if (!isPlaying) {
                                    IconButton(
                                        onClick = { isPlaying = true },
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(56.dp)
                                            .background(Color(0xBB000000), CircleShape)
                                            .border(1.dp, Color(0x30FFFFFF), CircleShape)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Timeline Scrubbing Bar & Player Buttons (Only if activeVideo is present and loaded)
                    if (activeVideo != null && !isGenerating) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Timeline Seek Slider
                        val totalDurationMs = activeVideo.duration * 1000L
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val curSecs = currentTimeMs / 1000
                            val curSub = (currentTimeMs % 1000) / 100
                            Text(
                                text = String.format("%d.%d", curSecs, curSub),
                                color = TextSecondarySlate,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            Slider(
                                value = currentTimeMs.toFloat(),
                                onValueChange = {
                                    currentTimeMs = it.toLong()
                                },
                                valueRange = 0f..totalDurationMs.toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = PrimaryNeonViolet,
                                    activeTrackColor = PrimaryNeonViolet,
                                    inactiveTrackColor = Color(0x20FFFFFF)
                                )
                            )

                            Text(
                                text = String.format("%d.0", activeVideo.duration),
                                color = TextSecondarySlate,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Playback Control Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Play/Pause
                                IconButton(
                                    onClick = { isPlaying = !isPlaying },
                                    modifier = Modifier
                                        .background(Color(0x15FFFFFF), CircleShape)
                                        .testTag("video_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = TextPrimaryWhite
                                    )
                                }

                                // Restart / Rewind
                                IconButton(
                                    onClick = { currentTimeMs = 0L },
                                    modifier = Modifier.background(Color(0x10FFFFFF), CircleShape)
                                ) {
                                    Icon(Icons.Default.SkipPrevious, "Rewind", tint = TextPrimaryWhite)
                                }

                                // Loop Toggle
                                IconButton(
                                    onClick = { isLooping = !isLooping },
                                    modifier = Modifier
                                        .background(if (isLooping) Color(0x208B5CF6) else Color.Transparent, CircleShape)
                                        .testTag("video_loop_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Loop,
                                        contentDescription = "Looping",
                                        tint = if (isLooping) PrimaryNeonViolet else TextSecondarySlate
                                    )
                                }
                            }

                            // Download / Copy Details
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeVideo.style.uppercase(Locale.getDefault()),
                                    color = TertiaryCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x1500E5FF))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )

                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(activeVideo.prompt))
                                        Toast.makeText(context, "Motion prompt parameters copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    border = BorderStroke(1.dp, Color(0x20FFFFFF)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryWhite),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Prompt", fontSize = 10.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Prompt Info Box
                        Text("GENERATED PROMPT DETAILS", fontSize = 9.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF07080E), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = activeVideo.prompt,
                                color = TextPrimaryWhite,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Historic Gallery Header
        Text(
            text = "KINETIC STUDIO ARCHIVE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondarySlate,
            letterSpacing = 1.sp
        )

        if (generatedVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(SurfaceObsidian, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color(0x10FFFFFF)), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Movie, null, tint = TextSecondarySlate, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No generated videos in archive history.", color = TextSecondarySlate, fontSize = 11.sp)
                }
            }
        } else {
            // Video Cards Grid
            val chunked = generatedVideos.chunked(2)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.forEach { video ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceObsidian)
                                .border(
                                    1.dp,
                                    if (activeVideo?.id == video.id) PrimaryNeonViolet else Color(0x10FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    activeVideoState = video
                                    isPlaying = true // Auto play when tapped
                                }
                                .padding(12.dp)
                                .testTag("video_gallery_item_${video.id}")
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${video.duration}s | ${video.aspectRatio}",
                                        color = TertiaryCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    IconButton(
                                        onClick = {
                                            if (activeVideo?.id == video.id) {
                                                activeVideoState = null
                                            }
                                            viewModel.deleteGeneratedVideo(video)
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Delete", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }
                                }

                                Text(
                                    text = video.prompt,
                                    color = TextPrimaryWhite,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 14.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = video.style,
                                        color = TextSecondarySlate,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = PrimaryNeonViolet,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2.5: ANTIGRAVITY RADIO STUDIO
// ==========================================
@Composable
fun RadioShowPane(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val radioPrompt by viewModel.radioPrompt.collectAsStateWithLifecycle()
    val radioStation by viewModel.radioStation.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingRadioShow.collectAsStateWithLifecycle()
    val progress by viewModel.radioShowProgress.collectAsStateWithLifecycle()
    val statusText by viewModel.radioShowStatus.collectAsStateWithLifecycle()
    val generatedShows by viewModel.generatedRadioShows.collectAsStateWithLifecycle()
    val selectedShow by viewModel.selectedRadioShowForPlayback.collectAsStateWithLifecycle()

    val isPlaying by viewModel.isRadioShowPlaying.collectAsStateWithLifecycle()
    val activeSegmentIndex by viewModel.activeRadioShowSegmentIndex.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.radioShowPlaybackProgress.collectAsStateWithLifecycle()
    val visualizerAmplitude by viewModel.radioVisualizerAmplitude.collectAsStateWithLifecycle()
    val activeSpeaker by viewModel.activeRadioSpeaker.collectAsStateWithLifecycle()
    val activeSpeakerRole by viewModel.activeRadioSpeakerRole.collectAsStateWithLifecycle()

    val isSimulatorMode by viewModel.isSimulatorMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. HEADER CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SurfaceGlass)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Radio",
                        tint = PrimaryNeonViolet,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Antigravity Radio Network",
                            color = TextPrimaryWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Simulate or generate complete radio broadcasts using the Antigravity Agent",
                            color = TextSecondarySlate,
                            fontSize = 13.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Mode Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(SurfaceGlass, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val badgeColor = if (isSimulatorMode) SecondaryOrchid else AccentSuccessGreen
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(badgeColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSimulatorMode) "SIMULATION MODE ACTIVE" else "LIVE TRANSMISSION (GEMINI AI)",
                        color = TextPrimaryWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 2. GENERATION FORM
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SurfaceGlass)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Broadcast Station Prompt",
                    color = TextPrimaryWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Text Field
                OutlinedTextField(
                    value = radioPrompt,
                    onValueChange = { viewModel.radioPrompt.value = it },
                    placeholder = { Text("What should the broadcast be about? e.g. An intergalactic cooking segment...", color = TextSecondarySlate) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite,
                        focusedBorderColor = PrimaryNeonViolet,
                        unfocusedBorderColor = SurfaceGlass,
                        focusedContainerColor = BackgroundDeepSpace,
                        unfocusedContainerColor = BackgroundDeepSpace
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Station Theme Selector
                Text(
                    text = "Transmitter Channel Genre",
                    color = TextPrimaryWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val channels = listOf(
                    "Antigravity FM - Cosmic Lounge",
                    "Hyperdrive Beat - Synthwave Cyberpunk",
                    "Astro Science - Talk Radio",
                    "Nebula Groove - Deep Jazz"
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.forEach { channel ->
                        val isSelected = radioStation == channel
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) SurfaceGlass else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) PrimaryNeonViolet else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.radioStation.value = channel }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.radioStation.value = channel },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PrimaryNeonViolet,
                                    unselectedColor = TextSecondarySlate
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = channel,
                                color = TextPrimaryWhite,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Quick suggestions
                Text(
                    text = "Quick Presets",
                    color = TextSecondarySlate,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                val suggestions = listOf(
                    "Reviewing space luxury travel to Mars",
                    "Interstellar cooking with quantum spice",
                    "Searching for mysterious signals on Orbit Station 9"
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { sug ->
                        Box(
                            modifier = Modifier
                                .background(SurfaceGlass, RoundedCornerShape(20.dp))
                                .border(1.dp, SurfaceGlass, RoundedCornerShape(20.dp))
                                .clickable { viewModel.radioPrompt.value = sug }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = sug,
                                color = TextSecondarySlate,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Generate Button
                Button(
                    onClick = { viewModel.generateRadioShow() },
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryNeonViolet,
                        disabledContainerColor = SurfaceGlass
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = TextPrimaryWhite,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Drafting Broadcast...", color = TextPrimaryWhite)
                    } else {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = TextPrimaryWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Initiate Broadcaster Transmission", color = TextPrimaryWhite, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (isGenerating) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = PrimaryNeonViolet,
                            trackColor = SurfaceGlass
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusText,
                            color = TextSecondarySlate,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        // 3. LIVE TRANSMISSION STUDIO (Shown if selectedShow is active)
        selectedShow?.let { show ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PrimaryNeonViolet)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Title section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📻 Studio Air Control",
                            color = SecondaryOrchid,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        IconButton(onClick = { 
                            if (isPlaying) viewModel.pauseRadioShow() else viewModel.playRadioShow(show)
                        }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = PrimaryNeonViolet,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Cover Art + Live Wave Visualizer Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Decoded Cover Art
                        val bitmap = remember(show.coverArtUrl) {
                            try {
                                val bytes = Base64.decode(show.coverArtUrl, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .background(BackgroundDeepSpace, RoundedCornerShape(12.dp))
                                .border(1.dp, SurfaceGlass, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Cover Art",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = TextSecondarySlate,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                        
                        // Live Stats + Waveform visualizer
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = show.title,
                                color = TextPrimaryWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Station: ${show.stationGenre}",
                                color = TextSecondarySlate,
                                fontSize = 12.sp
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Wave Visualizer
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(45.dp)
                                    .background(BackgroundDeepSpace, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val barCount = 18
                                    val spacing = size.width / (barCount * 1.6f)
                                    val barWidth = size.width / (barCount * 2.5f)
                                    val centerY = size.height / 2f
                                    
                                    for (i in 0 until barCount) {
                                        val waveFactor = kotlin.math.sin(i.toDouble() * 0.5 + System.currentTimeMillis() * 0.01)
                                        val amplitude = visualizerAmplitude.coerceIn(0.01f, 1f)
                                        val barHeight = (size.height * 0.85f) * amplitude * (0.3f + 0.7f * kotlin.math.abs(waveFactor)).toFloat()
                                        
                                        val x = i * (barWidth + spacing) + spacing
                                        val y = centerY - barHeight / 2f
                                        
                                        drawRoundRect(
                                            color = if (isPlaying && activeSpeaker.isNotEmpty()) PrimaryNeonViolet else TextSecondarySlate,
                                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(3f)),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Playback progress slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = playbackProgress,
                            onValueChange = {}, // read-only track progression
                            colors = SliderDefaults.colors(
                                thumbColor = SecondaryOrchid,
                                activeTrackColor = PrimaryNeonViolet,
                                inactiveTrackColor = SurfaceGlass
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0:00", color = TextSecondarySlate, fontSize = 10.sp)
                            Text(
                                text = if (isPlaying && activeSpeaker.isNotEmpty()) "LIVE: SPEAKING" else "STREAM STANDBY",
                                color = if (isPlaying && activeSpeaker.isNotEmpty()) TertiaryCyan else TextSecondarySlate,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("100%", color = TextSecondarySlate, fontSize = 10.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Soundboard Effects Triggers
                    Text(
                        text = "🔊 Soundboard - Trigger Live SFX Elements",
                        color = TextPrimaryWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val sfxList = listOf(
                        "Chime" to "chime",
                        "Laser" to "laser",
                        "Static" to "static",
                        "Airhorn" to "airhorn",
                        "Applause" to "applause"
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sfxList.forEach { pair ->
                            Button(
                                onClick = { viewModel.playSoundboardSfx(pair.second) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(pair.first, color = TextPrimaryWhite, fontSize = 11.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Active Speaker Status Indicator
                    if (activeSpeaker.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrimaryNeonViolet.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, PrimaryNeonViolet.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(SecondaryOrchid, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$activeSpeaker is speaking...",
                                    color = TextPrimaryWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Role: $activeSpeakerRole | Auto-Ducked background music",
                                    color = TextSecondarySlate,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    
                    // Dialogue Bubble Logs
                    Text(
                        text = "📜 Dialogue Transmission Log",
                        color = TextPrimaryWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val parsedSegments = remember(show.scriptJson) {
                        try {
                            val arr = org.json.JSONArray(show.scriptJson)
                            val list = mutableListOf<RadioSegment>()
                            for (j in 0 until arr.length()) {
                                val obj = arr.getJSONObject(j)
                                list.add(
                                    RadioSegment(
                                        speaker = obj.optString("speaker", "Host"),
                                        text = obj.optString("text", ""),
                                        bgMusicState = obj.optString("bgMusicState", "ducked"),
                                        sfx = obj.optString("sfx").let { if (it == "null" || it.isEmpty()) null else it }
                                    )
                                )
                            }
                            list
                        } catch (e: Exception) {
                            emptyList<RadioSegment>()
                        }
                    }
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        parsedSegments.forEachIndexed { idx, seg ->
                            val isActive = idx == activeSegmentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) PrimaryNeonViolet.copy(alpha = 0.1f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) PrimaryNeonViolet else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val badgeColor = when (seg.speaker.lowercase()) {
                                    "nova", "co-host" -> SecondaryOrchid
                                    "pulsar", "guest", "dr. pulsar" -> TertiaryCyan
                                    else -> PrimaryNeonViolet
                                }
                                
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = seg.speaker,
                                            color = badgeColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (seg.sfx != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(AccentGold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "SFX: ${seg.sfx}",
                                                    color = AccentGold,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = seg.text,
                                        color = if (isActive) TextPrimaryWhite else TextSecondarySlate,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. HISTORIC RADIO ARCHIVES CARD
        Text(
            text = "📁 Transmission Archives",
            color = TextPrimaryWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        if (generatedShows.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SurfaceGlass)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = TextSecondarySlate,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recorded radio broadcasts yet. Trigger your first prompt!",
                            color = TextSecondarySlate,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                generatedShows.forEach { show ->
                    val isCurrent = selectedShow?.id == show.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playRadioShow(show) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) SurfaceGlass else SurfaceObsidian
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) PrimaryNeonViolet else SurfaceGlass
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val bitmap = remember(show.coverArtUrl) {
                                try {
                                    val bytes = Base64.decode(show.coverArtUrl, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(BackgroundDeepSpace, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Radio, contentDescription = null, tint = TextSecondarySlate)
                                }
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = show.title,
                                    color = TextPrimaryWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = show.stationGenre,
                                    color = TextSecondarySlate,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { 
                                        if (isCurrent && isPlaying) viewModel.pauseRadioShow() else viewModel.playRadioShow(show)
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Control",
                                        tint = if (isCurrent) SecondaryOrchid else TextSecondarySlate
                                    )
                                }
                                
                                IconButton(
                                    onClick = { viewModel.deleteRadioShow(show) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = AccentErrorRed.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ==========================================
// SCREEN 3: SAVED PROMPTS LIBRARY
// ==========================================
@Composable
fun SavedPromptsPane(viewModel: AuraViewModel) {
    val savedPrompts by viewModel.savedPrompts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
            border = BorderStroke(1.dp, Color(0x10FFFFFF))
        ) {
            var newText by remember { mutableStateOf("") }
            var newCategory by remember { mutableStateOf("Creative") }

            Column(modifier = Modifier.padding(16.dp)) {
                Text("ADD CUSTOM PROMPT TEMPLATE", fontSize = 12.sp, color = PrimaryNeonViolet, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    placeholder = { Text("Enter prompt template structure...", color = TextSecondarySlate) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite,
                        focusedBorderColor = PrimaryNeonViolet
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Creative", "Coding", "Research", "General").forEach { cat ->
                            val selected = newCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) PrimaryNeonViolet else Color(0x10FFFFFF))
                                    .clickable { newCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(cat, fontSize = 11.sp, color = if (selected) Color.White else TextSecondarySlate, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (newText.isNotBlank()) {
                                viewModel.savePrompt(newText, newCategory)
                                newText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonViolet)
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        Text("SAVED TEMPLATES", fontSize = 12.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)

        savedPrompts.forEach { prompt ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(PrimaryNeonViolet.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(prompt.category, color = PrimaryNeonViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { viewModel.deleteSavedPrompt(prompt) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = AccentErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(prompt.text, color = TextPrimaryWhite, fontSize = 14.sp, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.currentChatInput.value = prompt.text
                                viewModel.activeScreen.value = Screen.Chat
                                Toast.makeText(context, "Loaded into chat", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x10FFFFFF))
                        ) {
                            Text("Use in Chat", color = TextPrimaryWhite)
                        }
                        Button(
                            onClick = {
                                viewModel.imagePrompt.value = prompt.text
                                viewModel.activeScreen.value = Screen.ImageGen
                                Toast.makeText(context, "Loaded into Imagen", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x10FFFFFF))
                        ) {
                            Text("Use in Imagen", color = TextPrimaryWhite)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 4: USAGE STATISTICS DASHBOARD
// ==========================================
@Composable
fun DashboardPane(viewModel: AuraViewModel) {
    val stats = viewModel.getUsageStats()
    val isSimulated by viewModel.isSimulatorMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("DASHBOARD STATS", fontSize = 12.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceObsidian)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.ChatBubble, "Messages", tint = PrimaryNeonViolet, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Messages", color = TextSecondarySlate, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${stats.totalMessages}", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceObsidian)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Image, "Images", tint = SecondaryOrchid, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Photos", color = TextSecondarySlate, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${stats.totalImages}", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceObsidian)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Movie, "Videos", tint = TertiaryCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Videos", color = TextSecondarySlate, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${stats.totalVideos}", color = TextPrimaryWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("MODEL DISTRIBUTIONS & ENGINE CONFIG", fontSize = 12.sp, color = TertiaryCyan, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active Chat engine", color = TextSecondarySlate)
                    Text(if (isSimulated) "Offline Simulator" else "Aura 3.0 Flash", color = TextPrimaryWhite, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active Image engine", color = TextSecondarySlate)
                    Text(if (isSimulated) "Offline Simulator" else "Aura Art v3", color = TextPrimaryWhite, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active Video engine", color = TextSecondarySlate)
                    Text(if (isSimulated) "Offline Simulator" else "Aura Video v3", color = TextPrimaryWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: SETTINGS & SECRETS MANAGER
// ==========================================
@Composable
fun SettingsPane(viewModel: AuraViewModel) {
    val isSimulated by viewModel.isSimulatorMode.collectAsStateWithLifecycle()
    val supabaseUrlState by viewModel.supabaseUrl.collectAsStateWithLifecycle()
    val supabaseKeyState by viewModel.supabaseAnonKey.collectAsStateWithLifecycle()

    var inputUrl by remember(supabaseUrlState) { mutableStateOf(supabaseUrlState) }
    var inputKey by remember(supabaseKeyState) { mutableStateOf(supabaseKeyState) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x20EF4444)),
            border = BorderStroke(1.dp, AccentErrorRed.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, "Security Alert", tint = AccentErrorRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Security Warning", color = AccentErrorRed, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android APKs can be decompiled, and embedded credentials can be extracted. For absolute security, manage your keys through secure environment secrets rather than hardcoding.",
                    color = TextPrimaryWhite,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Credentials & Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
            border = BorderStroke(1.dp, Color(0x10FFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("GEMINI ENGINE STATUS", fontSize = 12.sp, color = PrimaryNeonViolet, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (isSimulated) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x15FFB300))
                            .border(1.dp, Color(0x30FFB300), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, "Offline", tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Offline Simulated Mode", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Aura is currently running in simulated mode. Conversations are handled by an intelligent local template engine, and images are dynamically drawn using a custom artistic shader generator.\n\nTo activate Live Aura AI, enter your GEMINI_API_KEY into the Secrets panel in AI Studio.",
                                color = TextSecondarySlate,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1510B981))
                            .border(1.dp, Color(0x3010B981), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, "Live", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Live Aura Active", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Connected securely! Conversations are streamed in real-time from Aura 3.0 Flash, and your creative canvas renders using Aura Art v2.",
                                color = TextSecondarySlate,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Supabase Integration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian),
            border = BorderStroke(1.dp, Color(0x10FFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SUPABASE CHAT STORAGE INTEGRATION", fontSize = 12.sp, color = PrimaryNeonViolet, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Supabase Project URL", fontSize = 11.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("https://your-project.supabase.co", color = TextSecondarySlate, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite,
                        focusedBorderColor = PrimaryNeonViolet,
                        unfocusedBorderColor = Color(0x20FFFFFF)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Supabase Anon Key", fontSize = 11.sp, color = TextSecondarySlate, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    placeholder = { Text("eyJhbGciOi...", color = TextSecondarySlate, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite,
                        focusedBorderColor = PrimaryNeonViolet,
                        unfocusedBorderColor = Color(0x20FFFFFF)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveSupabaseConfig(inputUrl, inputKey)
                            Toast.makeText(context, "Supabase configurations saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonViolet)
                    ) {
                        Text("Save", fontSize = 12.sp, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            viewModel.backupToSupabase()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x158B5CF6)),
                        border = BorderStroke(1.dp, PrimaryNeonViolet)
                    ) {
                        Text("Backup", fontSize = 12.sp, color = TextPrimaryWhite, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            viewModel.restoreFromSupabase()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x15EC4899)),
                        border = BorderStroke(1.dp, Color(0xFFEC4899))
                    ) {
                        Text("Retrieve", fontSize = 12.sp, color = TextPrimaryWhite, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Syncs and restores local Room chat history to/from Supabase tables (`chat_sessions` and `chat_messages`) securely.",
                    color = TextSecondarySlate,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Theme preference
        val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text("Theme mode", color = TextPrimaryWhite, fontWeight = FontWeight.Bold)
                    Text("Select light, dark, or high contrast mode", color = TextSecondarySlate, fontSize = 11.sp)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf("light" to "Light", "dark" to "Dark", "high_contrast" to "High Contrast")
                    themes.forEach { (key, label) ->
                        val isSelected = currentThemeMode == key
                        Button(
                            onClick = { viewModel.themeMode.value = key },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) PrimaryNeonViolet else SurfaceGlass
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextPrimaryWhite,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Export/Delete database
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceObsidian)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DATA INTEGRITY & ARCHIVES", fontSize = 12.sp, color = AccentGold, fontWeight = FontWeight.Bold)

                Button(
                    onClick = {
                        val exportedText = viewModel.exportConversations()
                        clipboard.setText(AnnotatedString(exportedText))
                        Toast.makeText(context, "Chat history copied as structured text archive", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x10FFFFFF))
                ) {
                    Text("Export Chats to Clipboard", color = TextPrimaryWhite)
                }

                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        Toast.makeText(context, "Database purged successfully", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentErrorRed)
                ) {
                    Text("Clear Chat Database")
                }
            }
        }
    }
}

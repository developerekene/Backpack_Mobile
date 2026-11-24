package com.example.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.accessibility.AccessibilityMenuScreen
import com.example.ui.courses.CoursesScreen
import com.example.ui.courses.CoursesViewModel
import com.example.ui.events.EventsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.notifications.NotificationsScreen
import com.example.ui.profile.ProfileAlertsScreen
import com.example.ui.profile.ProfileAlertsViewModel
import com.example.ui.progress.ProgressScreen
import com.example.ui.progress.ProgressViewModel
import com.example.ui.submissions.SubmissionsReviewScreen
import com.example.ui.submissions.SubmissionsViewModel
import com.example.ui.theme.BackpackPrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    coursesViewModel: CoursesViewModel,
    captureViewModel: com.example.ui.capture.CaptureViewModel,
    profileAlertsViewModel: ProfileAlertsViewModel,
    progressViewModel: ProgressViewModel,
    submissionsViewModel: SubmissionsViewModel,
    userProfile: UserProfile?,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    val role = userProfile?.userRole ?: UserRole.STUDENT
    
    // Request push notification permission on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Permission granted state handled by OS */ }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Define bottom nav tabs based on role
    val tabs = when (role) {
        UserRole.STUDENT -> listOf(
            NavTab("Dashboard", Icons.Default.Today, 0),
            NavTab("My Courses", Icons.Default.Class, 1),
            NavTab("Progress", Icons.Default.Analytics, 2),
            NavTab("Profile", Icons.Default.Person, 3)
        )
        UserRole.INSTRUCTOR -> listOf(
            NavTab("Dashboard", Icons.Default.Today, 0),
            NavTab("My Courses", Icons.Default.Class, 1),
            NavTab("Submissions", Icons.Default.Assignment, 2),
            NavTab("Profile", Icons.Default.Person, 3)
        )
        UserRole.ORGANISATION, UserRole.SUPER_ADMIN -> listOf(
            NavTab("Dashboard", Icons.Default.Today, 0),
            NavTab("Courses", Icons.Default.Class, 1),
            NavTab("Reviews", Icons.Default.Assignment, 2),
            NavTab("Profile", Icons.Default.Person, 3)
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var currentSubScreen by remember { mutableStateOf<String?>(null) } // "notifications", "events", "accessibility"
    var showHamburgerDrawer by remember { mutableStateOf(false) }

    val unreadCount by profileAlertsViewModel.unreadCount.collectAsState()
    val events by homeViewModel.events.collectAsState()
    val courses by homeViewModel.courses.collectAsState()

    // Sub-screen routing
    when (currentSubScreen) {
        "notifications" -> {
            NotificationsScreen(
                viewModel = profileAlertsViewModel,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "events" -> {
            EventsScreen(
                events = events,
                courses = courses,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "accessibility" -> {
            AccessibilityMenuScreen(
                userProfile = userProfile,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_backpack_logo),
                                contentDescription = "Logo",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "BACKPACK",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    actions = {
                        // Notifications Bell Icon (Dedicated Notifications Page)
                        IconButton(
                            onClick = { currentSubScreen = "notifications" },
                            modifier = Modifier.testTag("top_navbar_notifications_button")
                        ) {
                            if (unreadCount > 0) {
                                BadgedBox(
                                    badge = { Badge { Text("$unreadCount") } }
                                ) {
                                    Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications")
                                }
                            } else {
                                Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications")
                            }
                        }

                        // Hamburger Menu Icon (replaces dark mode icon in top navbar)
                        IconButton(
                            onClick = { showHamburgerDrawer = true },
                            modifier = Modifier.testTag("hamburger_menu_button")
                        ) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab.index,
                            onClick = { selectedTab = tab.index },
                            icon = {
                                Icon(imageVector = tab.icon, contentDescription = tab.label)
                            },
                            label = { 
                                Text(
                                    text = tab.label, 
                                    fontSize = 10.sp, 
                                    fontWeight = if (selectedTab == tab.index) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                ) 
                            },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = BackpackPrimaryBlue)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        viewModel = homeViewModel,
                        userProfile = userProfile,
                        isDarkMode = isDarkMode,
                        onToggleTheme = onToggleTheme,
                        onNavigateToCourses = { selectedTab = 1 },
                        onOpenCourse = { course ->
                            coursesViewModel.selectCourse(course)
                            selectedTab = 1
                        },
                        onNavigateToCapture = { selectedTab = 2 }
                    )
                    1 -> CoursesScreen(viewModel = coursesViewModel, userProfile = userProfile)
                    2 -> {
                        if (role == UserRole.STUDENT) {
                            ProgressScreen(viewModel = progressViewModel, userProfile = userProfile)
                        } else {
                            SubmissionsReviewScreen(viewModel = submissionsViewModel, userProfile = userProfile)
                        }
                    }
                    3 -> ProfileAlertsScreen(
                        viewModel = profileAlertsViewModel,
                        userProfile = userProfile,
                        onSignOut = onSignOut
                    )
                }
            }
        }

        // Right-Sliding Hamburger Menu Modal Drawer with Smooth Transition
        AnimatedVisibility(
            visible = showHamburgerDrawer,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showHamburgerDrawer = false },
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .clickable(enabled = false) {}, // Consume clicks
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Drawer Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_backpack_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Backpack Menu",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            IconButton(onClick = { showHamburgerDrawer = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Menu")
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // User Summary Header in Drawer
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = userProfile?.name?.ifBlank { null } ?: "Backpack User",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = userProfile?.email ?: "user@backpack.edu",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Dark Mode Toggle Item
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleTheme() },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null,
                                        tint = BackpackPrimaryBlue
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text("Dark Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                Switch(
                                    checked = isDarkMode,
                                    onCheckedChange = { onToggleTheme() }
                                )
                            }
                        }

                        // Scheduled Events Item
                        NavigationMenuItem(
                            icon = Icons.Default.Event,
                            label = "Scheduled Events",
                            onClick = {
                                showHamburgerDrawer = false
                                currentSubScreen = "events"
                            }
                        )

                        // Accessibility & Special Needs Item
                        NavigationMenuItem(
                            icon = Icons.Default.Accessible,
                            label = "Accessibility & Special Needs",
                            onClick = {
                                showHamburgerDrawer = false
                                currentSubScreen = "accessibility"
                            }
                        )

                        // Notifications Inbox Item
                        NavigationMenuItem(
                            icon = Icons.Default.Notifications,
                            label = "Notifications Inbox",
                            badgeCount = unreadCount,
                            onClick = {
                                showHamburgerDrawer = false
                                currentSubScreen = "notifications"
                            }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        HorizontalDivider()

                        // Sign Out Item
                        NavigationMenuItem(
                            icon = Icons.Default.ExitToApp,
                            label = "Sign Out of Backpack",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                showHamburgerDrawer = false
                                onSignOut()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int = 0,
    tint: Color = BackpackPrimaryBlue,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (tint == BackpackPrimaryBlue) MaterialTheme.colorScheme.onSurface else tint
                )
            }
            if (badgeCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = CircleShape
                ) {
                    Text(
                        text = "$badgeCount",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

data class NavTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val index: Int)

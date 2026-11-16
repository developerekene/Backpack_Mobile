package com.example.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.courses.CoursesScreen
import com.example.ui.courses.CoursesViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.profile.NotificationsModalSheet
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
    var showNotificationsSheet by remember { mutableStateOf(false) }
    val unreadCount by profileAlertsViewModel.unreadCount.collectAsState()

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
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                    IconButton(
                        onClick = { showNotificationsSheet = true },
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

            if (showNotificationsSheet) {
                NotificationsModalSheet(
                    viewModel = profileAlertsViewModel,
                    onDismiss = { showNotificationsSheet = false }
                )
            }
        }
    }
}

data class NavTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val index: Int)

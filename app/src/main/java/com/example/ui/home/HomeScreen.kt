package com.example.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userProfile: UserProfile? = null,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToCourses: () -> Unit,
    onOpenCourse: (CourseItem) -> Unit = { onNavigateToCourses() },
    onNavigateToCapture: () -> Unit = {}
) {
    val events by viewModel.events.collectAsState()
    val liveProfile by viewModel.userProfile.collectAsState()
    val enrolledCourses by viewModel.courses.collectAsState()
    val allCourses by viewModel.allCourses.collectAsState()
    val organizations by viewModel.organizations.collectAsState()
    val enrollmentRequests by viewModel.enrollmentRequests.collectAsState()
    val orgJoinRequests by viewModel.orgJoinRequests.collectAsState()
    val courseDonations by viewModel.courseDonations.collectAsState()
    val orgMembers by viewModel.orgMembers.collectAsState()
    val userProgressList by viewModel.userProgressList.collectAsState()
    val assessments by viewModel.assessments.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val attendanceRecords by viewModel.attendanceRecords.collectAsState()
    val statusMessage by viewModel.checkInStatusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeProfile = liveProfile ?: userProfile
    val role = activeProfile?.userRole ?: UserRole.STUDENT

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (role) {
            UserRole.STUDENT -> StudentDashboardView(
                userProfile = activeProfile,
                enrolledCourses = enrolledCourses,
                allCourses = allCourses,
                organizations = organizations,
                enrollmentRequests = enrollmentRequests,
                orgMembers = orgMembers,
                userProgressList = userProgressList,
                assessments = assessments,
                submissions = submissions,
                events = events,
                onCheckIn = { viewModel.checkIn(it) },
                onOpenCourse = onOpenCourse,
                onNavigateToCourses = onNavigateToCourses
            )
            UserRole.INSTRUCTOR -> InstructorDashboardView(
                userProfile = activeProfile,
                courses = enrolledCourses,
                organizations = organizations,
                enrollmentRequests = enrollmentRequests,
                orgMembers = orgMembers,
                assessments = assessments,
                submissions = submissions,
                attendanceRecords = attendanceRecords,
                events = events,
                onCheckIn = { viewModel.checkIn(it) },
                onOpenCourse = onOpenCourse
            )
            UserRole.ORGANISATION, UserRole.SUPER_ADMIN -> OrganisationDashboardView(
                userProfile = activeProfile,
                courses = enrolledCourses,
                organizations = organizations,
                enrollmentRequests = enrollmentRequests,
                orgJoinRequests = orgJoinRequests,
                courseDonations = courseDonations,
                orgMembers = orgMembers,
                userProgressList = userProgressList,
                assessments = assessments,
                submissions = submissions,
                attendanceRecords = attendanceRecords,
                events = events,
                onOpenCourse = onOpenCourse
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AgendaEventCard(
    event: ScheduleEvent,
    courseTitle: String? = null,
    onCheckIn: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agenda_event_card_${event.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${event.date} • ${event.time} (${event.durationMins}m)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BackpackPrimaryBlue
                        )
                        if (event.isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = BackpackStatusGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "LIVE NOW",
                                    color = BackpackStatusGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = event.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    if (!courseTitle.isNullOrBlank()) {
                        Text(
                            text = courseTitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (event.isCheckedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Checked In",
                            tint = BackpackStatusGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Present",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BackpackStatusGreen
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onCheckIn,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("agenda_checkin_button_${event.id}"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Check In", fontSize = 12.sp)
                    }
                }
            }

            event.meetingUrl?.ifBlank { null }?.let { url ->
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (event.isActive) "Join Live Class" else "Open Meeting Link",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

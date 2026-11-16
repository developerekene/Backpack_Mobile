package com.example.ui.courses

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen

@Composable
fun CoursesScreen(
    viewModel: CoursesViewModel,
    userProfile: UserProfile? = null
) {
    val courses by viewModel.courses.collectAsState()
    val organizations by viewModel.organizations.collectAsState()
    val selectedCourse by viewModel.selectedCourse.collectAsState()
    val userProgress by viewModel.userProgress.collectAsState()
    val materials by viewModel.materials.collectAsState()
    val assessments by viewModel.assessments.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val discussionChannels by viewModel.discussionChannels.collectAsState()
    val activeChannelId by viewModel.activeChannelId.collectAsState()
    val discussionMessages by viewModel.discussionMessages.collectAsState()
    val discussionPolls by viewModel.discussionPolls.collectAsState()
    val coursePresence by viewModel.coursePresence.collectAsState()
    val savedMessageIds by viewModel.savedMessageIds.collectAsState()
    val attendanceRecords by viewModel.attendanceRecords.collectAsState()
    val scheduleEvents by viewModel.scheduleEvents.collectAsState()
    val courseMembers by viewModel.courseMembers.collectAsState()
    val courseEnrollmentRequests by viewModel.courseEnrollmentRequests.collectAsState()
    val activeVideoUrl by viewModel.activeVideoUrl.collectAsState()
    val activePdfName by viewModel.activePdfName.collectAsState()
    val activePdfUrl by viewModel.activePdfUrl.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val role = userProfile?.userRole ?: UserRole.STUDENT
    val isStudent = role == UserRole.STUDENT
    val currentUid = userProfile?.userId?.ifBlank { null } ?: viewModel.currentUserId()

    var activeTab by remember { mutableIntStateOf(0) }
    var discussionSearch by remember { mutableStateOf("") }
    var discussionFilter by remember { mutableStateOf("All") }
    var attendanceMode by remember { mutableStateOf("physical") }

    val courseTabs = if (isStudent) {
        listOf("Overview", "Modules", "Assessments", "Discussion", "Attendance", "Schedule & Cert")
    } else {
        listOf("Overview", "Modules", "Assessments", "Discussion", "Roll Call", "Schedule & Cert")
    }

    LaunchedEffect(actionFeedback) {
        actionFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val activeTabLabel = courseTabs.getOrNull(activeTab) ?: courseTabs.first()
    LaunchedEffect(selectedCourse?.id, activeTabLabel) {
        if (activeTabLabel == "Discussion" && selectedCourse != null) {
            viewModel.pingPresence()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (courses.isEmpty()) {
            EmptyCoursesView(isStudent = isStudent)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = if (isStudent) "Enrolled Courses (${courses.size})"
                        else "Course Classrooms (${courses.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ScrollableTabRow(
                        selectedTabIndex = courses.indexOfFirst { it.id == selectedCourse?.id }.coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {}
                    ) {
                        courses.forEach { course ->
                            val isSelected = selectedCourse?.id == course.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectCourse(course)
                                    activeTab = 0
                                },
                                label = { Text(course.title, maxLines = 1) },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("course_chip_${course.id}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BackpackPrimaryBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                selectedCourse?.let { currentCourse ->
                    item {
                        CourseHeaderCard(course = currentCourse, organizations = organizations)
                    }

                    item {
                        ScrollableTabRow(
                            selectedTabIndex = activeTab.coerceIn(0, courseTabs.size - 1),
                            edgePadding = 0.dp,
                            containerColor = Color.Transparent,
                            contentColor = BackpackPrimaryBlue,
                            divider = { HorizontalDivider() }
                        ) {
                            courseTabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = activeTab == index,
                                    onClick = { activeTab = index },
                                    modifier = Modifier.testTag("course_tab_${title.lowercase().replace(" ", "_")}"),
                                    text = {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }
                    }

                    when (activeTabLabel) {
                        "Overview" -> studentOverviewTab(
                            course = currentCourse,
                            organizations = organizations,
                            materials = materials,
                            onOpenDocument = { name, url -> viewModel.openPdfAttachment(name, url) },
                            onPlayVideo = { viewModel.playVideo(it) }
                        )
                        "Modules" -> studentModulesTab(
                            course = currentCourse,
                            userProgress = userProgress,
                            onPlayVideo = { viewModel.playVideo(it) },
                            onOpenDocument = { name, url -> viewModel.openPdfAttachment(name, url) }
                        )
                        "Assessments" -> if (isStudent) {
                            studentAssessmentsTab(
                                courseId = currentCourse.id,
                                assessments = assessments,
                                submissions = submissions,
                                isSubmitting = isSubmitting,
                                onSubmitAssessment = { ass, content, uri, manualUrl, subId, onDone ->
                                    viewModel.submitAssessmentWork(ass, content, uri, manualUrl, subId, onDone)
                                },
                                onOpenAttachment = { name, url -> viewModel.openPdfAttachment(name, url) }
                            )
                        } else {
                            instructorOrgAssessmentsTab(
                                course = currentCourse,
                                assessments = assessments,
                                submissions = submissions,
                                isSubmitting = isSubmitting,
                                onSaveEssayDraft = { sub, scores -> viewModel.saveEssayScoresDraft(sub, scores) },
                                onGradeSubmission = { sub, score, fb, essays, done ->
                                    viewModel.gradeSubmission(sub, score, fb, essays, done)
                                },
                                onOpenAttachment = { name, url -> viewModel.openPdfAttachment(name, url) }
                            )
                        }
                        "Discussion" -> studentDiscussionTab(
                            currentUserId = currentUid,
                            channels = discussionChannels,
                            activeChannelId = activeChannelId,
                            messages = discussionMessages,
                            polls = discussionPolls,
                            presenceList = coursePresence,
                            savedMessageIds = savedMessageIds,
                            isSubmitting = isSubmitting,
                            searchQuery = discussionSearch,
                            onSearchQueryChange = { discussionSearch = it },
                            filterTab = discussionFilter,
                            onFilterTabChange = { discussionFilter = it },
                            onSelectChannel = { viewModel.selectChannel(it) },
                            onToggleSubscribe = { viewModel.toggleChannelSubscription(it) },
                            onSendMessage = { text, uri, parentId, onDone ->
                                viewModel.sendMessage(text, uri, null, parentId, onDone)
                            },
                            onToggleReaction = { msgId, emoji -> viewModel.toggleReaction(msgId, emoji) },
                            onVotePoll = { pollId, optId -> viewModel.votePoll(pollId, optId) },
                            onToggleSave = { viewModel.toggleSavedMessage(it) },
                            onPlayVideo = { viewModel.playVideo(it) },
                            onOpenDocument = { name, url -> viewModel.openPdfAttachment(name, url) },
                            isModerator = !isStudent,
                            onToggleVerifyMessage = { msgId, verified -> viewModel.verifyDiscussionMessage(msgId, verified) },
                            onTogglePinMessage = { msgId, pinned -> viewModel.pinDiscussionMessage(msgId, pinned) },
                            onCreateChannel = { name, desc, done -> viewModel.createChannel(name, desc, done) },
                            onCreatePoll = { q, opts, done -> viewModel.createPoll(q, opts, done) }
                        )
                        "Attendance" -> studentAttendanceTab(
                            course = currentCourse,
                            currentUserId = currentUid,
                            attendanceRecords = attendanceRecords,
                            activeMode = attendanceMode,
                            onModeChange = { attendanceMode = it },
                            isSubmitting = isSubmitting,
                            onClockIn = { mode, loc -> viewModel.clockInAttendance(mode, loc) },
                            onClockOut = { mode -> viewModel.clockOutAttendance(mode) }
                        )
                        "Roll Call" -> instructorOrgAttendanceTab(
                            course = currentCourse,
                            currentUserId = currentUid,
                            attendanceRecords = attendanceRecords,
                            orgMembers = courseMembers,
                            approvedEnrollments = courseEnrollmentRequests,
                            activeMode = attendanceMode,
                            onModeChange = { attendanceMode = it },
                            isSubmitting = isSubmitting,
                            lastStatusMessage = actionFeedback,
                            onClockIn = { mode, loc -> viewModel.clockInAttendance(mode, loc) },
                            onClockOut = { mode -> viewModel.clockOutAttendance(mode) },
                            onSaveRollCall = { d, m, t, l, e, existing, done ->
                                viewModel.saveRollCallSession(d, m, t, l, e, existing, done)
                            },
                            onKioskClock = { person, act ->
                                viewModel.kioskClockParticipant(person.userId, person.name, person.email, person.role, act)
                            },
                            onDeleteRecord = { rec -> viewModel.deleteAttendanceRecord(rec) }
                        )
                        "Schedule & Cert" -> studentScheduleAndCertTab(
                            course = currentCourse,
                            userProfile = userProfile,
                            userProgress = userProgress,
                            scheduleEvents = scheduleEvents,
                            organizations = organizations
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    activeVideoUrl?.let { url -> VideoPlayerModal(url) { viewModel.closeVideo() } }
    activePdfName?.let { name -> DocumentPreviewModal(name, activePdfUrl) { viewModel.closePdfAttachment() } }
}

@Composable
fun EmptyCoursesView(isStudent: Boolean = true) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = Icons.Default.Class,
                contentDescription = null,
                tint = BackpackPrimaryBlue.copy(alpha = 0.55f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isStudent) "No Enrolled Courses Yet" else "No Assigned Courses",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isStudent) {
                    "Enroll in courses on the Backpack Web Portal. Approved courses will appear here automatically."
                } else {
                    "Author and publish courses on the Backpack Web Portal. Assigned courses appear here."
                },
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://backpack-edu.com")))
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Backpack Web Portal")
            }
        }
    }
}

@Composable
fun CourseHeaderCard(course: CourseItem, organizations: List<Organization> = emptyList()) {
    val orgName = organizations.firstOrNull { it.id == course.orgId || it.ownerId == course.orgId }?.name
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = BackpackStatusGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = course.pacing.uppercase(),
                        color = BackpackStatusGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            course.subtitle?.ifBlank { null }?.let {
                Text(text = it, fontSize = 13.sp, color = BackpackPrimaryBlue, fontWeight = FontWeight.Medium)
            }
            val metaParts = listOfNotNull(
                orgName?.ifBlank { null },
                course.instructorName?.ifBlank { null }?.let { "Instructor: $it" }
            )
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" • "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStateText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(vertical = 24.dp)
            .fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
fun VideoPlayerModal(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lesson Media Player", fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close player") }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
fun DocumentPreviewModal(name: String, url: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = BackpackPrimaryBlue, modifier = Modifier.size(44.dp))
                Text(text = name, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (!url.isNullOrBlank() && url.startsWith("data:image", ignoreCase = true)) {
                    BackpackImagePreview(
                        imageUrl = url,
                        contentDescription = name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                } else {
                    Text(
                        text = if (url?.startsWith("data:") == true) {
                            "This file is stored securely on Backpack. Use the Backpack Web Portal to download or open binary document attachments."
                        } else {
                            "Document reference synchronized from Backpack."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                    Button(
                        onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open External Link")
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

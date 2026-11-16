package com.example.ui.submissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.BackpackSnapshotParser
import com.example.ui.courses.DocumentPreviewModal
import com.example.ui.home.AgendaEventCard
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.ui.theme.BackpackStatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionsReviewScreen(
    viewModel: SubmissionsViewModel,
    userProfile: UserProfile?
) {
    val liveProfile by viewModel.userProfile.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val organizations by viewModel.organizations.collectAsState()
    val enrollmentRequests by viewModel.enrollmentRequests.collectAsState()
    val allEnrollmentRequests by viewModel.allEnrollmentRequests.collectAsState()
    val orgJoinRequests by viewModel.orgJoinRequests.collectAsState()
    val courseDonations by viewModel.courseDonations.collectAsState()
    val assessments by viewModel.assessments.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val attendanceRecords by viewModel.attendanceRecords.collectAsState()
    val scheduleEvents by viewModel.scheduleEvents.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val activeProfile = liveProfile ?: userProfile
    val role = activeProfile?.userRole ?: UserRole.INSTRUCTOR
    val isOrg = role == UserRole.ORGANISATION || role == UserRole.SUPER_ADMIN

    var activeSubTab by remember { mutableIntStateOf(0) }
    var appStatusFilter by remember { mutableStateOf("pending") }
    var gradingFilter by remember { mutableStateOf("pending") }
    var selectedRequest by remember { mutableStateOf<EnrollmentRequest?>(null) }
    var previewDocument by remember { mutableStateOf<Pair<String, String?>?>(null) }

    LaunchedEffect(actionFeedback) {
        actionFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val subTabs = listOf("Application Review", "Assessment Grading", "Roll Call & Check-Ins")
    val courseById = courses.associateBy { it.id }
    val assessmentById = assessments.associateBy { it.id }

    val filteredRequests = remember(enrollmentRequests, appStatusFilter) {
        when (appStatusFilter) {
            "all" -> enrollmentRequests
            else -> enrollmentRequests.filter { it.status == appStatusFilter }
        }
    }
    val filteredSubmissions = remember(submissions, gradingFilter) {
        when (gradingFilter) {
            "pending" -> submissions.filter { it.status != "graded" }
            "graded" -> submissions.filter { it.status == "graded" }
            else -> submissions
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ReviewStatusBanner(
                    roleText = if (isOrg) "Organization Review Command" else "Faculty Review Command",
                    pendingApps = enrollmentRequests.count { it.status == "pending" },
                    pendingGrades = submissions.count { it.status != "graded" }
                )
            }

            item {
                TabRow(
                    selectedTabIndex = activeSubTab.coerceIn(0, subTabs.size - 1),
                    containerColor = Color.Transparent,
                    contentColor = BackpackPrimaryBlue,
                    divider = { HorizontalDivider() }
                ) {
                    subTabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = activeSubTab == idx,
                            onClick = { activeSubTab = idx },
                            modifier = Modifier.testTag("reviews_tab_$idx"),
                            text = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                        )
                    }
                }
            }

            when (activeSubTab) {
                0 -> {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "pending" to "Pending (${enrollmentRequests.count { it.status == "pending" }})",
                                "approved" to "Approved (${enrollmentRequests.count { it.status == "approved" }})",
                                "rejected" to "Rejected (${enrollmentRequests.count { it.status == "rejected" }})",
                                "all" to "All (${enrollmentRequests.size})"
                            ).forEach { (key, lbl) ->
                                FilterChip(
                                    selected = appStatusFilter == key,
                                    onClick = { appStatusFilter = key },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    if (filteredRequests.isEmpty()) {
                        item {
                            EmptyReviewStateCard(
                                icon = Icons.Default.AssignmentInd,
                                title = "No Matching Applications",
                                subtitle = "Student enrollment applications and documents submitted on Backpack appear here."
                            )
                        }
                    } else {
                        items(filteredRequests, key = { it.id }) { req ->
                            ApplicationQueueCard(
                                request = req,
                                courseTitle = courseById[req.courseId]?.title ?: req.courseTitle,
                                onOpenReview = { selectedRequest = req }
                            )
                        }
                    }

                    if (isOrg && orgJoinRequests.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Organization Staff Join Requests (Read-Only • ${orgJoinRequests.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        items(orgJoinRequests, key = { it.id }) { joinReq ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(joinReq.userName.ifBlank { joinReq.userId }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(joinReq.orgName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = joinReq.status.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BackpackPrimaryBlue
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "pending" to "Needs Grading (${submissions.count { it.status != "graded" }})",
                                "graded" to "Graded (${submissions.count { it.status == "graded" }})",
                                "all" to "All (${submissions.size})"
                            ).forEach { (key, lbl) ->
                                FilterChip(
                                    selected = gradingFilter == key,
                                    onClick = { gradingFilter = key },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    if (filteredSubmissions.isEmpty()) {
                        item {
                            EmptyReviewStateCard(
                                icon = Icons.Default.Assignment,
                                title = "All Caught Up!",
                                subtitle = "No student assessment submissions match this filter."
                            )
                        }
                    } else {
                        items(filteredSubmissions, key = { it.id }) { submission ->
                            val assessment = assessmentById[submission.assessmentId]
                            val courseTitle = courseById[submission.courseId]?.title ?: submission.courseId
                            AssessmentSubmissionGradingCard(
                                submission = submission,
                                assessment = assessment,
                                courseTitle = courseTitle,
                                isSubmitting = isSubmitting,
                                onSaveEssayDraft = { sub, scores -> viewModel.saveEssayScoresDraft(sub, scores) },
                                onGradeSubmission = { sub, score, fb, essays, done ->
                                    viewModel.gradeSubmission(sub, score, fb, essays, done)
                                },
                                onOpenAttachment = { title, url -> previewDocument = title to url }
                            )
                        }
                    }
                }

                else -> {
                    item {
                        AttendanceReviewTabHeader(
                            attendanceRecords = attendanceRecords,
                            scheduleEventsCount = scheduleEvents.size
                        )
                    }

                    if (scheduleEvents.isNotEmpty()) {
                        item {
                            Text(
                                text = "Scheduled Sessions Check-In (${scheduleEvents.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        items(scheduleEvents, key = { it.id }) { event ->
                            AgendaEventCard(
                                event = event,
                                courseTitle = courseById[event.courseId]?.title,
                                onCheckIn = { viewModel.checkInScheduleEvent(event) }
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Recent Roll Call & Engagement Logs (${attendanceRecords.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (attendanceRecords.isEmpty()) {
                        item {
                            EmptyReviewStateCard(
                                icon = Icons.Default.Checklist,
                                title = "No Attendance Logs Yet",
                                subtitle = "Open any course's Roll Call tab to log class rosters or launch the Physical Kiosk."
                            )
                        }
                    } else {
                        items(attendanceRecords.take(20), key = { it.id }) { rec ->
                            AttendanceLogSummaryCard(rec, courseById[rec.courseId]?.title ?: rec.courseTitle)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    selectedRequest?.let { req ->
        val course = courseById[req.courseId]
        val org = organizations.firstOrNull {
            it.id == req.orgId || it.ownerId == req.orgId || it.id == course?.orgId
        }
        val gate = BackpackSnapshotParser.computeVocationalAdmissionGate(
            course = course,
            organizations = organizations,
            courseDonations = courseDonations,
            enrollmentRequests = allEnrollmentRequests
        )
        StudentApplicationReviewDialog(
            request = req,
            course = course,
            organization = org,
            vocationalGate = gate,
            isSubmitting = isSubmitting,
            onDismiss = { selectedRequest = null },
            onApprove = { viewModel.approveEnrollmentRequest(req) { selectedRequest = null } },
            onReject = { reason -> viewModel.rejectEnrollmentRequest(req, reason) { selectedRequest = null } },
            onPreviewDocument = { title, url -> previewDocument = title to url }
        )
    }

    previewDocument?.let { (title, url) ->
        DocumentPreviewModal(name = title, url = url) { previewDocument = null }
    }
}

@Composable
private fun ApplicationQueueCard(
    request: EnrollmentRequest,
    courseTitle: String,
    onOpenReview: () -> Unit
) {
    val statusColor = when (request.status) {
        "approved" -> BackpackStatusGreen
        "rejected" -> BackpackStatusRed
        else -> BackpackStatusOrange
    }
    val docCount = request.documents.size + request.additionalDocuments.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenReview() }
            .testTag("application_card_${request.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(request.userName.ifBlank { request.userId }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(courseTitle, fontSize = 13.sp, color = BackpackPrimaryBlue, fontWeight = FontWeight.SemiBold)
                    val meta = listOfNotNull(
                        request.sessionName?.ifBlank { null },
                        request.appliedAt.take(10).ifBlank { null },
                        "$docCount docs"
                    ).joinToString(" • ")
                    Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = request.status.uppercase(),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (request.accommodations?.enabled == true) {
                    Surface(color = BackpackPrimaryBlue.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "Accommodations (${request.accommodations.examTimeMultiplier}x)",
                            color = BackpackPrimaryBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (request.reapplicationHistory.isNotEmpty()) {
                    Surface(color = BackpackStatusOrange.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "Reapplication #${request.reapplicationHistory.size + 1}",
                            color = BackpackStatusOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onOpenReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("review_application_btn_${request.id}"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (request.status == "pending") "Review Application & Documents" else "View Application Details",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AttendanceReviewTabHeader(
    attendanceRecords: List<AttendanceRecord>,
    scheduleEventsCount: Int
) {
    val instructorCheckIns = attendanceRecords.count {
        it.instructorEngagement != null || it.entries.any { e -> e.role == "instructor" }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cross-Course Attendance & Self Check-In", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                text = "${attendanceRecords.size} total session logs • $instructorCheckIns with instructor engagement • $scheduleEventsCount scheduled classes. Use Courses -> Roll Call for full roster edits or Kiosk.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AttendanceLogSummaryCard(rec: AttendanceRecord, courseTitle: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(rec.date, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BackpackPrimaryBlue)
                Text("${rec.totalAttendees} Present", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BackpackStatusGreen)
            }
            Text(rec.sessionTitle?.ifBlank { null } ?: "Class Session", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            courseTitle?.ifBlank { null }?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rec.instructorEngagement?.let { inst ->
                Text(
                    text = "Instructor: ${inst.instructorName} (${inst.checkInTime ?: "—"} - ${inst.checkOutTime ?: "—"})",
                    fontSize = 11.sp,
                    color = BackpackPrimaryBlue
                )
            }
        }
    }
}

@Composable
private fun EmptyReviewStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = BackpackPrimaryBlue.copy(alpha = 0.55f), modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun ReviewStatusBanner(roleText: String, pendingApps: Int, pendingGrades: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackpackStatusOrange.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = BackpackStatusOrange, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = roleText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "$pendingApps Pending Apps • $pendingGrades Pending Grades",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BackpackPrimaryBlue
                )
            }
            Text(
                text = "Approve or decline student applications, review & grade assessments, and verify session attendance.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

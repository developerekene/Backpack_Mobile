package com.example.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.ui.theme.BackpackStatusRed
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StudentDashboardView(
    userProfile: UserProfile?,
    enrolledCourses: List<CourseItem>,
    allCourses: List<CourseItem>,
    organizations: List<Organization>,
    enrollmentRequests: List<EnrollmentRequest>,
    orgMembers: List<OrgMember>,
    userProgressList: List<UserProgress>,
    assessments: List<Assessment>,
    submissions: List<Submission>,
    events: List<ScheduleEvent>,
    onCheckIn: (ScheduleEvent) -> Unit,
    onOpenCourse: (CourseItem) -> Unit,
    onNavigateToCourses: () -> Unit
) {
    val context = LocalContext.current
    val orgsById = remember(organizations) { organizations.associateBy { it.id } }
    val allCoursesById = remember(allCourses) { allCourses.associateBy { it.id } }

    // Real total & completed curriculum items across enrolled courses
    val (completedTrackables, totalTrackables) = remember(enrolledCourses, userProgressList) {
        var comp = 0
        var tot = 0
        enrolledCourses.forEach { course ->
            val prog = userProgressList.firstOrNull { it.courseId == course.id }
            val courseModIds = course.modules.map { it.id }.toSet()
            val courseItemIds = course.modules.flatMap { it.items }.map { it.id }.toSet()
            tot += courseModIds.size + courseItemIds.size
            if (prog != null) {
                comp += prog.completedModuleIds.count { it in courseModIds }
                comp += prog.completedItemIds.count { it in courseItemIds }
            }
        }
        comp to tot
    }

    // Real average percentage across graded submissions (or "—" when none)
    val averageScoreText = remember(submissions, assessments) {
        val assessmentsById = assessments.associateBy { it.id }
        val graded = submissions.filter { it.score != null }
        if (graded.isEmpty()) {
            "—"
        } else {
            val avgPct = graded.map { sub ->
                val maxScore = (assessmentsById[sub.assessmentId]?.maxScore ?: 100).coerceAtLeast(1)
                ((sub.score ?: 0.0) / maxScore.toDouble()) * 100.0
            }.average()
            "${avgPct.roundToInt()}%"
        }
    }

    // Tuition due: Approved enrollments for paid courses that are not paid/sponsored/donation-funded
    val unpaidApprovedEnrollments = remember(enrollmentRequests, allCoursesById, orgsById) {
        enrollmentRequests.filter { req ->
            if (req.status != "approved" || req.paymentStatus == "paid" || req.isSponsored) return@filter false
            val course = allCoursesById[req.courseId] ?: return@filter false
            val org = orgsById[course.orgId]
            val isDonationFunded = org?.orgType == "vocational" && course.fundingModel == "donations_sponsorships"
            course.price > 0.0 && !isDonationFunded && course.fundingModel != "donations_sponsorships"
        }
    }

    // Read-only pending / rejected applications and active org course invitations
    val nonEnrolledApplications = remember(enrollmentRequests) {
        enrollmentRequests.filter { it.status != "approved" }
    }
    val pendingInvites = remember(orgMembers) {
        orgMembers.filter { it.status == "invited" }
    }

    val openWebPortal: (String) -> Unit = { path ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://backpack-edu.com$path"))
            context.startActivity(intent)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BackpackPrimaryBlue)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Student Dashboard",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = userProfile?.name.orEmpty()
                                    .ifBlank { userProfile?.displayName.orEmpty().ifBlank { "Student" } },
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            userProfile?.headline?.ifBlank { null }?.let { headline ->
                                Text(
                                    text = headline,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (userProfile?.kycVerified == true) {
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = "KYC Verified",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Verified",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Academic Standing Real Metrics
        item {
            Text(
                text = "Academic Standing",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Enrolled",
                    value = "${enrolledCourses.size}",
                    subtitle = if (enrolledCourses.size == 1) "Course" else "Courses",
                    icon = Icons.Default.School,
                    iconTint = BackpackPrimaryBlue
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Completed",
                    value = if (totalTrackables > 0) "$completedTrackables/$totalTrackables" else "0",
                    subtitle = "Curriculum Items",
                    icon = Icons.Default.CheckCircle,
                    iconTint = BackpackStatusGreen
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Avg. Score",
                    value = averageScoreText,
                    subtitle = "${submissions.count { it.score != null }} Graded",
                    icon = Icons.Default.Analytics,
                    iconTint = BackpackStatusOrange
                )
            }
        }

        // Real Outstanding Tuition Due (only when actual approved unpaid enrollments exist)
        if (unpaidApprovedEnrollments.isNotEmpty()) {
            item {
                Text(
                    text = "Outstanding Tuition Fees (${unpaidApprovedEnrollments.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(unpaidApprovedEnrollments, key = { it.id }) { req ->
                val course = allCoursesById[req.courseId]
                val currency = course?.currency ?: "USD"
                val amount = course?.price ?: 0.0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tuition_payment_card_${req.courseId}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course?.title?.ifBlank { req.courseTitle } ?: req.courseTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Tuition Due: $currency ${String.format(Locale.US, "%,.2f", amount)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BackpackStatusOrange
                            )
                            Text(
                                text = "Complete payment on Backpack Web to unlock classroom.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { openWebPortal("/course/${req.courseId}") },
                            modifier = Modifier.testTag("open_web_payment_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pay on Web", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // My Enrolled Courses Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Courses (${enrolledCourses.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (enrolledCourses.isNotEmpty()) {
                    TextButton(onClick = onNavigateToCourses) {
                        Text("Open Classroom", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (enrolledCourses.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = BackpackPrimaryBlue.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Active Enrolled Courses", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Browse and apply to courses on the Backpack Web Portal. Once approved, they sync here automatically.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { openWebPortal("/explore") },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Explore Courses on Web")
                        }
                    }
                }
            }
        } else {
            items(enrolledCourses, key = { it.id }) { course ->
                val prog = userProgressList.firstOrNull { it.courseId == course.id }
                val pct = course.calculateProgressPercent(prog)
                val orgName = orgsById[course.orgId]?.name
                    ?: organizations.firstOrNull { it.ownerId == course.orgId }?.name

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCourse(course) }
                        .testTag("student_course_card_${course.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = course.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val metaParts = listOfNotNull(
                                    orgName?.ifBlank { null },
                                    course.instructorName?.ifBlank { null }?.let { "Inst: $it" }
                                )
                                if (metaParts.isNotEmpty()) {
                                    Text(
                                        text = metaParts.joinToString(" • "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                color = BackpackPrimaryBlue.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${pct.roundToInt()}%",
                                    color = BackpackPrimaryBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = BackpackPrimaryBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${course.modules.size} Modules • Tap to open classroom",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Read-Only Invitations & Course Applications
        if (pendingInvites.isNotEmpty() || nonEnrolledApplications.isNotEmpty()) {
            item {
                Text(
                    text = "Applications & Invitations (Read-Only)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(pendingInvites, key = { "inv_${it.id}" }) { invite ->
                val orgName = orgsById[invite.orgId]?.name ?: "Organization"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Course Invitation • $orgName", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            invite.inviteNote?.ifBlank { null }?.let {
                                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "Accept or decline on Backpack Web.",
                                fontSize = 11.sp,
                                color = BackpackPrimaryBlue
                            )
                        }
                        OutlinedButton(onClick = { openWebPortal("/notifications") }) {
                            Text("Open Web", fontSize = 12.sp)
                        }
                    }
                }
            }
            items(nonEnrolledApplications, key = { "req_${it.id}" }) { req ->
                val badgeColor = when (req.status.lowercase()) {
                    "rejected", "cancelled" -> BackpackStatusRed
                    else -> BackpackStatusOrange
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(req.courseTitle.ifBlank { "Course Application" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            req.sessionName?.ifBlank { null }?.let {
                                Text("Session: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            req.rejectionReason?.ifBlank { null }?.let {
                                Text("Note: $it", fontSize = 11.sp, color = BackpackStatusRed)
                            }
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = req.status.uppercase(),
                                color = badgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Timetable & Schedule
        item {
            Text(
                text = "Timetable & Live Classes (${events.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (events.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No upcoming timetable events for your enrolled courses.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(events, key = { it.id }) { event ->
                val courseTitle = allCoursesById[event.courseId]?.title
                AgendaEventCard(
                    event = event,
                    courseTitle = courseTitle,
                    onCheckIn = { onCheckIn(event) }
                )
            }
        }
    }
}

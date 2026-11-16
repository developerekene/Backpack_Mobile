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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.ui.theme.BackpackStatusRed
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OrganisationDashboardView(
    userProfile: UserProfile?,
    courses: List<CourseItem>,
    organizations: List<Organization>,
    enrollmentRequests: List<EnrollmentRequest>,
    orgJoinRequests: List<OrgJoinRequest>,
    courseDonations: List<CourseDonation>,
    orgMembers: List<OrgMember>,
    userProgressList: List<UserProgress>,
    assessments: List<Assessment>,
    submissions: List<Submission>,
    attendanceRecords: List<AttendanceRecord>,
    events: List<ScheduleEvent>,
    onOpenCourse: (CourseItem) -> Unit
) {
    val context = LocalContext.current
    val uid = userProfile?.userId.orEmpty()
    val myOrg = organizations.firstOrNull {
        it.id == uid || it.ownerId == uid || it.id == "org_$uid" || it.id == userProfile?.orgId
    }
    val currency = myOrg?.baseCurrency ?: userProfile?.baseCurrency ?: "USD"
    val courseMap = courses.associateBy { it.id }

    // Web AnalyticsOverview.tsx parity: Paid & approved revenue adjusted for installment (/ 3)
    val totalRevenue = remember(enrollmentRequests, courseMap) {
        enrollmentRequests
            .filter { it.status == "approved" && it.paymentStatus == "paid" }
            .sumOf { req ->
                val price = courseMap[req.courseId]?.price ?: 0.0
                if (req.paymentMethod == "installment") price / 3.0 else price
            }
    }

    val uniqueStudentIds = remember(enrollmentRequests, orgMembers) {
        val fromReqs = enrollmentRequests.filter { it.status == "approved" }.map { it.userId }.filter { it.isNotBlank() }
        val fromMembers = orgMembers
            .filter { it.role != "instructor" && (it.status == "active" || it.status == "invited") }
            .map { it.userId ?: it.email }
            .filter { it.isNotBlank() }
        (fromReqs + fromMembers).toSet().size
    }

    val totalModules = courses.sumOf { it.modules.size }
    val avgRetention = remember(userProgressList, courses) {
        if (userProgressList.isEmpty() || courses.isEmpty()) 0
        else {
            val pcts = userProgressList.mapNotNull { prog ->
                val c = courseMap[prog.courseId] ?: return@mapNotNull null
                c.calculateProgressPercent(prog)
            }
            if (pcts.isEmpty()) 0 else pcts.average().roundToInt()
        }
    }

    val physicalRecords = attendanceRecords.filter { (it.mode ?: "physical") == "physical" }
    val onlineRecords = attendanceRecords.filter { it.mode == "online" }
    val physicalMinutes = physicalRecords.sumOf { rec -> rec.entries.sumOf { it.durationMinutes } }
    val onlineMinutes = onlineRecords.sumOf { rec -> rec.entries.sumOf { it.durationMinutes } }
    val instructorLogsCount = attendanceRecords.count {
        it.instructorEngagement != null || it.entries.any { e -> e.role == "instructor" }
    }

    val pendingApps = enrollmentRequests.count { it.status == "pending" }
    val approvedApps = enrollmentRequests.count { it.status == "approved" }
    val pendingSubmissions = submissions.filter { it.status != "graded" }
    val assessmentsById = assessments.associateBy { it.id }
    val totalDonations = courseDonations.sumOf { it.amount }
    val planLabel = if ((myOrg?.plan ?: userProfile?.plan) == "pro") "PRO" else "FREE (${courses.size}/15)"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Institution Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("org_admin_banner"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BackpackPrimaryBlue)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = myOrg?.name?.ifBlank { null }
                            ?: userProfile?.institutionName?.ifBlank { null }
                            ?: userProfile?.displayName?.ifBlank { null }
                            ?: "Organization Dashboard",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val subtitle = listOfNotNull(
                        myOrg?.orgType?.replaceFirstChar { it.uppercase() } ?: userProfile?.orgType?.replaceFirstChar { it.uppercase() },
                        myOrg?.location?.ifBlank { null } ?: userProfile?.location?.ifBlank { null },
                        myOrg?.motto?.ifBlank { null } ?: userProfile?.motto?.ifBlank { null }
                    ).joinToString(" • ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPillBadge(
                            text = if (myOrg?.kycVerified == true || userProfile?.kycVerified == true) "KYC Verified" else "Standard KYC",
                            containerColor = Color.White.copy(alpha = 0.22f),
                            textColor = Color.White
                        )
                        if (myOrg?.isAccredited == true || userProfile?.isAccredited == true) {
                            StatusPillBadge(
                                text = "Accredited",
                                containerColor = BackpackStatusGreen.copy(alpha = 0.9f),
                                textColor = Color.White
                            )
                        }
                        StatusPillBadge(
                            text = planLabel,
                            containerColor = Color.White.copy(alpha = 0.18f),
                            textColor = Color.White
                        )
                    }
                }
            }
        }

        // Read-Only Web Portal Sync Notice
        item {
            ReadOnlyDashboardNotice(
                subtitle = "Institution KPIs, courses & settings are read-only on mobile. Use Reviews or Courses for Discussions, Roll Call, Application Review & Grading.",
                onOpenWeb = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://backpack-edu.com/dashboard")))
                    }
                }
            )
        }

        // 4 KPI Cards (Web AnalyticsOverview.tsx parity)
        item {
            Text(
                text = "Analytics Overview (Read-Only)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Total Revenue",
                        value = String.format(Locale.US, "%s %.0f", currency, totalRevenue),
                        subtitle = "$approvedApps Approved Enrollments",
                        icon = Icons.Default.Payments,
                        iconTint = BackpackStatusGreen
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Active Students",
                        value = "$uniqueStudentIds",
                        subtitle = "${orgMembers.count { it.role == "instructor" }} Instructors",
                        icon = Icons.Default.People,
                        iconTint = BackpackPrimaryBlue
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Published Courses",
                        value = "${courses.size}",
                        subtitle = "$totalModules Total Modules",
                        icon = Icons.Default.School,
                        iconTint = BackpackPrimaryBlue
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Avg Retention",
                        value = "$avgRetention%",
                        subtitle = "${userProgressList.size} Learning Logs",
                        icon = Icons.Default.TrendingUp,
                        iconTint = BackpackStatusOrange
                    )
                }
            }
        }

        // Attendance & Engagement Command Center (Read-Only Summary)
        item {
            AttendanceSummaryCard(
                totalSessions = attendanceRecords.size,
                physicalSessions = physicalRecords.size,
                physicalHours = physicalMinutes / 60.0,
                onlineSessions = onlineRecords.size,
                onlineHours = onlineMinutes / 60.0,
                instructorLogs = instructorLogsCount
            )
        }

        // Admissions & Donations Overview (Read-Only)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Admissions & Organization Queue", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pending Applications: $pendingApps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BackpackStatusOrange)
                        Text("Approved: $approvedApps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BackpackStatusGreen)
                    }
                    if (orgJoinRequests.isNotEmpty()) {
                        Text(
                            text = "Instructor/Staff Join Requests: ${orgJoinRequests.count { it.status == "pending" }} pending (${orgJoinRequests.size} total)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (totalDonations > 0.0 || myOrg?.orgType == "vocational") {
                        Text(
                            text = "Vocational Sponsorship Donations: $currency ${String.format(Locale.US, "%.2f", totalDonations)} (${courseDonations.size} gifts)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = BackpackPrimaryBlue
                        )
                    }
                }
            }
        }

        // Assessment Overview
        item {
            DashboardAssessmentOverviewCard(
                assessments = assessments,
                pendingSubmissions = pendingSubmissions,
                assessmentsById = assessmentsById,
                courseMap = courseMap,
                onOpenCourse = onOpenCourse
            )
        }

        // Courses Section
        item {
            Text(
                text = "Courses & Intake Sessions (${courses.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        if (courses.isEmpty()) {
            item {
                DashboardEmptyCard("No courses published yet. Create courses on the Backpack Web Portal.")
            }
        } else {
            items(courses, key = { it.id }) { course ->
                DashboardCourseCard(
                    course = course,
                    onOpenCourse = { onOpenCourse(course) }
                )
            }
        }

        // Schedule Events Section (Read-Only for Org)
        item {
            Text(
                text = "Schedule Events (${events.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        if (events.isEmpty()) {
            item { DashboardEmptyCard("No scheduled class events.") }
        } else {
            items(events, key = { it.id }) { event ->
                AgendaEventCard(
                    event = event,
                    courseTitle = courseMap[event.courseId]?.title,
                    onCheckIn = {}
                )
            }
        }
    }
}

@Composable
fun InstructorDashboardView(
    userProfile: UserProfile?,
    courses: List<CourseItem>,
    organizations: List<Organization>,
    enrollmentRequests: List<EnrollmentRequest>,
    orgMembers: List<OrgMember>,
    assessments: List<Assessment>,
    submissions: List<Submission>,
    attendanceRecords: List<AttendanceRecord>,
    events: List<ScheduleEvent>,
    onCheckIn: (ScheduleEvent) -> Unit,
    onOpenCourse: (CourseItem) -> Unit
) {
    val context = LocalContext.current
    var showOrgsDialog by remember { mutableStateOf(false) }

    val courseMap = courses.associateBy { it.id }
    val linkedOrgs = remember(courses, organizations, userProfile) {
        val ids = (courses.map { it.orgId } + listOfNotNull(userProfile?.orgId)).filter { it.isNotBlank() }.toSet()
        organizations.filter { o ->
            o.id in ids || o.ownerId in ids || "org_${o.ownerId}" in ids
        }
    }

    val uniqueStudentCount = remember(enrollmentRequests, orgMembers) {
        val fromReqs = enrollmentRequests.filter { it.status == "approved" }.map { it.userId }.filter { it.isNotBlank() }
        val fromMem = orgMembers.filter { it.role != "instructor" }.map { it.userId ?: it.email }.filter { it.isNotBlank() }
        (fromReqs + fromMem).toSet().size
    }

    val pendingSubmissions = submissions.filter { it.status != "graded" }
    val assessmentsById = assessments.associateBy { it.id }
    val physicalRecords = attendanceRecords.filter { (it.mode ?: "physical") == "physical" }
    val onlineRecords = attendanceRecords.filter { it.mode == "online" }
    val physicalMinutes = physicalRecords.sumOf { rec -> rec.entries.sumOf { it.durationMinutes } }
    val onlineMinutes = onlineRecords.sumOf { rec -> rec.entries.sumOf { it.durationMinutes } }
    val instructorLogs = attendanceRecords.count {
        it.instructorEngagement != null || it.entries.any { e -> e.role == "instructor" }
    }

    if (showOrgsDialog) {
        AssociatedOrganisationsDialog(
            organizations = linkedOrgs,
            fallbackOrgId = userProfile?.orgId,
            onDismiss = { showOrgsDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("instructor_banner"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BackpackPrimaryBlue)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = userProfile?.displayName?.ifBlank { null } ?: "Faculty Instructor",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    userProfile?.headline?.ifBlank { null }?.let {
                        Text(it, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp)
                    }
                    if (linkedOrgs.isNotEmpty()) {
                        Text(
                            text = linkedOrgs.joinToString(" • ") { it.name },
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPillBadge("Instructor", Color.White.copy(alpha = 0.22f), Color.White)
                        if (userProfile?.kycVerified == true) {
                            StatusPillBadge("KYC Verified", BackpackStatusGreen.copy(alpha = 0.9f), Color.White)
                        }
                    }
                }
            }
        }

        item {
            ReadOnlyDashboardNotice(
                subtitle = "Overview & analytics are read-only. Use Courses or Reviews for Discussions, Roll Call, Self Check-In, Application Review & Grading.",
                onOpenWeb = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://backpack-edu.com/dashboard")))
                    }
                }
            )
        }

        item {
            Text(
                text = "Faculty Overview (Read-Only)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Assigned Courses",
                        value = "${courses.size}",
                        subtitle = "${courses.sumOf { it.modules.size }} Modules",
                        icon = Icons.Default.School,
                        iconTint = BackpackPrimaryBlue
                    )
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showOrgsDialog = true }
                            .testTag("linked_orgs_card"),
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
                                    text = "Organisations",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = "View organisations",
                                    tint = BackpackStatusGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val linkCount = maxOf(linkedOrgs.size, if (userProfile?.orgId.isNullOrBlank()) 0 else 1)
                            Text(
                                text = "$linkCount Linked",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Tap to view details",
                                fontSize = 10.sp,
                                color = BackpackPrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Cohort Students",
                        value = "$uniqueStudentCount",
                        subtitle = "${enrollmentRequests.count { it.status == "pending" }} Pending Apps",
                        icon = Icons.Default.People,
                        iconTint = BackpackStatusGreen
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Pending Grading",
                        value = "${pendingSubmissions.size}",
                        subtitle = "${assessments.size} Assessments",
                        icon = Icons.Default.RateReview,
                        iconTint = if (pendingSubmissions.isNotEmpty()) BackpackStatusOrange else BackpackPrimaryBlue
                    )
                }
            }
        }

        item {
            AttendanceSummaryCard(
                totalSessions = attendanceRecords.size,
                physicalSessions = physicalRecords.size,
                physicalHours = physicalMinutes / 60.0,
                onlineSessions = onlineRecords.size,
                onlineHours = onlineMinutes / 60.0,
                instructorLogs = instructorLogs
            )
        }

        item {
            DashboardAssessmentOverviewCard(
                assessments = assessments,
                pendingSubmissions = pendingSubmissions,
                assessmentsById = assessmentsById,
                courseMap = courseMap,
                onOpenCourse = onOpenCourse
            )
        }

        item {
            Text(
                text = "Assigned Courses (${courses.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        if (courses.isEmpty()) {
            item { DashboardEmptyCard("No courses assigned to you yet.") }
        } else {
            items(courses, key = { it.id }) { course ->
                DashboardCourseCard(
                    course = course,
                    onOpenCourse = { onOpenCourse(course) }
                )
            }
        }

        item {
            Text(
                text = "Schedule Events & Check-In (${events.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        if (events.isEmpty()) {
            item { DashboardEmptyCard("No scheduled class events.") }
        } else {
            items(events, key = { it.id }) { event ->
                AgendaEventCard(
                    event = event,
                    courseTitle = courseMap[event.courseId]?.title,
                    onCheckIn = { onCheckIn(event) }
                )
            }
        }
    }
}

@Composable
private fun StatusPillBadge(text: String, containerColor: Color, textColor: Color) {
    Surface(color = containerColor, shape = RoundedCornerShape(10.dp)) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ReadOnlyDashboardNotice(subtitle: String, onOpenWeb: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onOpenWeb,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Web", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(3.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun AttendanceSummaryCard(
    totalSessions: Int,
    physicalSessions: Int,
    physicalHours: Double,
    onlineSessions: Int,
    onlineHours: Double,
    instructorLogs: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Attendance Command Telemetry", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("$totalSessions Sessions", fontSize = 11.sp, color = BackpackPrimaryBlue, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("In-Person Campus", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.US, "%.1f hrs", physicalHours),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = BackpackPrimaryBlue
                        )
                        Text("$physicalSessions logs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Online Live", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.US, "%.1f hrs", onlineHours),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = BackpackStatusGreen
                        )
                        Text("$onlineSessions logs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                text = "Instructor check-in sessions recorded: $instructorLogs",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardAssessmentOverviewCard(
    assessments: List<Assessment>,
    pendingSubmissions: List<Submission>,
    assessmentsById: Map<String, Assessment>,
    courseMap: Map<String, CourseItem>,
    onOpenCourse: (CourseItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Assessments & Grading Queue", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Surface(
                    color = if (pendingSubmissions.isNotEmpty()) BackpackStatusOrange.copy(alpha = 0.14f)
                    else BackpackStatusGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (pendingSubmissions.isNotEmpty()) "${pendingSubmissions.size} Needs Grading" else "All Graded",
                        color = if (pendingSubmissions.isNotEmpty()) BackpackStatusOrange else BackpackStatusGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (pendingSubmissions.isEmpty()) {
                Text(
                    text = "${assessments.size} published assessments • No pending submissions.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pendingSubmissions.take(4).forEach { sub ->
                    val ass = assessmentsById[sub.assessmentId]
                    val course = courseMap[sub.courseId]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = course != null) { course?.let(onOpenCourse) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${sub.userName ?: sub.userId} • ${ass?.title ?: "Assessment"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = course?.title ?: sub.courseId,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open course", tint = BackpackPrimaryBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCourseCard(
    course: CourseItem,
    onOpenCourse: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenCourse() }
            .testTag("dashboard_course_card_${course.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                val isOpen = course.admissionStatus == "open"
                StatusPillBadge(
                    text = if (isOpen) "OPEN" else "CLOSED",
                    containerColor = (if (isOpen) BackpackStatusGreen else BackpackStatusRed).copy(alpha = 0.14f),
                    textColor = if (isOpen) BackpackStatusGreen else BackpackStatusRed
                )
            }
            val priceLabel = if (course.fundingModel == "donations_sponsorships") {
                "Donation Sponsored"
            } else if (course.price <= 0.0) {
                "Free"
            } else {
                "${course.currency} ${course.price}"
            }
            val meta = listOfNotNull(
                "${course.modules.size} modules",
                priceLabel,
                course.activeSessionName?.ifBlank { null }?.let { "Session: $it" }
            ).joinToString(" • ")
            Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedButton(
                onClick = onOpenCourse,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("open_classroom_btn_${course.id}"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Open Classroom", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun DashboardEmptyCard(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun AssociatedOrganisationsDialog(
    organizations: List<Organization>,
    fallbackOrgId: String?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Associated Organisations",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Icon(Icons.Default.Business, contentDescription = null, tint = BackpackPrimaryBlue)
                }

                if (organizations.isEmpty()) {
                    Text(
                        text = if (!fallbackOrgId.isNullOrBlank()) {
                            "Linked Organization ID: $fallbackOrgId"
                        } else {
                            "No linked organizations found in your faculty profile."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    organizations.forEach { org ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(org.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                val info = listOfNotNull(
                                    org.orgType?.replaceFirstChar { it.uppercase() },
                                    org.location?.ifBlank { null },
                                    if (org.kycVerified) "KYC Verified" else null
                                ).joinToString(" • ")
                                if (info.isNotBlank()) {
                                    Text(info, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

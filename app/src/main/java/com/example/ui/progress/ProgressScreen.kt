package com.example.ui.progress

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
fun ProgressScreen(
    viewModel: ProgressViewModel,
    userProfile: UserProfile?
) {
    val enrolledCourses by viewModel.enrolledCourses.collectAsState()
    val organizations by viewModel.organizations.collectAsState()
    val userProgressList by viewModel.userProgressList.collectAsState()
    val assessments by viewModel.assessments.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val attendanceRecords by viewModel.attendanceRecords.collectAsState()

    val uid = userProfile?.userId?.ifBlank { null } ?: viewModel.currentUserId()
    val coursesById = remember(enrolledCourses) { enrolledCourses.associateBy { it.id } }
    val assessmentsById = remember(assessments) { assessments.associateBy { it.id } }
    val orgsById = remember(organizations) { organizations.associateBy { it.id } }

    // Curriculum completion totals
    val (completedTrackables, totalTrackables) = remember(enrolledCourses, userProgressList) {
        var comp = 0
        var tot = 0
        enrolledCourses.forEach { course ->
            val prog = userProgressList.firstOrNull { it.courseId == course.id }
            val modIds = course.modules.map { it.id }.toSet()
            val itemIds = course.modules.flatMap { it.items }.map { it.id }.toSet()
            tot += modIds.size + itemIds.size
            if (prog != null) {
                comp += prog.completedModuleIds.count { it in modIds }
                comp += prog.completedItemIds.count { it in itemIds }
            }
        }
        comp to tot
    }
    val curriculumPct = if (totalTrackables > 0) ((completedTrackables.toDouble() / totalTrackables) * 100.0).roundToInt() else 0

    // Real graded average & GPA
    val gradedSubmissions = remember(submissions) { submissions.filter { it.score != null } }
    val avgScorePct = remember(gradedSubmissions, assessmentsById) {
        if (gradedSubmissions.isEmpty()) null
        else gradedSubmissions.map { sub ->
            val max = (assessmentsById[sub.assessmentId]?.maxScore ?: 100).coerceAtLeast(1)
            ((sub.score ?: 0.0) / max.toDouble()) * 100.0
        }.average()
    }
    val gpaText = avgScorePct?.let {
        String.format(Locale.US, "%.2f GPA", (it / 25.0).coerceIn(0.0, 4.0))
    }

    // Real Attendance stats for this student
    val myAttendanceSessions = remember(attendanceRecords, uid) {
        attendanceRecords.mapNotNull { rec ->
            val myEntry = rec.entries.firstOrNull { it.userId == uid }
            val legacyPresent = rec.records[uid]
            when {
                myEntry != null -> rec to myEntry
                legacyPresent != null -> rec to AttendanceEntry(
                    id = "rec_${rec.id}",
                    userId = uid,
                    status = if (legacyPresent) "present" else "absent",
                    checkInTime = rec.startTime,
                    checkOutTime = rec.endTime
                )
                else -> null
            }
        }
    }
    val presentSessionsCount = remember(myAttendanceSessions) {
        myAttendanceSessions.count { (_, e) -> e.status == "present" || e.status == "late" }
    }
    val attendanceRateText = remember(attendanceRecords, presentSessionsCount) {
        if (attendanceRecords.isEmpty()) "—"
        else "${((presentSessionsCount.toDouble() / attendanceRecords.size) * 100.0).roundToInt()}%"
    }
    val totalEngagementMins = remember(myAttendanceSessions) {
        myAttendanceSessions.sumOf { (_, e) -> e.durationMinutes }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("student_progress_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_overview_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BackpackPrimaryBlue)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Academic Transcript & Analytics (Read-Only)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ProgressMetricItem("Curriculum", if (totalTrackables > 0) "$curriculumPct%" else "0%")
                        ProgressMetricItem("Avg. Grade", avgScorePct?.let { "${it.roundToInt()}%" } ?: "—")
                        ProgressMetricItem("Attendance", attendanceRateText)
                        ProgressMetricItem("Engagement", "${String.format(Locale.US, "%.1f", totalEngagementMins / 60.0)}h")
                    }
                    if (gpaText != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Estimated Standing: $gpaText • ${gradedSubmissions.size} Graded Assessments • $completedTrackables/$totalTrackables Modules & Lessons",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.sp
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${enrolledCourses.size} Enrolled Courses • $completedTrackables/$totalTrackables Curriculum Items Completed",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Course-by-Course Breakdown
        item {
            Text(
                text = "Course Breakdown (${enrolledCourses.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (enrolledCourses.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No enrolled courses synchronized yet.", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Apply and enroll on the Backpack Web Portal to track curriculum progress and academic grades here.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(enrolledCourses, key = { it.id }) { course ->
                val prog = userProgressList.firstOrNull { it.courseId == course.id }
                val pct = course.calculateProgressPercent(prog)
                val orgName = orgsById[course.orgId]?.name
                    ?: organizations.firstOrNull { it.ownerId == course.orgId }?.name
                val courseAssessments = assessments.filter { it.courseId == course.id }
                val courseSubs = submissions.filter { it.courseId == course.id }
                val courseAttendance = attendanceRecords.filter {
                    it.courseId == course.id || (!it.courseTitle.isNullOrBlank() && it.courseTitle == course.title)
                }
                val coursePresent = myAttendanceSessions.count { (rec, e) ->
                    (rec.courseId == course.id || (!rec.courseTitle.isNullOrBlank() && rec.courseTitle == course.title)) &&
                        (e.status == "present" || e.status == "late")
                }

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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(course.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                orgName?.ifBlank { null }?.let {
                                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text = "${pct.roundToInt()}%",
                                fontWeight = FontWeight.ExtraBold,
                                color = BackpackPrimaryBlue,
                                fontSize = 14.sp
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = BackpackPrimaryBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Submissions: ${courseSubs.size}/${courseAssessments.size}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (courseAttendance.isEmpty()) "Attendance: No sessions"
                                else "Attendance: $coursePresent/${courseAttendance.size}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Grades & Submissions Log
        item {
            Text(
                text = "Grades & Submissions (${submissions.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (submissions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "No submissions recorded yet.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Submit assignments, classwork, or projects inside My Courses to track grades and instructor feedback.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(submissions, key = { it.id }) { submission ->
                val assessment = assessmentsById[submission.assessmentId]
                val courseTitle = coursesById[submission.courseId]?.title ?: submission.courseId
                SubmissionCard(
                    submission = submission,
                    assessmentTitle = assessment?.title,
                    assessmentType = assessment?.type,
                    maxScore = assessment?.maxScore ?: 100,
                    courseTitle = courseTitle
                )
            }
        }

        // Attendance History
        item {
            Text(
                text = "Recorded Attendance History (${myAttendanceSessions.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (myAttendanceSessions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null, tint = BackpackStatusGreen)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "No attendance check-ins logged yet. Use the Attendance tab in My Courses to check in.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(myAttendanceSessions.take(15), key = { "${it.first.id}_${it.second.id}" }) { (rec, entry) ->
                val courseTitle = rec.courseTitle?.ifBlank { null } ?: coursesById[rec.courseId]?.title ?: rec.courseId
                val statusColor = when (entry.status.lowercase()) {
                    "present" -> BackpackStatusGreen
                    "late" -> BackpackStatusOrange
                    "absent" -> BackpackStatusRed
                    else -> BackpackPrimaryBlue
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(courseTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${rec.date} • ${if (rec.mode == "online") "Online Live" else "In-Person"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val timeStr = listOfNotNull(
                                entry.checkInTime?.let { "In: $it" },
                                entry.checkOutTime?.let { "Out: $it" },
                                if (entry.durationMinutes > 0) "${entry.durationMinutes}m" else null
                            ).joinToString(" • ")
                            if (timeStr.isNotBlank()) {
                                Text(timeStr, fontSize = 11.sp, color = BackpackPrimaryBlue)
                            }
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = entry.status.uppercase(),
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubmissionCard(
    submission: Submission,
    assessmentTitle: String?,
    assessmentType: String?,
    maxScore: Int,
    courseTitle: String
) {
    val isGraded = submission.status.equals("graded", ignoreCase = true) || submission.score != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = assessmentTitle?.ifBlank { null } ?: "Assessment Submission",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                val meta = listOfNotNull(
                    courseTitle.ifBlank { null },
                    assessmentType?.uppercase()
                ).joinToString(" • ")
                Text(text = meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                submission.score?.let { sc ->
                    Text(
                        text = "Score: ${sc.roundToInt()} / $maxScore (${((sc / maxScore.coerceAtLeast(1)) * 100.0).roundToInt()}%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BackpackStatusGreen
                    )
                }
                submission.feedback?.ifBlank { null }?.let { fb ->
                    Text(
                        text = "Feedback: $fb",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Submitted: ${submission.submittedAt.take(10)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (isGraded) BackpackStatusGreen.copy(alpha = 0.12f) else BackpackStatusOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isGraded) "GRADED" else submission.status.uppercase(),
                    color = if (isGraded) BackpackStatusGreen else BackpackStatusOrange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ProgressMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}

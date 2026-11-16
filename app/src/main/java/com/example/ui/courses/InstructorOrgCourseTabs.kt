package com.example.ui.courses

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.submissions.AssessmentSubmissionGradingCard
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.ui.theme.BackpackStatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// =========================================================================
// ASSESSMENT REVIEW & GRADING TAB (Instructor / Organization)
// =========================================================================

fun LazyListScope.instructorOrgAssessmentsTab(
    course: CourseItem,
    assessments: List<Assessment>,
    submissions: List<Submission>,
    isSubmitting: Boolean,
    onSaveEssayDraft: (Submission, Map<String, Double>) -> Unit,
    onGradeSubmission: (Submission, Double, String, Map<String, Double>?, () -> Unit) -> Unit,
    onOpenAttachment: (String, String?) -> Unit
) {
    val totalPending = submissions.count { it.status != "graded" }
    val totalGraded = submissions.count { it.status == "graded" }

    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("instructor_grading_summary_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackpackPrimaryBlue)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Assessment Review & Grading", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "${assessments.size} assessments • ${submissions.size} submissions",
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$totalPending Pending", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("$totalGraded Graded", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                }
            }
        }
    }

    if (assessments.isEmpty()) {
        item {
            EmptyStateText("No assessments created for ${course.title} yet. Author new assessments on the Backpack Web Portal.")
        }
    } else {
        items(assessments, key = { it.id }) { assessment ->
            val matchedSubs = submissions.filter { it.assessmentId == assessment.id }
            InstructorAssessmentAccordionCard(
                assessment = assessment,
                submissions = matchedSubs,
                isSubmitting = isSubmitting,
                onSaveEssayDraft = onSaveEssayDraft,
                onGradeSubmission = onGradeSubmission,
                onOpenAttachment = onOpenAttachment
            )
        }
    }
}

@Composable
private fun InstructorAssessmentAccordionCard(
    assessment: Assessment,
    submissions: List<Submission>,
    isSubmitting: Boolean,
    onSaveEssayDraft: (Submission, Map<String, Double>) -> Unit,
    onGradeSubmission: (Submission, Double, String, Map<String, Double>?, () -> Unit) -> Unit,
    onOpenAttachment: (String, String?) -> Unit
) {
    val pendingCount = submissions.count { it.status != "graded" }
    var expandedSubs by remember(assessment.id) { mutableStateOf(pendingCount > 0) }
    var showPreview by remember(assessment.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("instructor_assessment_card_${assessment.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedSubs = !expandedSubs },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = BackpackPrimaryBlue.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = assessment.type.uppercase(),
                                color = BackpackPrimaryBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        if (pendingCount > 0) {
                            Surface(
                                color = BackpackStatusOrange.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "$pendingCount PENDING",
                                    color = BackpackStatusOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(assessment.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val meta = listOfNotNull(
                        assessment.subject?.ifBlank { null },
                        assessment.gradeLevel?.ifBlank { null },
                        if (assessment.dueDate.isNotBlank()) "Due ${assessment.dueDate.take(10)}" else null,
                        "${assessment.maxScore} pts",
                        "${submissions.size} submissions"
                    ).joinToString(" • ")
                    Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Icon(
                    imageVector = if (expandedSubs) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expandedSubs) "Collapse" else "Expand"
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showPreview = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview (Read-Only)", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { expandedSubs = !expandedSubs },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (expandedSubs) "Hide Submissions" else "Review (${submissions.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (expandedSubs) {
                if (submissions.isEmpty()) {
                    Text(
                        text = "No student submissions received yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    submissions.forEach { sub ->
                        AssessmentSubmissionGradingCard(
                            submission = sub,
                            assessment = assessment,
                            isSubmitting = isSubmitting,
                            onSaveEssayDraft = onSaveEssayDraft,
                            onGradeSubmission = onGradeSubmission,
                            onOpenAttachment = onOpenAttachment
                        )
                    }
                }
            }
        }
    }

    if (showPreview) {
        AssessmentReadOnlyPreviewDialog(
            assessment = assessment,
            onDismiss = { showPreview = false },
            onOpenAttachment = onOpenAttachment
        )
    }
}

@Composable
private fun AssessmentReadOnlyPreviewDialog(
    assessment: Assessment,
    onDismiss: () -> Unit,
    onOpenAttachment: (String, String?) -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Assessment Preview", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close preview")
                    }
                }
                Text(assessment.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BackpackPrimaryBlue)
                assessment.description?.ifBlank { null }?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (assessment.attachments.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        assessment.attachments.forEachIndexed { idx, url ->
                            AssistChip(
                                onClick = {
                                    if (url.startsWith("http", ignoreCase = true)) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    } else {
                                        onOpenAttachment("${assessment.title} #${idx + 1}", url)
                                    }
                                },
                                label = { Text("Attachment ${idx + 1}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
                if (assessment.questions.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Questions (${assessment.questions.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    assessment.questions.take(8).forEachIndexed { i, q ->
                        Text("${i + 1}. ${q.prompt} (${q.points} pts)", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// =========================================================================
// ROLL CALL, KIOSK & SELF CHECK-IN TAB (Instructor / Organization)
// =========================================================================

fun LazyListScope.instructorOrgAttendanceTab(
    course: CourseItem,
    currentUserId: String,
    attendanceRecords: List<AttendanceRecord>,
    orgMembers: List<OrgMember>,
    approvedEnrollments: List<EnrollmentRequest>,
    activeMode: String,
    onModeChange: (String) -> Unit,
    isSubmitting: Boolean,
    lastStatusMessage: String?,
    onClockIn: (mode: String, location: String) -> Unit,
    onClockOut: (mode: String) -> Unit,
    onSaveRollCall: (
        date: String,
        mode: String,
        sessionTitle: String,
        location: String,
        entries: List<AttendanceEntry>,
        existingRecord: AttendanceRecord?,
        onDone: () -> Unit
    ) -> Unit,
    onKioskClock: (CohortParticipant, action: String) -> Unit,
    onDeleteRecord: (AttendanceRecord) -> Unit
) {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val clockMode = if (activeMode == "online") "online" else "physical"
    val roster = buildRealCourseRoster(course, orgMembers, approvedEnrollments)

    val modeRecords = attendanceRecords.filter { rec ->
        when (activeMode) {
            "physical" -> (rec.mode ?: "physical") == "physical"
            "online" -> rec.mode == "online"
            else -> true
        }
    }
    val todayPhysicalRecord = attendanceRecords.firstOrNull {
        it.date == today && (it.mode ?: "physical") == "physical"
    }
    val todayClockRecord = attendanceRecords.firstOrNull {
        it.date == today && (it.mode ?: "physical") == clockMode
    }
    val myTodayEntry = todayClockRecord?.entries?.firstOrNull { it.userId == currentUserId }
    val isCheckedIn = myTodayEntry != null
    val hasCheckedOut = !myTodayEntry?.checkOutTime.isNullOrBlank()

    // Analytics metrics matching Web CourseAttendance.tsx
    var studentMins = 0
    var instructorMins = 0
    var presentTotal = 0
    var entryTotal = 0
    modeRecords.forEach { rec ->
        rec.entries.forEach { e ->
            entryTotal++
            if (e.status == "present" || e.status == "late") presentTotal++
            if (e.role == "instructor") instructorMins += e.durationMinutes else studentMins += e.durationMinutes
        }
    }
    val attendanceRate = if (entryTotal > 0) ((presentTotal * 100.0) / entryTotal).roundToInt() else 0

    // Mode Filter Chips
    item {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("physical" to "In-Person", "online" to "Online Live", "all" to "Combined").forEach { (key, label) ->
                FilterChip(
                    selected = activeMode == key,
                    onClick = { onModeChange(key) },
                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    }

    // Roll Call & Physical Kiosk Action Toolbar + Modals State
    item {
        var showRollCallDialog by remember { mutableStateOf(false) }
        var editingRecord by remember { mutableStateOf<AttendanceRecord?>(null) }
        var showKioskDialog by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    editingRecord = null
                    showRollCallDialog = true
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("take_roll_call_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
            ) {
                Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Take Roll Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showKioskDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("open_kiosk_station_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Kiosk Station", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showRollCallDialog) {
            TakeAttendanceModal(
                course = course,
                roster = roster,
                defaultMode = clockMode,
                existingRecord = editingRecord,
                isSubmitting = isSubmitting,
                onDismiss = { showRollCallDialog = false },
                onSave = { date, mode, title, loc, entries ->
                    onSaveRollCall(date, mode, title, loc, entries, editingRecord) {
                        showRollCallDialog = false
                    }
                }
            )
        }

        if (showKioskDialog) {
            KioskModal(
                course = course,
                roster = roster,
                todayRecord = todayPhysicalRecord,
                isSubmitting = isSubmitting,
                lastStatusMessage = lastStatusMessage,
                onDismiss = { showKioskDialog = false },
                onClockParticipant = { person, act -> onKioskClock(person, act) }
            )
        }
    }

    // Instructor / Org Self Check-In Card
    item {
        var locInput by remember(clockMode) {
            mutableStateOf(if (clockMode == "online") "Online Live Classroom" else "Main Campus Station")
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("instructor_self_checkin_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (clockMode == "online") "Instructor Online Check-In" else "Instructor Campus Check-In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = when {
                            hasCheckedOut -> "COMPLETED (${myTodayEntry?.durationMinutes ?: 0}m)"
                            isCheckedIn -> "CHECKED IN (${myTodayEntry?.checkInTime ?: "—"})"
                            else -> "NOT CLOCKED IN"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCheckedIn) BackpackStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = locInput,
                    onValueChange = { locInput = it },
                    label = { Text("Room / Classroom Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onClockIn(clockMode, locInput) },
                        enabled = !isSubmitting && !isCheckedIn,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("instructor_checkin_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Check In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onClockOut(clockMode) },
                        enabled = !isSubmitting && isCheckedIn && !hasCheckedOut,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("instructor_checkout_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Check Out", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // 4-KPI Summary
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RollCallKpiCard(Modifier.weight(1f), "Sessions", "${modeRecords.size}")
                RollCallKpiCard(Modifier.weight(1f), "Attendance Rate", "$attendanceRate%")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RollCallKpiCard(
                    Modifier.weight(1f),
                    "Student Time",
                    String.format(Locale.US, "%.1f hrs", studentMins / 60.0)
                )
                RollCallKpiCard(
                    Modifier.weight(1f),
                    "Instructor Time",
                    String.format(Locale.US, "%.1f hrs", instructorMins / 60.0)
                )
            }
        }
    }

    item {
        Text(
            text = "Roll Call Sessions (${modeRecords.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }

    if (modeRecords.isEmpty()) {
        item {
            EmptyStateText("No attendance records logged yet. Tap Take Roll Call or Kiosk Station above.")
        }
    } else {
        items(modeRecords, key = { it.id }) { rec ->
            InstructorAttendanceSessionCard(
                rec = rec,
                course = course,
                roster = roster,
                isSubmitting = isSubmitting,
                onSaveEdit = { date, mode, title, loc, entries, done ->
                    onSaveRollCall(date, mode, title, loc, entries, rec, done)
                },
                onDelete = { onDeleteRecord(rec) }
            )
        }
    }
}

@Composable
private fun RollCallKpiCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = BackpackPrimaryBlue)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InstructorAttendanceSessionCard(
    rec: AttendanceRecord,
    course: CourseItem,
    roster: List<CohortParticipant>,
    isSubmitting: Boolean,
    onSaveEdit: (String, String, String, String, List<AttendanceEntry>, () -> Unit) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember(rec.id) { mutableStateOf(false) }
    var showEditModal by remember(rec.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rec.date, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BackpackPrimaryBlue)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = if (rec.mode == "online") "ONLINE LIVE" else "IN-PERSON",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${rec.totalAttendees} Present",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BackpackStatusGreen
                )
            }

            Text(rec.sessionTitle?.ifBlank { null } ?: "Class Session", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            rec.location?.ifBlank { null }?.let {
                Text("Location: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val presentCount = rec.entries.count { it.status == "present" }
            val lateCount = rec.entries.count { it.status == "late" }
            val absentCount = rec.entries.count { it.status == "absent" }
            Text(
                text = "$presentCount Present • $lateCount Late • $absentCount Absent",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rec.instructorEngagement?.let { inst ->
                Surface(
                    color = BackpackPrimaryBlue.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Instructor: ${inst.instructorName} (${inst.status.uppercase()} • ${inst.checkInTime ?: "—"} - ${inst.checkOutTime ?: "—"})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = BackpackPrimaryBlue,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showEditModal = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Roster", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = BackpackStatusRed, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 11.sp, color = BackpackStatusRed)
                }
            }

            if (expanded && rec.entries.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                rec.entries.forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${e.userName} (${e.role})", fontSize = 11.sp)
                        Text(
                            text = "${e.status.uppercase()} • ${e.checkInTime ?: "—"} (${e.durationMinutes}m)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showEditModal) {
        TakeAttendanceModal(
            course = course,
            roster = roster,
            defaultMode = rec.mode ?: "physical",
            existingRecord = rec,
            isSubmitting = isSubmitting,
            onDismiss = { showEditModal = false },
            onSave = { date, mode, title, loc, entries ->
                onSaveEdit(date, mode, title, loc, entries) { showEditModal = false }
            }
        )
    }
}

// =========================================================================
// DISCUSSION MODERATION DIALOGS (Create Channel & Publish Poll)
// =========================================================================

@Composable
fun CreateDiscussionChannelDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, onDone: () -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Create Discussion Channel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel Name (e.g. module-1-qa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(name, description.ifBlank { null }, onDismiss) },
                        enabled = !isSubmitting && name.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                    ) {
                        Text("Create Channel", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateDiscussionPollDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onCreate: (question: String, options: List<String>, onDone: () -> Unit) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("") }
    var opt2 by remember { mutableStateOf("") }
    var opt3 by remember { mutableStateOf("") }
    var opt4 by remember { mutableStateOf("") }

    val validOpts = listOf(opt1, opt2, opt3, opt4).map { it.trim() }.filter { it.isNotBlank() }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Publish Cohort Poll", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Poll Question") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = opt1, onValueChange = { opt1 = it }, label = { Text("Option 1 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = opt2, onValueChange = { opt2 = it }, label = { Text("Option 2 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = opt3, onValueChange = { opt3 = it }, label = { Text("Option 3 (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = opt4, onValueChange = { opt4 = it }, label = { Text("Option 4 (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(question, validOpts, onDismiss) },
                        enabled = !isSubmitting && question.isNotBlank() && validOpts.size >= 2,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                    ) {
                        Text("Publish Poll", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

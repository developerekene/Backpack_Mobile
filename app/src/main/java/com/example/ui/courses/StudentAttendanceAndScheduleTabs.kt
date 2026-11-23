package com.example.ui.courses

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.ui.theme.BackpackStatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun LazyListScope.studentAttendanceTab(
    course: CourseItem,
    currentUserId: String,
    attendanceRecords: List<AttendanceRecord>,
    activeMode: String, // "physical" | "online" | "all"
    onModeChange: (String) -> Unit,
    isSubmitting: Boolean,
    onClockIn: (mode: String, location: String) -> Unit,
    onClockOut: (mode: String) -> Unit
) {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val clockMode = if (activeMode == "online") "online" else "physical"

    val modeRecords = attendanceRecords.filter { rec ->
        when (activeMode) {
            "physical" -> (rec.mode ?: "physical") == "physical"
            "online" -> rec.mode == "online"
            else -> true
        }
    }

    val todayRecord = attendanceRecords.firstOrNull { rec ->
        rec.date == today && (rec.mode ?: "physical") == clockMode
    }
    val myTodayEntry = todayRecord?.entries?.firstOrNull { it.userId == currentUserId }
    val isCheckedInToday = myTodayEntry != null
    val hasCheckedOutToday = !myTodayEntry?.checkOutTime.isNullOrBlank()

    // Calculate real student attendance telemetry
    var myPresentCount = 0
    var myTotalSessions = 0
    var myTotalMinutes = 0
    modeRecords.forEach { rec ->
        val mine = rec.entries.firstOrNull { it.userId == currentUserId }
        if (mine != null) {
            myTotalSessions++
            if (mine.status == "present" || mine.status == "late") myPresentCount++
            myTotalMinutes += mine.durationMinutes
        } else if (rec.records.containsKey(currentUserId)) {
            myTotalSessions++
            if (rec.records[currentUserId] == true) myPresentCount++
        }
    }
    val myHoursText = String.format(Locale.US, "%.1f", myTotalMinutes / 60.0)
    val myRatePct = if (myTotalSessions > 0) ((myPresentCount * 100.0) / myTotalSessions).roundToInt() else 0

    // Mode Selector
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "physical" to "In-Person",
                "online" to "Online Live",
                "all" to "Combined"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = activeMode == key,
                    onClick = { onModeChange(key) },
                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    }

    // Student Clock-In / Clock-Out Interactive Card
    item {
        var locationInput by remember(clockMode) {
            mutableStateOf(
                if (clockMode == "online") "Online Live Classroom" else "Main Campus Station"
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("student_attendance_clock_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (clockMode == "online") "Online Live Attendance" else "Campus Attendance Check-In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Surface(
                        color = when {
                            hasCheckedOutToday -> BackpackPrimaryBlue.copy(alpha = 0.14f)
                            isCheckedInToday -> BackpackStatusGreen.copy(alpha = 0.14f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when {
                                hasCheckedOutToday -> "COMPLETED TODAY"
                                isCheckedInToday -> "CHECKED IN"
                                else -> "NOT CHECKED IN"
                            },
                            color = when {
                                hasCheckedOutToday -> BackpackPrimaryBlue
                                isCheckedInToday -> BackpackStatusGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (myTodayEntry != null) {
                    Text(
                        text = buildString {
                            append("Today: Checked in at ${myTodayEntry.checkInTime ?: "—"}")
                            if (!myTodayEntry.checkOutTime.isNullOrBlank()) {
                                append(" • Checked out at ${myTodayEntry.checkOutTime} (${myTodayEntry.durationMinutes}m)")
                            }
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = BackpackStatusGreen
                    )
                } else {
                    Text(
                        text = "Date: $today • Synchronized directly with ${course.title}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    label = { Text("Location / Session Room") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onClockIn(clockMode, locationInput) },
                        enabled = !isSubmitting && !isCheckedInToday,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("attendance_checkin_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check In", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onClockOut(clockMode) },
                        enabled = !isSubmitting && isCheckedInToday && !hasCheckedOutToday,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("attendance_checkout_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Out", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Real Attendance KPI Row
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AttendanceKpiBox(
                modifier = Modifier.weight(1f),
                label = "Sessions",
                value = "${modeRecords.size}"
            )
            AttendanceKpiBox(
                modifier = Modifier.weight(1f),
                label = "My Time",
                value = "$myHoursText hrs"
            )
            AttendanceKpiBox(
                modifier = Modifier.weight(1f),
                label = "My Rate",
                value = "$myRatePct%"
            )
        }
    }

    item {
        Text(
            text = "Attendance History (${modeRecords.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }

    if (modeRecords.isEmpty()) {
        item {
            EmptyStateText("No attendance sessions recorded yet for this view.")
        }
    } else {
        items(modeRecords, key = { it.id }) { rec ->
            AttendanceSessionHistoryCard(rec = rec, currentUserId = currentUserId)
        }
    }
}

@Composable
private fun AttendanceKpiBox(modifier: Modifier = Modifier, label: String, value: String) {
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
private fun AttendanceSessionHistoryCard(rec: AttendanceRecord, currentUserId: String) {
    var expanded by remember(rec.id) { mutableStateOf(false) }
    val myEntry = rec.entries.firstOrNull { it.userId == currentUserId }
    val statusColor = when (myEntry?.status) {
        "present" -> BackpackStatusGreen
        "late" -> BackpackStatusOrange
        "absent" -> BackpackStatusRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (rec.mode == "online") "ONLINE LIVE" else "IN-PERSON",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (myEntry != null) {
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = myEntry.status.uppercase(),
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(rec.sessionTitle?.ifBlank { null } ?: "Class Session", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            rec.location?.ifBlank { null }?.let {
                Text("Location: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val presentCount = rec.entries.count { it.status == "present" }
            val lateCount = rec.entries.count { it.status == "late" }
            Text(
                text = "$presentCount Present" + (if (lateCount > 0) " • $lateCount Late" else "") + " • Tap for details",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (myEntry != null) {
                    Text(
                        text = "Your Check-In: ${myEntry.checkInTime ?: "—"} | Check-Out: ${myEntry.checkOutTime ?: "—"} | Duration: ${myEntry.durationMinutes}m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                rec.instructorEngagementName?.let { instName ->
                    Text(
                        text = "Instructor ($instName): In ${rec.instructorCheckInTime ?: "—"} • Out ${rec.instructorCheckOutTime ?: "—"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (rec.entries.isNotEmpty()) {
                    rec.entries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${entry.userName} (${entry.role})",
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${entry.status.uppercase()} • ${entry.checkInTime ?: "—"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.studentScheduleAndCertTab(
    course: CourseItem,
    userProfile: UserProfile?,
    userProgress: UserProgress?,
    scheduleEvents: List<ScheduleEvent>,
    organizations: List<Organization>
) {
    val pct = course.calculateProgressPercent(userProgress)
    val cert = course.certificateConfig
    val orgName = cert?.orgName?.ifBlank { null }
        ?: organizations.firstOrNull { it.id == course.orgId || it.ownerId == course.orgId }?.name
        ?: "Backpack Academy"

    item {
        Text(
            text = "Class Timetable (${scheduleEvents.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }

    if (scheduleEvents.isEmpty()) {
        item {
            EmptyStateText("No scheduled classes or events for this course.")
        }
    } else {
        items(scheduleEvents, key = { it.id }) { evt ->
            val context = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            text = "${evt.date} • ${evt.time} (${evt.durationMins}m)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BackpackPrimaryBlue
                        )
                        Surface(
                            color = if (evt.isActive) BackpackStatusGreen.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (evt.isActive) "LIVE NOW" else evt.type.uppercase(),
                                color = if (evt.isActive) BackpackStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(evt.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    evt.meetingUrl?.ifBlank { null }?.let { url ->
                        OutlinedButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (evt.isActive) "Join Live Session" else "Meeting Link", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Certificate Status (Read-Only)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }

    item {
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("student_certificate_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cert == null || !cert.enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Course completion certificates are currently not configured for this course.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (pct < 100.0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = BackpackStatusOrange)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Certificate Locked", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "Complete 100% of modules to unlock (${pct.roundToInt()}% finished).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp),
                        color = BackpackStatusOrange
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = BackpackStatusGreen, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(cert.customText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = BackpackStatusGreen)
                            Text("Issued by $orgName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text("Recipient: ${userProfile?.name ?: "Student"}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Course: ${course.title}", fontSize = 13.sp)
                    cert.qualificationTitle?.ifBlank { null }?.let {
                        Text("Qualification: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    cert.gradeLevel?.ifBlank { null }?.let {
                        Text("Level: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Status: Verified Course Completion", fontSize = 11.sp, color = BackpackStatusGreen, fontWeight = FontWeight.Medium)

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://backpack-edu.com/course/${course.id}")))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download Official PDF on Web Portal")
                    }
                }
            }
        }
    }
}

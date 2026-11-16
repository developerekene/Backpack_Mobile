package com.example.ui.courses

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
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.ui.theme.BackpackStatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CohortParticipant(
    val userId: String,
    val name: String,
    val email: String?,
    val role: String // "student" | "instructor"
)

fun buildRealCourseRoster(
    course: CourseItem,
    orgMembers: List<OrgMember>,
    approvedEnrollments: List<EnrollmentRequest>
): List<CohortParticipant> {
    val map = linkedMapOf<String, CohortParticipant>()

    if (!course.instructorId.isNullOrBlank()) {
        map[course.instructorId] = CohortParticipant(
            userId = course.instructorId,
            name = course.instructorName?.ifBlank { null } ?: "Course Instructor",
            email = null,
            role = "instructor"
        )
    }

    orgMembers
        .filter { m ->
            m.courseIds.contains(course.id) || m.orgId == course.orgId || m.orgId == "org_${course.orgId}"
        }
        .filter { m -> m.status == "active" || m.role == "instructor" }
        .forEach { m ->
            val key = m.userId?.ifBlank { null } ?: m.id
            if (key.isNotBlank() && !map.containsKey(key)) {
                map[key] = CohortParticipant(
                    userId = key,
                    name = m.name.ifBlank { m.email },
                    email = m.email.ifBlank { null },
                    role = if (m.role == "instructor") "instructor" else "student"
                )
            }
        }

    approvedEnrollments
        .filter { it.courseId == course.id && it.status == "approved" }
        .forEach { req ->
            val key = req.userId
            if (key.isNotBlank() && !map.containsKey(key)) {
                map[key] = CohortParticipant(
                    userId = key,
                    name = req.userName.ifBlank { req.userEmail ?: "Student" },
                    email = req.userEmail,
                    role = "student"
                )
            }
        }

    return map.values.sortedWith(
        compareBy<CohortParticipant> { it.role != "instructor" }.thenBy { it.name.lowercase() }
    )
}

@Composable
fun TakeAttendanceModal(
    course: CourseItem,
    roster: List<CohortParticipant>,
    defaultMode: String,
    existingRecord: AttendanceRecord? = null,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        date: String,
        mode: String,
        sessionTitle: String,
        location: String,
        entries: List<AttendanceEntry>
    ) -> Unit
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var dateText by remember(existingRecord) { mutableStateOf(existingRecord?.date ?: today) }
    val mode = existingRecord?.mode ?: if (defaultMode == "online") "online" else "physical"
    var sessionTitle by remember(existingRecord) {
        mutableStateOf(
            existingRecord?.sessionTitle
                ?: if (mode == "online") "Online Class Session ($today)" else "Physical Campus Session ($today)"
        )
    }
    var location by remember(existingRecord) {
        mutableStateOf(
            existingRecord?.location
                ?: if (mode == "online") "Online Live Classroom" else "Main Classroom"
        )
    }
    var defaultIn by remember { mutableStateOf("09:00") }
    var defaultOut by remember { mutableStateOf("10:30") }

    val allParticipants = remember(roster, existingRecord) {
        val map = linkedMapOf<String, CohortParticipant>()
        roster.forEach { map[it.userId] = it }
        existingRecord?.entries?.forEach { e ->
            if (!map.containsKey(e.userId)) {
                map[e.userId] = CohortParticipant(
                    userId = e.userId,
                    name = e.userName,
                    email = e.userEmail,
                    role = e.role
                )
            }
        }
        map.values.toList()
    }

    var draftEntries by remember(existingRecord, allParticipants) {
        mutableStateOf(
            allParticipants.associate { p ->
                val existingEntry = existingRecord?.entries?.firstOrNull { it.userId == p.userId }
                p.userId to (existingEntry ?: AttendanceEntry(
                    id = "rc_${p.userId}_${System.currentTimeMillis()}",
                    userId = p.userId,
                    userName = p.name,
                    userEmail = p.email,
                    role = p.role,
                    status = "present",
                    checkInTime = defaultIn,
                    checkOutTime = defaultOut,
                    durationMinutes = 90,
                    location = location,
                    method = if (mode == "online") "live_online" else "manual_rollcall"
                ))
            }
        )
    }

    fun computeMins(inT: String?, outT: String?): Int {
        if (inT.isNullOrBlank() || outT.isNullOrBlank()) return 0
        return runCatching {
            val s = inT.split(":").map { it.trim().toInt() }
            val e = outT.split(":").map { it.trim().toInt() }
            val sm = s[0] * 60 + s[1]
            val em = e[0] * 60 + e[1]
            if (em >= sm) em - sm else (24 * 60 - sm + em)
        }.getOrDefault(0)
    }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .testTag("take_attendance_modal"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existingRecord != null) "Edit Roll Call" else "Take Class Roll Call",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Icon(Icons.Default.Close, contentDescription = "Close roll call")
                    }
                }

                OutlinedTextField(
                    value = sessionTitle,
                    onValueChange = { sessionTitle = it },
                    label = { Text("Session Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Venue / Room") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Default Times & Bulk Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = defaultIn,
                        onValueChange = { defaultIn = it },
                        label = { Text("In") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = defaultOut,
                        onValueChange = { defaultOut = it },
                        label = { Text("Out") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val mins = computeMins(defaultIn, defaultOut)
                            draftEntries = draftEntries.mapValues { (_, e) ->
                                if (e.status == "present" || e.status == "late") {
                                    e.copy(checkInTime = defaultIn, checkOutTime = defaultOut, durationMinutes = mins)
                                } else e
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Apply", fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("present" to "All Present", "late" to "All Late", "absent" to "All Absent").forEach { (st, lbl) ->
                        AssistChip(
                            onClick = {
                                val mins = if (st == "absent") 0 else computeMins(defaultIn, defaultOut)
                                draftEntries = draftEntries.mapValues { (_, e) ->
                                    e.copy(
                                        status = st,
                                        checkInTime = if (st == "absent") null else (e.checkInTime ?: defaultIn),
                                        checkOutTime = if (st == "absent") null else (e.checkOutTime ?: defaultOut),
                                        durationMinutes = mins
                                    )
                                }
                            },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }

                if (allParticipants.isEmpty()) {
                    Text(
                        text = "No active cohort members or approved students found for this course.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allParticipants, key = { it.userId }) { p ->
                            val cur = draftEntries[p.userId] ?: return@items
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${p.name} (${p.role})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${cur.durationMinutes}m", fontSize = 11.sp, color = BackpackPrimaryBlue)
                                    }
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("present", "late", "absent", "excused").forEach { statusOpt ->
                                            FilterChip(
                                                selected = cur.status == statusOpt,
                                                onClick = {
                                                    val next = if (statusOpt == "absent" || statusOpt == "excused") {
                                                        cur.copy(status = statusOpt, checkInTime = null, checkOutTime = null, durationMinutes = 0)
                                                    } else {
                                                        val inT = cur.checkInTime ?: defaultIn
                                                        val outT = cur.checkOutTime ?: defaultOut
                                                        cur.copy(status = statusOpt, checkInTime = inT, checkOutTime = outT, durationMinutes = computeMins(inT, outT))
                                                    }
                                                    draftEntries = draftEntries.toMutableMap().apply { put(p.userId, next) }
                                                },
                                                label = { Text(statusOpt.uppercase(), fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val finalList = draftEntries.values.map { e ->
                            e.copy(location = location, durationMinutes = computeMins(e.checkInTime, e.checkOutTime))
                        }
                        onSave(dateText, mode, sessionTitle, location, finalList)
                    },
                    enabled = !isSubmitting && allParticipants.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_roll_call_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save Roll Call", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun KioskModal(
    course: CourseItem,
    roster: List<CohortParticipant>,
    todayRecord: AttendanceRecord?,
    isSubmitting: Boolean,
    lastStatusMessage: String?,
    onDismiss: () -> Unit,
    onClockParticipant: (CohortParticipant, action: String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(roster, search) {
        if (search.isBlank()) roster
        else {
            val q = search.trim().lowercase()
            roster.filter { it.name.lowercase().contains(q) || it.email.orEmpty().lowercase().contains(q) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .testTag("kiosk_station_modal"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Physical Kiosk Check-In", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(course.title, fontSize = 12.sp, color = BackpackPrimaryBlue)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close kiosk")
                    }
                }

                lastStatusMessage?.let { msg ->
                    Surface(
                        color = BackpackStatusGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = BackpackStatusGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search student or instructor...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (filtered.isEmpty()) {
                    Text(
                        text = "No matching course participants found.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.userId }) { person ->
                            val todayEntry = todayRecord?.entries?.firstOrNull { it.userId == person.userId }
                            val clockedIn = todayEntry != null
                            val clockedOut = !todayEntry?.checkOutTime.isNullOrBlank()

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(person.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = buildString {
                                                append(person.role.uppercase())
                                                if (todayEntry != null) {
                                                    append(" • In ${todayEntry.checkInTime ?: "—"}")
                                                    if (!todayEntry.checkOutTime.isNullOrBlank()) {
                                                        append(" • Out ${todayEntry.checkOutTime}")
                                                    }
                                                }
                                            },
                                            fontSize = 11.sp,
                                            color = if (clockedIn) BackpackStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { onClockParticipant(person, "in") },
                                            enabled = !isSubmitting && !clockedIn,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                                        ) {
                                            Text("IN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onClockParticipant(person, "out") },
                                            enabled = !isSubmitting && clockedIn && !clockedOut,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp)
                                        ) {
                                            Text("OUT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

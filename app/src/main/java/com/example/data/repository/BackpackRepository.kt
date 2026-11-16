package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

class BackpackRepository(context: Context) {

    private val db = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    )
    private val auth = Firebase.auth

    private fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun getCurrentTime24h(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return sdf.format(Date())
    }

    private fun calculateDurationMinutes(startTime: String?, endTime: String?): Int {
        if (startTime.isNullOrBlank() || endTime.isNullOrBlank()) return 0
        return try {
            val sParts = startTime.split(":").map { it.trim().toInt() }
            val eParts = endTime.split(":").map { it.trim().toInt() }
            val startMins = sParts[0] * 60 + sParts[1]
            val endMins = eParts[0] * 60 + eParts[1]
            if (endMins >= startMins) endMins - startMins else (24 * 60 - startMins + endMins)
        } catch (_: Exception) {
            0
        }
    }

    fun currentUserId(): String = auth.currentUser?.uid ?: ""

    fun currentUserDisplayName(): String = auth.currentUser?.displayName
        ?: auth.currentUser?.email?.substringBefore("@")?.replace(".", " ")?.replaceFirstChar { it.uppercase() }
        ?: "Backpack User"

    fun currentUserEmail(): String = auth.currentUser?.email ?: ""

    // --- Profile Handling ---

    suspend fun fetchUserProfileOnce(uid: String): UserProfile {
        if (uid.isBlank()) return UserProfile()
        return try {
            val doc = db.collection("backpack").document(uid).get().await()
            if (doc.exists()) {
                BackpackSnapshotParser.parseUserProfile(
                    uid = uid,
                    doc = doc,
                    fallbackName = currentUserDisplayName(),
                    fallbackEmail = currentUserEmail()
                )
            } else {
                UserProfile(
                    userId = uid,
                    name = currentUserDisplayName(),
                    displayName = currentUserDisplayName(),
                    email = currentUserEmail()
                )
            }
        } catch (e: Exception) {
            UserProfile(
                userId = uid,
                name = currentUserDisplayName(),
                displayName = currentUserDisplayName(),
                email = currentUserEmail()
            )
        }
    }

    fun observeUserProfile(targetUserId: String? = null): Flow<UserProfile> = flow {
        val uid = targetUserId?.ifBlank { null } ?: currentUserId()
        if (uid.isBlank()) {
            emit(UserProfile())
            return@flow
        }
        db.collection("backpack").document(uid).snapshots()
            .map { snap ->
                BackpackSnapshotParser.parseUserProfile(
                    uid = uid,
                    doc = snap,
                    fallbackName = currentUserDisplayName(),
                    fallbackEmail = currentUserEmail()
                )
            }
            .catch { emit(UserProfile(userId = uid, displayName = currentUserDisplayName())) }
            .collect { emit(it) }
    }

    // --- Unified Real-Time Backpack Snapshot ---

    fun observeBackpackSnapshot(): Flow<BackpackSnapshotData> = flow {
        db.collection("backpack").snapshots()
            .map { snapshot -> BackpackSnapshotParser.parseSnapshot(snapshot) }
            .catch { err ->
                Log.e("BackpackRepository", "Error observing backpack collection", err)
                emit(BackpackSnapshotData())
            }
            .collect { emit(it) }
    }

    // --- Shared Firestore Read-Modify-Write Array Helpers (Map & Legacy 1-Element Array) ---

    private suspend fun performStickyUpdate(userId: String, updates: Map<String, Any?>): Boolean {
        if (userId.isBlank()) return false
        val docRef = db.collection("backpack").document(userId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) return false
            val data = snap.data ?: return false
            val userRaw = data["user"]
            val isArray = userRaw is List<*>

            val finalUpdates = mutableMapOf<String, Any?>()
            if (isArray) {
                val userList = userRaw.mapNotNull {
                    (it as? Map<*, *>)?.mapKeys { e -> e.key.toString() }
                }.toMutableList()
                val userMap = userList.firstOrNull()?.toMutableMap() ?: mutableMapOf()
                updates.forEach { (key, value) ->
                    if (key.contains(".")) {
                        val parts = key.split(".")
                        var current: MutableMap<String, Any?> = userMap
                        for (i in 0 until parts.size - 1) {
                            val next = (current[parts[i]] as? Map<*, *>)
                                ?.mapKeys { it.key.toString() }
                                ?.toMutableMap() ?: mutableMapOf()
                            current[parts[i]] = next
                            current = next
                        }
                        current[parts.last()] = value
                    } else {
                        userMap[key] = value
                    }
                }
                if (userList.isNotEmpty()) userList[0] = userMap else userList.add(userMap)
                finalUpdates["user"] = userList
            } else {
                updates.forEach { (key, value) ->
                    finalUpdates["user.$key"] = value
                }
            }

            docRef.update(finalUpdates).await()
            true
        } catch (e: Exception) {
            Log.e("BackpackRepository", "Sticky update failed", e)
            false
        }
    }

    private suspend fun updateListFieldInDoc(
        docId: String,
        fieldPath: String, // e.g., "course.attendance" or "notifications"
        transform: (MutableList<MutableMap<String, Any?>>) -> List<Map<String, Any?>>
    ): Boolean {
        if (docId.isBlank()) return false
        val docRef = db.collection("backpack").document(docId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) return false
            val data = snap.data ?: return false
            val userRaw = data["user"]
            val isArray = userRaw is List<*>

            val userMap = if (isArray) {
                (userRaw.firstOrNull() as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
            } else {
                (userRaw as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
            }

            val parts = fieldPath.split(".")
            val parentField = if (parts.size > 1) parts[0] else null
            val childField = parts.last()

            val targetContainer = if (parentField != null) {
                (userMap[parentField] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
            } else {
                userMap
            }

            val currentList = (targetContainer[childField] as? List<*>)
                ?.mapNotNull { item ->
                    (item as? Map<*, *>)?.entries
                        ?.associate { it.key.toString() to it.value }
                        ?.toMutableMap()
                }
                ?.toMutableList() ?: mutableListOf()

            val updatedList = transform(currentList)

            if (isArray) {
                val userList = userRaw.mapNotNull {
                    (it as? Map<*, *>)?.mapKeys { e -> e.key.toString() }
                }.toMutableList()
                val newUserMap = userList.firstOrNull()?.toMutableMap() ?: mutableMapOf()
                if (parentField != null) {
                    val newParentMap = (newUserMap[parentField] as? Map<*, *>)
                        ?.mapKeys { it.key.toString() }
                        ?.toMutableMap() ?: mutableMapOf()
                    newParentMap[childField] = updatedList
                    newUserMap[parentField] = newParentMap
                } else {
                    newUserMap[childField] = updatedList
                }
                if (userList.isNotEmpty()) userList[0] = newUserMap else userList.add(newUserMap)
                docRef.update("user", userList).await()
            } else {
                val fullPath = if (parentField != null) "user.$parentField.$childField" else "user.$childField"
                docRef.update(fullPath, updatedList).await()
            }
            true
        } catch (e: Exception) {
            Log.e("BackpackRepository", "updateListFieldInDoc($docId, $fieldPath) failed", e)
            false
        }
    }

    private suspend fun upsertInDocArray(
        docId: String,
        fieldPath: String,
        newItem: Map<String, Any?>,
        idKey: String = "id"
    ): Boolean = updateListFieldInDoc(docId, fieldPath) { currentList ->
        val newItemId = newItem[idKey]?.toString()
        val existingIdx = if (!newItemId.isNullOrBlank()) {
            currentList.indexOfFirst { it[idKey]?.toString() == newItemId }
        } else -1

        if (existingIdx >= 0) {
            val merged = currentList[existingIdx].toMutableMap()
            merged.putAll(newItem)
            currentList[existingIdx] = merged
        } else {
            currentList.add(newItem.toMutableMap())
        }
        currentList
    }

    private suspend fun findOwnerDocForListItem(
        fieldPath: String,
        itemId: String,
        fallbackDocId: String
    ): String {
        if (itemId.isBlank()) return fallbackDocId
        return try {
            val snapshot = db.collection("backpack").get().await()
            val parts = fieldPath.split(".")
            for (doc in snapshot.documents) {
                val userMap = BackpackSnapshotParser.getUserMap(doc.data)
                val container = if (parts.size > 1) {
                    (userMap[parts[0]] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
                } else {
                    userMap
                }
                val list = container[parts.last()] as? List<*> ?: continue
                val exists = list.any { (it as? Map<*, *>)?.get("id")?.toString() == itemId }
                if (exists) return doc.id
            }
            fallbackDocId
        } catch (_: Exception) {
            fallbackDocId
        }
    }

    private suspend fun resolveExistingDocId(candidateDocId: String, fallbackUid: String): String {
        val candidates = buildList {
            if (candidateDocId.isNotBlank()) {
                add(candidateDocId)
                val stripped = candidateDocId.removePrefix("org_")
                if (stripped.isNotBlank() && stripped != candidateDocId) add(stripped)
            }
            if (fallbackUid.isNotBlank()) add(fallbackUid)
        }.distinct()

        for (cid in candidates) {
            try {
                if (db.collection("backpack").document(cid).get().await().exists()) {
                    return cid
                }
            } catch (_: Exception) {
            }
        }
        return fallbackUid
    }

    private suspend fun pushUserNotification(
        targetUserId: String,
        title: String,
        message: String,
        type: String,
        linkUrl: String? = null,
        courseId: String? = null
    ): Boolean {
        val docId = resolveExistingDocId(targetUserId, "")
        if (docId.isBlank()) return false
        val notifId = "notif_${System.currentTimeMillis()}_${(100..999).random()}"
        val timeLabel = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
        val notifMap = mutableMapOf<String, Any?>(
            "id" to notifId,
            "userId" to targetUserId,
            "title" to title,
            "message" to message,
            "type" to type,
            "read" to false,
            "createdAt" to timeLabel
        )
        if (!courseId.isNullOrBlank()) notifMap["courseId"] = courseId
        if (!linkUrl.isNullOrBlank()) notifMap["linkUrl"] = linkUrl
        return upsertInDocArray(docId, "notifications", notifMap, idKey = "id")
    }

    // --- Filtered Streams Preserving Legacy Callers ---

    fun observeCourses(targetUserId: String? = null): Flow<List<CourseItem>> = flow {
        val uid = targetUserId?.ifBlank { null } ?: currentUserId()
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        val profile = fetchUserProfileOnce(uid)

        observeBackpackSnapshot().map { snap ->
            BackpackSnapshotParser.filterCoursesForRole(snap, profile, uid)
        }.collect { emit(it) }
    }

    fun observeScheduleEvents(targetUserId: String? = null): Flow<List<ScheduleEvent>> = flow {
        val uid = targetUserId?.ifBlank { null } ?: currentUserId()
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        val profile = fetchUserProfileOnce(uid)

        observeBackpackSnapshot().map { snap ->
            val roleCourses = BackpackSnapshotParser.filterCoursesForRole(snap, profile, uid)
            val roleCourseIds = roleCourses.map { it.id }.toSet()
            val today = getTodayDateString()
            val checkedInTodayCourseIds = snap.attendanceRecords
                .filter { it.date == today && it.entries.any { e -> e.userId == uid } }
                .map { it.courseId }
                .toSet()

            val filtered = if (profile.userRole == UserRole.SUPER_ADMIN) {
                snap.scheduleEvents
            } else {
                snap.scheduleEvents.filter { it.courseId in roleCourseIds }
            }
            filtered.map { it.copy(isCheckedIn = it.courseId in checkedInTodayCourseIds) }
        }.collect { emit(it) }
    }

    fun observeLessons(courseId: String): Flow<List<LessonNote>> =
        observeBackpackSnapshot().map { snap ->
            val course = snap.courses.firstOrNull { it.id == courseId }
            course?.modules?.flatMap { it.items } ?: emptyList()
        }

    fun observeAssessments(courseId: String): Flow<List<Assessment>> =
        observeBackpackSnapshot().map { snap ->
            snap.assessments.filter { it.courseId == courseId }
        }

    fun observeDiscussionMessages(courseId: String): Flow<List<DiscussionMessage>> =
        observeBackpackSnapshot().map { snap ->
            snap.discussionMessages
                .filter { it.courseId == courseId }
                .sortedBy { it.createdAt }
        }

    fun observeAttendanceRecords(courseId: String): Flow<List<AttendanceRecord>> =
        observeBackpackSnapshot().map { snap ->
            snap.attendanceRecords
                .filter { it.courseId == courseId }
                .sortedByDescending { it.date }
        }

    fun observeSubmissions(targetUserId: String? = null): Flow<List<Submission>> = flow {
        val uid = targetUserId?.ifBlank { null } ?: currentUserId()
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        val profile = fetchUserProfileOnce(uid)

        observeBackpackSnapshot().map { snap ->
            if (profile.userRole == UserRole.STUDENT) {
                snap.submissions.filter { it.userId == uid }
            } else if (profile.userRole == UserRole.SUPER_ADMIN) {
                snap.submissions
            } else {
                val roleCourseIds = BackpackSnapshotParser.filterCoursesForRole(snap, profile, uid)
                    .map { it.id }
                    .toSet()
                snap.submissions.filter { it.courseId in roleCourseIds }
            }
        }.collect { emit(it) }
    }

    // =========================================================================
    // ATTENDANCE WRITES: SELF CHECK-IN/OUT, ROLL CALL & KIOSK (Web Exact Parity)
    // =========================================================================

    suspend fun clockInAttendance(
        course: CourseItem,
        mode: String = "physical", // "physical" | "online"
        location: String = ""
    ): Result<String> {
        val uid = currentUserId()
        if (uid.isBlank()) return Result.failure(IllegalStateException("Not signed in."))
        val profile = fetchUserProfileOnce(uid)
        val entryRole = if (profile.userRole == UserRole.STUDENT) "student" else "instructor"
        val today = getTodayDateString()
        val nowTime = getCurrentTime24h()
        val cleanMode = if (mode == "online") "online" else "physical"
        val resolvedLocation = location.ifBlank {
            if (cleanMode == "online") "Online Live Classroom" else "Main Campus Station"
        }

        val snapshot = try {
            BackpackSnapshotParser.parseSnapshot(db.collection("backpack").get().await())
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val existing = snapshot.attendanceRecords.firstOrNull {
            it.courseId == course.id &&
                it.date == today &&
                (it.mode ?: "physical") == cleanMode
        }

        if (existing != null && existing.id.isNotBlank()) {
            val targetDocId = existing.ownerDocId ?: resolveExistingDocId(course.orgId, uid)
            val updatedEntries = existing.entries.toMutableList()
            val entryIdx = updatedEntries.indexOfFirst { it.userId == uid }

            val newOrUpdatedEntry = if (entryIdx >= 0) {
                val cur = updatedEntries[entryIdx]
                cur.copy(
                    role = entryRole,
                    status = "present",
                    checkInTime = cur.checkInTime ?: nowTime,
                    location = resolvedLocation,
                    method = if (cleanMode == "online") "live_online" else "self_clock"
                )
            } else {
                AttendanceEntry(
                    id = "self_${uid}_${System.currentTimeMillis()}",
                    userId = uid,
                    userName = profile.name,
                    userEmail = profile.email.ifBlank { null },
                    role = entryRole,
                    status = "present",
                    checkInTime = nowTime,
                    checkOutTime = null,
                    durationMinutes = 0,
                    location = resolvedLocation,
                    method = if (cleanMode == "online") "live_online" else "self_clock"
                )
            }

            if (entryIdx >= 0) updatedEntries[entryIdx] = newOrUpdatedEntry else updatedEntries.add(newOrUpdatedEntry)

            val entriesMaps = updatedEntries.map { entryToMap(it) }
            val updatedRecordsMap = existing.records.toMutableMap().apply { put(uid, true) }
            val totalMins = updatedEntries.sumOf { it.durationMinutes }
            val avgMins = if (updatedEntries.isNotEmpty()) (totalMins.toDouble() / updatedEntries.size).roundToInt() else 0
            val instEntry = updatedEntries.firstOrNull { it.role == "instructor" }

            val ok = updateListFieldInDoc(targetDocId, "course.attendance") { list ->
                list.map { item ->
                    if (item["id"]?.toString() == existing.id) {
                        item.toMutableMap().apply {
                            put("entries", entriesMaps)
                            put("records", updatedRecordsMap)
                            put("totalAttendees", updatedEntries.count { it.status == "present" || it.status == "late" })
                            put("avgEngagementMinutes", avgMins)
                            put("updatedAt", getCurrentIsoTimestamp())
                            instEntry?.let { put("instructorEngagement", instructorEngagementToMap(it)) }
                        }
                    } else item
                }
            }
            return if (ok) {
                Result.success("Checked in at $nowTime (${if (cleanMode == "online") "Online Live" else "In-Person"})")
            } else {
                Result.failure(IllegalStateException("Could not update attendance record."))
            }
        } else {
            val targetDocId = if (profile.userRole == UserRole.STUDENT) uid else resolveExistingDocId(course.orgId, uid)
            val recId = "att_${System.currentTimeMillis()}_${course.id}"
            val entry = AttendanceEntry(
                id = "self_${uid}_${System.currentTimeMillis()}",
                userId = uid,
                userName = profile.name,
                userEmail = profile.email.ifBlank { null },
                role = entryRole,
                status = "present",
                checkInTime = nowTime,
                checkOutTime = null,
                durationMinutes = 0,
                location = resolvedLocation,
                method = if (cleanMode == "online") "live_online" else "self_clock"
            )
            val recordMap = mutableMapOf<String, Any?>(
                "id" to recId,
                "courseId" to course.id,
                "courseTitle" to course.title,
                "orgId" to course.orgId,
                "date" to today,
                "mode" to cleanMode,
                "sessionTitle" to if (cleanMode == "online") "Online Class Session ($today)" else "Physical Campus Session ($today)",
                "location" to resolvedLocation,
                "startTime" to nowTime,
                "entries" to listOf(entryToMap(entry)),
                "records" to mapOf(uid to true),
                "totalAttendees" to 1,
                "avgEngagementMinutes" to 0,
                "createdAt" to getCurrentIsoTimestamp(),
                "updatedAt" to getCurrentIsoTimestamp()
            )
            if (entryRole == "instructor") {
                recordMap["instructorEngagement"] = instructorEngagementToMap(entry)
            }
            val ok = upsertInDocArray(targetDocId, "course.attendance", recordMap, idKey = "id")
            return if (ok) {
                Result.success("Checked in at $nowTime (${if (cleanMode == "online") "Online Live" else "In-Person"})")
            } else {
                Result.failure(IllegalStateException("Failed to create attendance record."))
            }
        }
    }

    suspend fun clockOutAttendance(
        course: CourseItem,
        mode: String = "physical"
    ): Result<String> {
        val uid = currentUserId()
        if (uid.isBlank()) return Result.failure(IllegalStateException("Not signed in."))
        val today = getTodayDateString()
        val nowTime = getCurrentTime24h()
        val cleanMode = if (mode == "online") "online" else "physical"

        val snapshot = try {
            BackpackSnapshotParser.parseSnapshot(db.collection("backpack").get().await())
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val todayRecord = snapshot.attendanceRecords.firstOrNull { rec ->
            rec.courseId == course.id &&
                rec.date == today &&
                (rec.mode ?: "physical") == cleanMode &&
                rec.entries.any { it.userId == uid }
        } ?: return Result.failure(IllegalStateException("No active check-in found for today."))

        val targetDocId = todayRecord.ownerDocId ?: uid
        val updatedEntries = todayRecord.entries.map { cur ->
            if (cur.userId == uid) {
                val mins = max(1, calculateDurationMinutes(cur.checkInTime, nowTime))
                cur.copy(
                    checkOutTime = nowTime,
                    durationMinutes = mins,
                    status = "present"
                )
            } else cur
        }
        val myEntry = updatedEntries.firstOrNull { it.userId == uid }
        val myMins = myEntry?.durationMinutes ?: 0
        val totalMins = updatedEntries.sumOf { it.durationMinutes }
        val avgMins = if (updatedEntries.isNotEmpty()) (totalMins.toDouble() / updatedEntries.size).roundToInt() else 0
        val entriesMaps = updatedEntries.map { entryToMap(it) }
        val updatedRecordsMap = todayRecord.records.toMutableMap().apply { put(uid, true) }
        val instEntry = updatedEntries.firstOrNull { it.role == "instructor" }

        val ok = updateListFieldInDoc(targetDocId, "course.attendance") { list ->
            list.map { item ->
                if (item["id"]?.toString() == todayRecord.id) {
                    item.toMutableMap().apply {
                        put("entries", entriesMaps)
                        put("records", updatedRecordsMap)
                        put("totalAttendees", updatedEntries.count { it.status == "present" || it.status == "late" })
                        put("avgEngagementMinutes", avgMins)
                        put("endTime", nowTime)
                        put("updatedAt", getCurrentIsoTimestamp())
                        instEntry?.let { put("instructorEngagement", instructorEngagementToMap(it)) }
                    }
                } else item
            }
        }
        return if (ok) {
            Result.success("Checked out at $nowTime (${myMins}m logged)")
        } else {
            Result.failure(IllegalStateException("Could not record check-out."))
        }
    }

    suspend fun saveAttendanceSession(
        course: CourseItem,
        date: String,
        mode: String,
        sessionTitle: String,
        location: String,
        entries: List<AttendanceEntry>,
        existingRecord: AttendanceRecord? = null
    ): Result<String> {
        val uid = currentUserId()
        if (uid.isBlank()) return Result.failure(IllegalStateException("Not signed in."))
        val cleanMode = if (mode == "online") "online" else "physical"
        val cleanDate = date.ifBlank { getTodayDateString() }
        val cleanTitle = sessionTitle.trim().ifBlank {
            if (cleanMode == "online") "Online Class Session ($cleanDate)" else "Physical Campus Session ($cleanDate)"
        }
        val cleanLocation = location.trim().ifBlank {
            if (cleanMode == "online") "Online Live Classroom" else "Main Classroom"
        }

        val totalMins = entries.sumOf { it.durationMinutes }
        val avgMins = if (entries.isNotEmpty()) (totalMins.toDouble() / entries.size).roundToInt() else 0
        val legacyRecords = entries.associate { it.userId to (it.status == "present" || it.status == "late") }
        val attendeeCount = entries.count { it.status == "present" || it.status == "late" }
        val instEntry = entries.firstOrNull { it.role == "instructor" }
        val nowIso = getCurrentIsoTimestamp()

        if (existingRecord != null && existingRecord.id.isNotBlank()) {
            val targetDocId = existingRecord.ownerDocId
                ?: findOwnerDocForListItem("course.attendance", existingRecord.id, resolveExistingDocId(course.orgId, uid))
            val entriesMaps = entries.map { entryToMap(it) }
            val ok = updateListFieldInDoc(targetDocId, "course.attendance") { list ->
                list.map { item ->
                    if (item["id"]?.toString() == existingRecord.id) {
                        item.toMutableMap().apply {
                            put("date", cleanDate)
                            put("sessionTitle", cleanTitle)
                            put("location", cleanLocation)
                            put("mode", existingRecord.mode ?: cleanMode)
                            put("entries", entriesMaps)
                            put("records", legacyRecords)
                            put("totalAttendees", attendeeCount)
                            put("avgEngagementMinutes", avgMins)
                            put("updatedAt", nowIso)
                            instEntry?.let { put("instructorEngagement", instructorEngagementToMap(it)) }
                        }
                    } else item
                }
            }
            return if (ok) Result.success("Attendance session updated.")
            else Result.failure(IllegalStateException("Failed to update attendance record."))
        } else {
            val targetDocId = resolveExistingDocId(course.orgId, uid)
            val recId = "att_${System.currentTimeMillis()}_${course.id}"
            val recordMap = mutableMapOf<String, Any?>(
                "id" to recId,
                "courseId" to course.id,
                "courseTitle" to course.title,
                "orgId" to course.orgId,
                "date" to cleanDate,
                "mode" to cleanMode,
                "sessionTitle" to cleanTitle,
                "location" to cleanLocation,
                "entries" to entries.map { entryToMap(it) },
                "records" to legacyRecords,
                "totalAttendees" to attendeeCount,
                "avgEngagementMinutes" to avgMins,
                "createdAt" to nowIso,
                "updatedAt" to nowIso
            )
            instEntry?.let { recordMap["instructorEngagement"] = instructorEngagementToMap(it) }
            val ok = upsertInDocArray(targetDocId, "course.attendance", recordMap, idKey = "id")
            return if (ok) Result.success("Attendance session saved ($attendeeCount present).")
            else Result.failure(IllegalStateException("Failed to save attendance record."))
        }
    }

    suspend fun kioskClockParticipant(
        course: CourseItem,
        participantId: String,
        participantName: String,
        participantEmail: String?,
        participantRole: String,
        action: String // "in" | "out"
    ): Result<String> {
        val uid = currentUserId()
        if (uid.isBlank()) return Result.failure(IllegalStateException("Not signed in."))
        val today = getTodayDateString()
        val nowTime = getCurrentTime24h()
        val cleanRole = if (participantRole == "instructor") "instructor" else "student"

        val snapshot = try {
            BackpackSnapshotParser.parseSnapshot(db.collection("backpack").get().await())
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val todayRecord = snapshot.attendanceRecords.firstOrNull { r ->
            (r.courseId == course.id || (!r.courseTitle.isNullOrBlank() && r.courseTitle == course.title)) &&
                r.date == today &&
                (r.mode == "physical" || r.mode.isNullOrBlank())
        }

        val nowIso = getCurrentIsoTimestamp()
        if (todayRecord == null) {
            val targetDocId = resolveExistingDocId(course.orgId, uid)
            val recId = "att_kiosk_${System.currentTimeMillis()}_${course.id}"
            val newEntry = AttendanceEntry(
                id = "kiosk_${participantId}_${System.currentTimeMillis()}",
                userId = participantId,
                userName = participantName,
                userEmail = participantEmail,
                role = cleanRole,
                status = "present",
                checkInTime = nowTime,
                checkOutTime = if (action == "out") nowTime else null,
                durationMinutes = 0,
                location = "Campus Entrance / Kiosk Station",
                method = "qr_kiosk"
            )
            val recMap = mutableMapOf<String, Any?>(
                "id" to recId,
                "courseId" to course.id,
                "courseTitle" to course.title,
                "orgId" to course.orgId,
                "date" to today,
                "mode" to "physical",
                "sessionTitle" to "Physical Campus Daily Attendance ($today)",
                "location" to "Main Campus Station",
                "entries" to listOf(entryToMap(newEntry)),
                "records" to mapOf(participantId to true),
                "totalAttendees" to 1,
                "avgEngagementMinutes" to 0,
                "createdAt" to nowIso,
                "updatedAt" to nowIso
            )
            if (cleanRole == "instructor") {
                recMap["instructorEngagement"] = instructorEngagementToMap(newEntry)
            }
            val ok = upsertInDocArray(targetDocId, "course.attendance", recMap, idKey = "id")
            return if (ok) {
                Result.success("$participantName clocked ${if (action == "in") "IN" else "OUT"} at $nowTime")
            } else {
                Result.failure(IllegalStateException("Failed to record kiosk check-in."))
            }
        }

        val targetDocId = todayRecord.ownerDocId
            ?: findOwnerDocForListItem("course.attendance", todayRecord.id, resolveExistingDocId(course.orgId, uid))
        val updatedEntries = todayRecord.entries.toMutableList()
        val existingIdx = updatedEntries.indexOfFirst { it.userId == participantId }
        val durationText: String
        if (existingIdx >= 0) {
            val cur = updatedEntries[existingIdx]
            val mins = if (action == "out" && !cur.checkInTime.isNullOrBlank()) {
                max(1, calculateDurationMinutes(cur.checkInTime, nowTime))
            } else cur.durationMinutes
            updatedEntries[existingIdx] = cur.copy(
                checkInTime = if (action == "in") nowTime else (cur.checkInTime ?: nowTime),
                checkOutTime = if (action == "out") nowTime else cur.checkOutTime,
                durationMinutes = mins,
                status = "present"
            )
            durationText = if (action == "out") " (${mins}m logged)" else ""
        } else {
            updatedEntries.add(
                AttendanceEntry(
                    id = "kiosk_${participantId}_${System.currentTimeMillis()}",
                    userId = participantId,
                    userName = participantName,
                    userEmail = participantEmail,
                    role = cleanRole,
                    status = "present",
                    checkInTime = nowTime,
                    checkOutTime = if (action == "out") nowTime else null,
                    durationMinutes = 0,
                    location = "Campus Entrance / Kiosk Station",
                    method = "qr_kiosk"
                )
            )
            durationText = ""
        }

        val entriesMaps = updatedEntries.map { entryToMap(it) }
        val updatedRecords = todayRecord.records.toMutableMap().apply { put(participantId, true) }
        val totalMins = updatedEntries.sumOf { it.durationMinutes }
        val avgMins = if (updatedEntries.isNotEmpty()) (totalMins.toDouble() / updatedEntries.size).roundToInt() else 0
        val instEntry = updatedEntries.firstOrNull { it.role == "instructor" }

        val ok = updateListFieldInDoc(targetDocId, "course.attendance") { list ->
            list.map { item ->
                if (item["id"]?.toString() == todayRecord.id) {
                    item.toMutableMap().apply {
                        put("entries", entriesMaps)
                        put("records", updatedRecords)
                        put("totalAttendees", updatedEntries.count { it.status == "present" || it.status == "late" })
                        put("avgEngagementMinutes", avgMins)
                        put("updatedAt", nowIso)
                        instEntry?.let { put("instructorEngagement", instructorEngagementToMap(it)) }
                    }
                } else item
            }
        }
        return if (ok) {
            Result.success("$participantName clocked ${if (action == "in") "IN" else "OUT"} at $nowTime$durationText")
        } else {
            Result.failure(IllegalStateException("Failed to update kiosk record."))
        }
    }

    suspend fun deleteAttendanceRecord(record: AttendanceRecord, orgId: String): Boolean {
        if (record.id.isBlank()) return false
        val targetDocId = record.ownerDocId
            ?: findOwnerDocForListItem("course.attendance", record.id, resolveExistingDocId(orgId, currentUserId()))
        return updateListFieldInDoc(targetDocId, "course.attendance") { list ->
            list.filter { it["id"]?.toString() != record.id }
        }
    }

    suspend fun checkInToEvent(eventId: String): Boolean {
        val snapshot = try {
            BackpackSnapshotParser.parseSnapshot(db.collection("backpack").get().await())
        } catch (_: Exception) {
            return false
        }
        val event = snapshot.scheduleEvents.firstOrNull { it.id == eventId } ?: return false
        val course = snapshot.courses.firstOrNull { it.id == event.courseId }
            ?: CourseItem(id = event.courseId, title = event.title)
        val mode = if (!event.meetingUrl.isNullOrBlank()) "online" else "physical"
        return clockInAttendance(course, mode, if (mode == "online") "Live Session: ${event.title}" else "Campus Class").isSuccess
    }

    private fun instructorEngagementToMap(entry: AttendanceEntry): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>(
            "instructorId" to entry.userId,
            "instructorName" to entry.userName,
            "durationMinutes" to entry.durationMinutes,
            "status" to entry.status
        )
        entry.checkInTime?.let { m["checkInTime"] = it }
        entry.checkOutTime?.let { m["checkOutTime"] = it }
        return m
    }

    private fun entryToMap(entry: AttendanceEntry): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>(
            "id" to entry.id.ifBlank { "entry_${entry.userId}_${System.currentTimeMillis()}" },
            "userId" to entry.userId,
            "userName" to entry.userName,
            "role" to entry.role,
            "status" to entry.status,
            "durationMinutes" to entry.durationMinutes,
            "method" to (entry.method ?: "self_clock")
        )
        entry.userEmail?.let { m["userEmail"] = it }
        entry.checkInTime?.let { m["checkInTime"] = it }
        entry.checkOutTime?.let { m["checkOutTime"] = it }
        entry.location?.let { m["location"] = it }
        entry.notes?.let { m["notes"] = it }
        return m
    }

    // =========================================================================
    // DISCUSSION WRITES & MODERATION (Web Schema Exact Parity)
    // =========================================================================

    suspend fun ensureDefaultDiscussionChannel(courseId: String, orgId: String): Boolean {
        val targetDocId = resolveExistingDocId(orgId, currentUserId())
        val channelId = "dchan_general_$courseId"
        val channelMap = mapOf(
            "id" to channelId,
            "courseId" to courseId,
            "name" to "general-discussion",
            "description" to "Course-wide discussion",
            "pinned" to true,
            "order" to 0,
            "createdAt" to getCurrentIsoTimestamp()
        )
        return updateListFieldInDoc(targetDocId, "course.discussionChannels") { list ->
            val exists = list.any { it["courseId"]?.toString() == courseId }
            if (exists) list else list + channelMap
        }
    }

    suspend fun createDiscussionChannel(
        courseId: String,
        orgId: String,
        name: String,
        description: String? = null,
        existingCount: Int = 0
    ): Result<DiscussionChannel> {
        val cleanName = name.trim().lowercase().replace(Regex("\\s+"), "-")
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Channel name is required."))
        val targetDocId = resolveExistingDocId(orgId, currentUserId())
        val chanId = "dchan_${System.currentTimeMillis()}_${(100..999).random()}"
        val nowIso = getCurrentIsoTimestamp()
        val chanMap = mutableMapOf<String, Any?>(
            "id" to chanId,
            "courseId" to courseId,
            "name" to cleanName,
            "order" to existingCount,
            "createdAt" to nowIso
        )
        description?.trim()?.ifBlank { null }?.let { chanMap["description"] = it }

        val ok = upsertInDocArray(targetDocId, "course.discussionChannels", chanMap, idKey = "id")
        return if (ok) {
            Result.success(
                DiscussionChannel(
                    id = chanId,
                    courseId = courseId,
                    name = cleanName,
                    description = description?.trim()?.ifBlank { null },
                    order = existingCount,
                    createdAt = nowIso
                )
            )
        } else {
            Result.failure(IllegalStateException("Failed to create channel."))
        }
    }

    suspend fun createDiscussionPoll(
        courseId: String,
        orgId: String,
        channelId: String,
        question: String,
        options: List<String>
    ): Result<DiscussionPoll> {
        val cleanQuestion = question.trim()
        val cleanOpts = options.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanQuestion.isBlank() || cleanOpts.size < 2) {
            return Result.failure(IllegalArgumentException("Enter a poll question and at least 2 options."))
        }
        val resolvedChannelId = channelId.ifBlank { "dchan_general_$courseId" }
        val targetDocId = resolveExistingDocId(orgId, currentUserId())
        val pollId = "poll_${System.currentTimeMillis()}_${(100..999).random()}"
        val nowMs = System.currentTimeMillis()
        val closesAt = nowMs + 4L * 60L * 60L * 1000L

        val optionObjects = cleanOpts.mapIndexed { idx, label ->
            DiscussionPollOption(
                id = "popt_${nowMs}_${idx}_${(10..99).random()}",
                label = label,
                votes = emptyList()
            )
        }
        val pollMap = mapOf(
            "id" to pollId,
            "channelId" to resolvedChannelId,
            "courseId" to courseId,
            "question" to cleanQuestion,
            "options" to optionObjects.map { o ->
                mapOf("id" to o.id, "label" to o.label, "votes" to o.votes)
            },
            "closesAt" to closesAt,
            "createdAt" to nowMs
        )
        val ok = upsertInDocArray(targetDocId, "course.discussionPolls", pollMap, idKey = "id")
        return if (ok) {
            Result.success(
                DiscussionPoll(
                    id = pollId,
                    channelId = resolvedChannelId,
                    courseId = courseId,
                    question = cleanQuestion,
                    options = optionObjects,
                    closesAt = closesAt,
                    createdAt = nowMs
                )
            )
        } else {
            Result.failure(IllegalStateException("Failed to create poll."))
        }
    }

    suspend fun setDiscussionMessageVerified(orgId: String, messageId: String, verified: Boolean): Boolean {
        val uid = currentUserId()
        if (uid.isBlank() || messageId.isBlank()) return false
        val targetDocId = findOwnerDocForListItem("course.discussionMessages", messageId, resolveExistingDocId(orgId, uid))
        return updateListFieldInDoc(targetDocId, "course.discussionMessages") { list ->
            list.map { item ->
                if (item["id"]?.toString() == messageId) {
                    item.toMutableMap().apply { put("verified", verified) }
                } else item
            }
        }
    }

    suspend fun togglePinDiscussionMessage(orgId: String, messageId: String, pinned: Boolean): Boolean {
        val uid = currentUserId()
        if (uid.isBlank() || messageId.isBlank()) return false
        val targetDocId = findOwnerDocForListItem("course.discussionMessages", messageId, resolveExistingDocId(orgId, uid))
        return updateListFieldInDoc(targetDocId, "course.discussionMessages") { list ->
            list.map { item ->
                if (item["id"]?.toString() == messageId) {
                    item.toMutableMap().apply { put("pinned", pinned) }
                } else item
            }
        }
    }

    suspend fun toggleChannelSubscription(courseId: String, orgId: String, channelId: String): Boolean {
        val uid = currentUserId()
        if (uid.isBlank()) return false
        val targetDocId = findOwnerDocForListItem("course.discussionChannels", channelId, resolveExistingDocId(orgId, uid))
        return updateListFieldInDoc(targetDocId, "course.discussionChannels") { list ->
            list.map { ch ->
                if (ch["id"]?.toString() == channelId) {
                    val currentSubs = (ch["subscriberIds"] as? List<*>)
                        ?.mapNotNull { it?.toString() }
                        ?.toMutableList() ?: mutableListOf()
                    if (uid in currentSubs) currentSubs.remove(uid) else currentSubs.add(uid)
                    ch.toMutableMap().apply { put("subscriberIds", currentSubs) }
                } else ch
            }
        }
    }

    suspend fun sendDiscussionMessage(
        courseId: String,
        orgId: String = "",
        channelId: String = "",
        text: String,
        parentId: String? = null,
        fileUrl: String? = null,
        fileType: String? = null
    ): Boolean {
        val uid = currentUserId()
        if (uid.isBlank() || (text.isBlank() && fileUrl.isNullOrBlank())) return false
        val profile = fetchUserProfileOnce(uid)
        val resolvedChannelId = channelId.ifBlank { "dchan_general_$courseId" }
        val targetDocId = resolveExistingDocId(orgId, uid)
        val msgId = "dmsg_${System.currentTimeMillis()}_${(100..999).random()}"

        val newItem = mutableMapOf<String, Any?>(
            "id" to msgId,
            "channelId" to resolvedChannelId,
            "courseId" to courseId,
            "senderId" to uid,
            "senderName" to profile.name,
            "senderRole" to if (profile.userRole == UserRole.STUDENT) "student" else "instructor",
            "text" to text.trim(),
            "createdAt" to System.currentTimeMillis()
        )
        if (!parentId.isNullOrBlank()) newItem["parentId"] = parentId
        if (!fileUrl.isNullOrBlank()) newItem["fileUrl"] = fileUrl
        if (!fileType.isNullOrBlank()) newItem["fileType"] = fileType

        val saved = upsertInDocArray(targetDocId, "course.discussionMessages", newItem, idKey = "id")
        if (saved && !parentId.isNullOrBlank()) {
            val parentDocId = findOwnerDocForListItem("course.discussionMessages", parentId, targetDocId)
            updateListFieldInDoc(parentDocId, "course.discussionMessages") { list ->
                list.map { item ->
                    if (item["id"]?.toString() == parentId) {
                        val currentReplies = (item["replyCount"] as? Number)?.toInt() ?: 0
                        item.toMutableMap().apply { put("replyCount", currentReplies + 1) }
                    } else item
                }
            }
        }
        return saved
    }

    suspend fun toggleMessageReaction(
        orgId: String,
        messageId: String,
        emoji: String
    ): Boolean {
        val uid = currentUserId()
        if (uid.isBlank()) return false
        val targetDocId = findOwnerDocForListItem("course.discussionMessages", messageId, resolveExistingDocId(orgId, uid))
        return updateListFieldInDoc(targetDocId, "course.discussionMessages") { list ->
            list.map { item ->
                if (item["id"]?.toString() == messageId) {
                    val reactionsMap = (item["reactions"] as? Map<*, *>)
                        ?.entries
                        ?.associate { e ->
                            e.key.toString() to ((e.value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList())
                        }?.toMutableMap() ?: mutableMapOf()
                    val currentUsers = reactionsMap[emoji]?.toMutableList() ?: mutableListOf()
                    if (uid in currentUsers) currentUsers.remove(uid) else currentUsers.add(uid)
                    reactionsMap[emoji] = currentUsers
                    item.toMutableMap().apply { put("reactions", reactionsMap) }
                } else item
            }
        }
    }

    suspend fun voteOnPoll(
        orgId: String,
        pollId: String,
        optionId: String
    ): Boolean {
        val uid = currentUserId()
        if (uid.isBlank()) return false
        val targetDocId = findOwnerDocForListItem("course.discussionPolls", pollId, resolveExistingDocId(orgId, uid))
        return updateListFieldInDoc(targetDocId, "course.discussionPolls") { list ->
            list.map { poll ->
                if (poll["id"]?.toString() == pollId) {
                    val updatedOptions = (poll["options"] as? List<*>)?.mapNotNull { optRaw ->
                        val opt = (optRaw as? Map<*, *>)?.entries
                            ?.associate { it.key.toString() to it.value }
                            ?.toMutableMap() ?: return@mapNotNull null
                        val votes = (opt["votes"] as? List<*>)
                            ?.mapNotNull { it?.toString() }
                            ?.filter { it != uid }
                            ?.toMutableList() ?: mutableListOf()
                        if (opt["id"]?.toString() == optionId) {
                            votes.add(uid)
                        }
                        opt["votes"] = votes
                        opt
                    } ?: emptyList()
                    poll.toMutableMap().apply { put("options", updatedOptions) }
                } else poll
            }
        }
    }

    suspend fun pingCoursePresence(courseId: String, orgId: String): Boolean {
        val uid = currentUserId()
        if (uid.isBlank()) return false
        val profile = fetchUserProfileOnce(uid)
        val targetDocId = resolveExistingDocId(orgId, uid)
        val entry = mapOf(
            "id" to "${courseId}_$uid",
            "courseId" to courseId,
            "userId" to uid,
            "userName" to profile.name,
            "role" to if (profile.userRole == UserRole.STUDENT) "student" else "instructor",
            "lastActiveAt" to System.currentTimeMillis()
        )
        return upsertInDocArray(targetDocId, "course.coursePresence", entry, idKey = "id")
    }

    // =========================================================================
    // STUDENT WRITE #3: ASSIGNMENT / CLASSWORK / PROJECT SUBMISSION ONLY
    // =========================================================================

    suspend fun submitStudentAssessment(
        assessment: Assessment,
        content: String,
        fileUrl: String? = null,
        existingSubmissionId: String? = null
    ): Result<Submission> {
        if (!assessment.isMobileSubmittable) {
            return Result.failure(
                IllegalStateException("Quizzes, tests, and exams cannot be submitted on mobile. Please use the Backpack Web Portal.")
            )
        }
        val uid = currentUserId()
        if (uid.isBlank()) return Result.failure(IllegalStateException("User not signed in."))
        val trimmedContent = content.trim()
        val trimmedFile = fileUrl?.trim()?.ifBlank { null }
        if (trimmedContent.isBlank() && trimmedFile == null) {
            return Result.failure(IllegalArgumentException("Please enter your response/link or attach a file."))
        }
        val profile = fetchUserProfileOnce(uid)
        val subId = existingSubmissionId?.ifBlank { null } ?: "sub_${System.currentTimeMillis()}"
        val nowIso = getCurrentIsoTimestamp()

        val subMap = mutableMapOf<String, Any?>(
            "id" to subId,
            "assessmentId" to assessment.id,
            "userId" to uid,
            "userName" to profile.name,
            "courseId" to assessment.courseId,
            "submittedAt" to nowIso,
            "content" to trimmedContent,
            "status" to "submitted"
        )
        if (trimmedFile != null) subMap["fileUrl"] = trimmedFile

        val ok = upsertInDocArray(uid, "course.submissions", subMap, idKey = "id")
        return if (ok) {
            Result.success(
                Submission(
                    id = subId,
                    assessmentId = assessment.id,
                    userId = uid,
                    userName = profile.name,
                    courseId = assessment.courseId,
                    submittedAt = nowIso,
                    content = trimmedContent,
                    fileUrl = trimmedFile,
                    status = "submitted"
                )
            )
        } else {
            Result.failure(IllegalStateException("Failed to save submission to Firestore."))
        }
    }

    // =========================================================================
    // ASSESSMENT REVIEW & GRADING WRITES (Web Exact Parity)
    // =========================================================================

    suspend fun saveEssayScoresDraft(
        submission: Submission,
        essayScores: Map<String, Double>
    ): Boolean {
        if (submission.id.isBlank()) return false
        val targetDocId = findOwnerDocForListItem(
            "course.submissions",
            submission.id,
            submission.userId.ifBlank { currentUserId() }
        )
        return updateListFieldInDoc(targetDocId, "course.submissions") { list ->
            list.map { item ->
                if (item["id"]?.toString() == submission.id) {
                    item.toMutableMap().apply { put("essayScores", essayScores) }
                } else item
            }
        }
    }

    suspend fun gradeSubmission(
        submission: Submission,
        score: Double,
        feedback: String,
        essayScores: Map<String, Double>? = null
    ): Result<String> {
        if (submission.id.isBlank()) return Result.failure(IllegalArgumentException("Missing submission ID."))
        val targetDocId = findOwnerDocForListItem(
            "course.submissions",
            submission.id,
            submission.userId.ifBlank { currentUserId() }
        )
        val cleanFeedback = feedback.trim().ifBlank { "Graded by instructor" }
        val ok = updateListFieldInDoc(targetDocId, "course.submissions") { list ->
            list.map { item ->
                if (item["id"]?.toString() == submission.id) {
                    item.toMutableMap().apply {
                        put("status", "graded")
                        put("score", score)
                        put("feedback", cleanFeedback)
                        if (!essayScores.isNullOrEmpty()) {
                            put("essayScores", essayScores)
                        }
                    }
                } else item
            }
        }
        return if (ok) {
            Result.success("Submission graded ($score pts).")
        } else {
            Result.failure(IllegalStateException("Could not save grade to Firestore."))
        }
    }

    // =========================================================================
    // APPLICATION REVIEW WRITE (Dual-Write enrollmentRequests + Notification)
    // =========================================================================

    suspend fun updateEnrollmentRequest(
        request: EnrollmentRequest,
        newStatus: String, // "approved" | "rejected"
        rejectionReason: String? = null
    ): Result<String> {
        if (request.id.isBlank()) return Result.failure(IllegalArgumentException("Invalid application."))
        val cleanStatus = if (newStatus == "approved") "approved" else "rejected"
        val cleanReason = rejectionReason?.trim()?.ifBlank { null }

        val updates = mutableMapOf<String, Any?>(
            "status" to cleanStatus
        )
        if (cleanStatus == "rejected") {
            updates["rejectedAt"] = getCurrentIsoTimestamp()
            if (cleanReason != null) updates["rejectionReason"] = cleanReason
            request.sessionId?.ifBlank { null }?.let { updates["rejectedSessionId"] = it }
        }

        var anySaved = false
        if (request.userId.isNotBlank()) {
            val userDocId = resolveExistingDocId(request.userId, request.userId)
            val savedUser = updateListFieldInDoc(userDocId, "enrollmentRequests") { list ->
                list.map { item ->
                    if (item["id"]?.toString() == request.id) {
                        item.toMutableMap().apply { putAll(updates) }
                    } else item
                }
            }
            if (savedUser) anySaved = true
        }

        if (request.orgId.isNotBlank() && request.orgId != request.userId) {
            val orgDocId = resolveExistingDocId(request.orgId, "")
            if (orgDocId.isNotBlank() && orgDocId != request.userId) {
                val savedOrg = updateListFieldInDoc(orgDocId, "enrollmentRequests") { list ->
                    list.map { item ->
                        if (item["id"]?.toString() == request.id) {
                            item.toMutableMap().apply { putAll(updates) }
                        } else item
                    }
                }
                if (savedOrg) anySaved = true
            }
        }

        if (!anySaved) {
            val ownerDoc = findOwnerDocForListItem("enrollmentRequests", request.id, "")
            if (ownerDoc.isNotBlank()) {
                anySaved = updateListFieldInDoc(ownerDoc, "enrollmentRequests") { list ->
                    list.map { item ->
                        if (item["id"]?.toString() == request.id) {
                            item.toMutableMap().apply { putAll(updates) }
                        } else item
                    }
                }
            }
        }

        if (anySaved && request.userId.isNotBlank()) {
            val sessionInfo = request.sessionName?.ifBlank { null }?.let { " for $it" } ?: ""
            val courseTitle = request.courseTitle.ifBlank { "the course" }
            val notifMessage = if (cleanStatus == "approved") {
                "Congratulations! Your admission application for \"$courseTitle\"$sessionInfo has been approved."
            } else {
                val reasonSuffix = if (cleanReason != null) " Note: $cleanReason" else " You may reapply in the next admission session."
                "Your application for \"$courseTitle\"$sessionInfo was declined.$reasonSuffix"
            }
            pushUserNotification(
                targetUserId = request.userId,
                title = "Enrollment Application ${cleanStatus.uppercase()}",
                message = notifMessage,
                type = "enrollment",
                linkUrl = "/course/${request.courseId}"
            )
        }

        return if (anySaved) {
            Result.success(
                if (cleanStatus == "approved") "Application approved for ${request.userName.ifBlank { "student" }}."
                else "Application declined."
            )
        } else {
            Result.failure(IllegalStateException("Could not update application record."))
        }
    }

    fun observeNotifications(targetUserId: String? = null): Flow<List<NotificationItem>> = flow {
        val uid = targetUserId?.ifBlank { null } ?: currentUserId()
        if (uid.isBlank()) { emit(emptyList()); return@flow }
        db.collection("backpack").document(uid).snapshots().map { snap ->
            val userMap = BackpackSnapshotParser.getUserMap(snap.data)
            (userMap["notifications"] as? List<*>)?.mapNotNull { item ->
                val m = (item as? Map<*, *>)?.mapKeys { it.key.toString() } ?: return@mapNotNull null
                NotificationItem(
                    id = (m["id"] as? String) ?: "",
                    userId = (m["userId"] as? String),
                    courseId = (m["courseId"] as? String),
                    title = (m["title"] as? String) ?: "",
                    message = (m["message"] as? String) ?: "",
                    read = (m["read"] as? Boolean) ?: false,
                    type = (m["type"] as? String) ?: "info",
                    createdAt = (m["createdAt"] as? String) ?: "",
                    linkUrl = (m["linkUrl"] as? String)
                )
            } ?: emptyList()
        }.catch { emit(emptyList()) }.collect { emit(it) }
    }

    suspend fun markNotificationAsRead(notificationId: String): Boolean {
        val uid = currentUserId()
        if (uid.isBlank()) return false
        return updateListFieldInDoc(uid, "notifications") { list ->
            list.map { item ->
                if (item["id"]?.toString() == notificationId) {
                    item.toMutableMap().apply { put("read", true) }
                } else item
            }
        }
    }
}

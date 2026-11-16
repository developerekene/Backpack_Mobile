package com.example.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.BackpackRepository
import com.example.data.repository.BackpackSnapshotData
import com.example.data.repository.BackpackSnapshotParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackpackRepository(application)
    private var latestSnapshot = BackpackSnapshotData()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val _events = MutableStateFlow<List<ScheduleEvent>>(emptyList())
    val events: StateFlow<List<ScheduleEvent>> = _events

    private val _courses = MutableStateFlow<List<CourseItem>>(emptyList())
    val courses: StateFlow<List<CourseItem>> = _courses

    private val _allCourses = MutableStateFlow<List<CourseItem>>(emptyList())
    val allCourses: StateFlow<List<CourseItem>> = _allCourses

    private val _organizations = MutableStateFlow<List<Organization>>(emptyList())
    val organizations: StateFlow<List<Organization>> = _organizations

    private val _enrollmentRequests = MutableStateFlow<List<EnrollmentRequest>>(emptyList())
    val enrollmentRequests: StateFlow<List<EnrollmentRequest>> = _enrollmentRequests

    private val _orgJoinRequests = MutableStateFlow<List<OrgJoinRequest>>(emptyList())
    val orgJoinRequests: StateFlow<List<OrgJoinRequest>> = _orgJoinRequests

    private val _courseDonations = MutableStateFlow<List<CourseDonation>>(emptyList())
    val courseDonations: StateFlow<List<CourseDonation>> = _courseDonations

    private val _orgMembers = MutableStateFlow<List<OrgMember>>(emptyList())
    val orgMembers: StateFlow<List<OrgMember>> = _orgMembers

    private val _userProgressList = MutableStateFlow<List<UserProgress>>(emptyList())
    val userProgressList: StateFlow<List<UserProgress>> = _userProgressList

    private val _assessments = MutableStateFlow<List<Assessment>>(emptyList())
    val assessments: StateFlow<List<Assessment>> = _assessments

    private val _submissions = MutableStateFlow<List<Submission>>(emptyList())
    val submissions: StateFlow<List<Submission>> = _submissions

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords

    private val _checkInStatusMessage = MutableStateFlow<String?>(null)
    val checkInStatusMessage: StateFlow<String?> = _checkInStatusMessage

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    init {
        observeProfileAndSnapshot()
    }

    private fun observeProfileAndSnapshot() {
        viewModelScope.launch {
            repository.observeUserProfile().collectLatest { profile ->
                _userProfile.value = profile
                recompute(latestSnapshot, profile)
            }
        }
        viewModelScope.launch {
            repository.observeBackpackSnapshot().collectLatest { snap ->
                latestSnapshot = snap
                val uid = repository.currentUserId()
                val profile = _userProfile.value ?: repository.fetchUserProfileOnce(uid)
                recompute(snap, profile)
            }
        }
    }

    private fun recompute(snap: BackpackSnapshotData, profile: UserProfile) {
        val uid = repository.currentUserId()
        if (uid.isBlank()) return
        val role = profile.userRole
        val normalizedEmail = profile.email.lowercase().trim()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        _allCourses.value = snap.courses
        _organizations.value = snap.organizations

        val myOrg = BackpackSnapshotParser.resolveOrganizationForUser(snap, uid)
        val roleCourses = BackpackSnapshotParser.filterCoursesForRole(snap, profile, uid)
        _courses.value = roleCourses
        val roleCourseIds = roleCourses.map { it.id }.toSet()

        _enrollmentRequests.value = when (role) {
            UserRole.STUDENT -> snap.enrollmentRequests.filter { it.userId == uid }
            UserRole.ORGANISATION -> snap.enrollmentRequests.filter { req ->
                BackpackSnapshotParser.matchesOrganization(req.orgId, uid, myOrg) ||
                    req.courseId in roleCourseIds
            }
            UserRole.INSTRUCTOR -> snap.enrollmentRequests.filter { req ->
                req.courseId in roleCourseIds
            }
            UserRole.SUPER_ADMIN -> snap.enrollmentRequests
        }

        _orgJoinRequests.value = when (role) {
            UserRole.STUDENT -> snap.orgJoinRequests.filter { it.userId == uid }
            UserRole.ORGANISATION -> snap.orgJoinRequests.filter { req ->
                BackpackSnapshotParser.matchesOrganization(req.orgId, uid, myOrg)
            }
            UserRole.INSTRUCTOR -> emptyList()
            UserRole.SUPER_ADMIN -> snap.orgJoinRequests
        }

        _orgMembers.value = when (role) {
            UserRole.STUDENT -> snap.orgMembers.filter { m ->
                (normalizedEmail.isNotBlank() && m.email.lowercase().trim() == normalizedEmail) ||
                    m.userId == uid ||
                    m.id == uid
            }
            UserRole.ORGANISATION -> snap.orgMembers.filter { m ->
                BackpackSnapshotParser.matchesOrganization(m.orgId, uid, myOrg) ||
                    m.courseIds.any { it in roleCourseIds }
            }
            UserRole.INSTRUCTOR -> snap.orgMembers.filter { m ->
                (!profile.orgId.isNullOrBlank() && m.orgId == profile.orgId) ||
                    m.courseIds.any { it in roleCourseIds } ||
                    m.userId == uid ||
                    (normalizedEmail.isNotBlank() && m.email.lowercase().trim() == normalizedEmail)
            }
            UserRole.SUPER_ADMIN -> snap.orgMembers
        }

        _userProgressList.value = when (role) {
            UserRole.STUDENT -> snap.userProgressList.filter { it.userId == uid }
            UserRole.SUPER_ADMIN -> snap.userProgressList
            else -> snap.userProgressList.filter { it.courseId in roleCourseIds }
        }

        _assessments.value = when (role) {
            UserRole.SUPER_ADMIN -> snap.assessments
            else -> snap.assessments.filter { it.courseId in roleCourseIds }
        }

        _submissions.value = when (role) {
            UserRole.STUDENT -> snap.submissions.filter { it.userId == uid }
            UserRole.SUPER_ADMIN -> snap.submissions
            else -> snap.submissions.filter { it.courseId in roleCourseIds }
        }

        _attendanceRecords.value = when (role) {
            UserRole.ORGANISATION -> snap.attendanceRecords.filter { r ->
                r.courseId in roleCourseIds || BackpackSnapshotParser.matchesOrganization(r.orgId, uid, myOrg)
            }
            UserRole.SUPER_ADMIN -> snap.attendanceRecords
            else -> snap.attendanceRecords.filter { it.courseId in roleCourseIds }
        }

        _courseDonations.value = when (role) {
            UserRole.ORGANISATION -> snap.courseDonations.filter { d ->
                d.courseId in roleCourseIds || BackpackSnapshotParser.matchesOrganization(d.orgId, uid, myOrg)
            }
            UserRole.SUPER_ADMIN -> snap.courseDonations
            else -> snap.courseDonations.filter { it.courseId in roleCourseIds }
        }

        val checkedInTodayCourseIds = snap.attendanceRecords
            .filter { it.date == today && it.entries.any { e -> e.userId == uid } }
            .map { it.courseId }
            .toSet()

        _events.value = when (role) {
            UserRole.SUPER_ADMIN -> snap.scheduleEvents.map {
                it.copy(isCheckedIn = it.courseId in checkedInTodayCourseIds)
            }
            else -> snap.scheduleEvents
                .filter { it.courseId in roleCourseIds }
                .map { it.copy(isCheckedIn = it.courseId in checkedInTodayCourseIds) }
        }
    }

    fun checkIn(event: ScheduleEvent) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val course = _allCourses.value.firstOrNull { it.id == event.courseId }
                ?: CourseItem(id = event.courseId, title = event.title)
            val mode = if (!event.meetingUrl.isNullOrBlank()) "online" else "physical"
            val result = repository.clockInAttendance(
                course = course,
                mode = mode,
                location = if (mode == "online") "Live Session: ${event.title}" else "Campus Class: ${event.title}"
            )
            _isSubmitting.value = false
            result.onSuccess { msg ->
                _checkInStatusMessage.value = msg
            }.onFailure { err ->
                _checkInStatusMessage.value = err.message ?: "Unable to check in."
            }
        }
    }

    fun clearMessage() {
        _checkInStatusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _userProfile.value = null
        _events.value = emptyList()
        _courses.value = emptyList()
        _allCourses.value = emptyList()
        _organizations.value = emptyList()
        _enrollmentRequests.value = emptyList()
        _orgJoinRequests.value = emptyList()
        _courseDonations.value = emptyList()
        _orgMembers.value = emptyList()
        _userProgressList.value = emptyList()
        _assessments.value = emptyList()
        _submissions.value = emptyList()
        _attendanceRecords.value = emptyList()
        _checkInStatusMessage.value = null
        _isSubmitting.value = false
    }
}

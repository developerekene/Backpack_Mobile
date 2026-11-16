package com.example.ui.submissions

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

class SubmissionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackpackRepository(application)
    private var latestSnapshot = BackpackSnapshotData()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val _courses = MutableStateFlow<List<CourseItem>>(emptyList())
    val courses: StateFlow<List<CourseItem>> = _courses

    private val _organizations = MutableStateFlow<List<Organization>>(emptyList())
    val organizations: StateFlow<List<Organization>> = _organizations

    private val _enrollmentRequests = MutableStateFlow<List<EnrollmentRequest>>(emptyList())
    val enrollmentRequests: StateFlow<List<EnrollmentRequest>> = _enrollmentRequests

    private val _allEnrollmentRequests = MutableStateFlow<List<EnrollmentRequest>>(emptyList())
    val allEnrollmentRequests: StateFlow<List<EnrollmentRequest>> = _allEnrollmentRequests

    private val _orgJoinRequests = MutableStateFlow<List<OrgJoinRequest>>(emptyList())
    val orgJoinRequests: StateFlow<List<OrgJoinRequest>> = _orgJoinRequests

    private val _courseDonations = MutableStateFlow<List<CourseDonation>>(emptyList())
    val courseDonations: StateFlow<List<CourseDonation>> = _courseDonations

    private val _assessments = MutableStateFlow<List<Assessment>>(emptyList())
    val assessments: StateFlow<List<Assessment>> = _assessments

    private val _submissions = MutableStateFlow<List<Submission>>(emptyList())
    val submissions: StateFlow<List<Submission>> = _submissions

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords

    private val _scheduleEvents = MutableStateFlow<List<ScheduleEvent>>(emptyList())
    val scheduleEvents: StateFlow<List<ScheduleEvent>> = _scheduleEvents

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback

    init {
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
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val myOrg = BackpackSnapshotParser.resolveOrganizationForUser(snap, uid)
        val roleCourses = BackpackSnapshotParser.filterCoursesForRole(snap, profile, uid)
        val roleCourseIds = roleCourses.map { it.id }.toSet()

        _courses.value = roleCourses
        _organizations.value = snap.organizations
        _allEnrollmentRequests.value = snap.enrollmentRequests

        _enrollmentRequests.value = when (role) {
            UserRole.ORGANISATION -> snap.enrollmentRequests.filter { req ->
                BackpackSnapshotParser.matchesOrganization(req.orgId, uid, myOrg) ||
                    req.courseId in roleCourseIds
            }.sortedByDescending { it.appliedAt }
            UserRole.INSTRUCTOR -> snap.enrollmentRequests.filter { req ->
                req.courseId in roleCourseIds
            }.sortedByDescending { it.appliedAt }
            UserRole.SUPER_ADMIN -> snap.enrollmentRequests.sortedByDescending { it.appliedAt }
            UserRole.STUDENT -> snap.enrollmentRequests.filter { it.userId == uid }
        }

        _orgJoinRequests.value = when (role) {
            UserRole.ORGANISATION -> snap.orgJoinRequests.filter { req ->
                BackpackSnapshotParser.matchesOrganization(req.orgId, uid, myOrg)
            }
            UserRole.SUPER_ADMIN -> snap.orgJoinRequests
            else -> emptyList()
        }

        _courseDonations.value = when (role) {
            UserRole.ORGANISATION -> snap.courseDonations.filter { d ->
                d.courseId in roleCourseIds || BackpackSnapshotParser.matchesOrganization(d.orgId, uid, myOrg)
            }
            UserRole.SUPER_ADMIN -> snap.courseDonations
            else -> snap.courseDonations.filter { it.courseId in roleCourseIds }
        }

        _assessments.value = if (role == UserRole.SUPER_ADMIN) {
            snap.assessments
        } else {
            snap.assessments.filter { it.courseId in roleCourseIds }
        }

        _submissions.value = when (role) {
            UserRole.STUDENT -> snap.submissions.filter { it.userId == uid }
            UserRole.SUPER_ADMIN -> snap.submissions
            else -> snap.submissions.filter { it.courseId in roleCourseIds }
        }.sortedByDescending { it.submittedAt }

        _attendanceRecords.value = when (role) {
            UserRole.ORGANISATION -> snap.attendanceRecords.filter { r ->
                r.courseId in roleCourseIds || BackpackSnapshotParser.matchesOrganization(r.orgId, uid, myOrg)
            }
            UserRole.SUPER_ADMIN -> snap.attendanceRecords
            else -> snap.attendanceRecords.filter { it.courseId in roleCourseIds }
        }.sortedByDescending { it.date }

        val checkedInCourseIds = snap.attendanceRecords
            .filter { it.date == today && it.entries.any { e -> e.userId == uid } }
            .map { it.courseId }
            .toSet()

        _scheduleEvents.value = (if (role == UserRole.SUPER_ADMIN) {
            snap.scheduleEvents
        } else {
            snap.scheduleEvents.filter { it.courseId in roleCourseIds }
        }).map { it.copy(isCheckedIn = it.courseId in checkedInCourseIds) }
    }

    fun approveEnrollmentRequest(request: EnrollmentRequest, onDone: () -> Unit = {}) {
        val course = _courses.value.firstOrNull { it.id == request.courseId }
            ?: latestSnapshot.courses.firstOrNull { it.id == request.courseId }
        val gate = BackpackSnapshotParser.computeVocationalAdmissionGate(
            course = course,
            organizations = _organizations.value,
            courseDonations = _courseDonations.value,
            enrollmentRequests = _allEnrollmentRequests.value
        )
        if (gate != null && !gate.canAdmitMore) {
            _actionFeedback.value = "Vocational Sponsorship Gate: Insufficient donations to admit another student (${gate.currency} ${gate.nextSeatNeededAmount} needed)."
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateEnrollmentRequest(request, "approved")
            _isSubmitting.value = false
            res.onSuccess {
                _actionFeedback.value = it
                onDone()
            }.onFailure { err ->
                _actionFeedback.value = err.message ?: "Approval failed."
            }
        }
    }

    fun rejectEnrollmentRequest(request: EnrollmentRequest, reason: String?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateEnrollmentRequest(request, "rejected", reason)
            _isSubmitting.value = false
            res.onSuccess {
                _actionFeedback.value = it
                onDone()
            }.onFailure { err ->
                _actionFeedback.value = err.message ?: "Decline failed."
            }
        }
    }

    fun saveEssayScoresDraft(submission: Submission, essayScores: Map<String, Double>) {
        viewModelScope.launch {
            val ok = repository.saveEssayScoresDraft(submission, essayScores)
            _actionFeedback.value = if (ok) "Essay scores draft saved." else "Could not save draft."
        }
    }

    fun gradeSubmission(
        submission: Submission,
        score: Double,
        feedback: String,
        essayScores: Map<String, Double>?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.gradeSubmission(submission, score, feedback, essayScores)
            _isSubmitting.value = false
            res.onSuccess {
                _actionFeedback.value = it
                onDone()
            }.onFailure { err ->
                _actionFeedback.value = err.message ?: "Could not save grade."
            }
        }
    }

    fun checkInScheduleEvent(event: ScheduleEvent) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val course = _courses.value.firstOrNull { it.id == event.courseId }
                ?: CourseItem(id = event.courseId, title = event.title)
            val mode = if (!event.meetingUrl.isNullOrBlank()) "online" else "physical"
            val res = repository.clockInAttendance(
                course = course,
                mode = mode,
                location = if (mode == "online") "Live Session: ${event.title}" else "Campus Class: ${event.title}"
            )
            _isSubmitting.value = false
            res.onSuccess { _actionFeedback.value = it }
                .onFailure { _actionFeedback.value = it.message ?: "Check-in failed." }
        }
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _userProfile.value = null
        _courses.value = emptyList()
        _organizations.value = emptyList()
        _enrollmentRequests.value = emptyList()
        _allEnrollmentRequests.value = emptyList()
        _orgJoinRequests.value = emptyList()
        _courseDonations.value = emptyList()
        _assessments.value = emptyList()
        _submissions.value = emptyList()
        _attendanceRecords.value = emptyList()
        _scheduleEvents.value = emptyList()
        _actionFeedback.value = null
        _isSubmitting.value = false
    }
}

package com.example.ui.courses

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.BackpackRepository
import com.example.data.repository.BackpackSnapshotData
import com.example.data.repository.BackpackSnapshotParser
import com.example.util.FileUploadUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CoursesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackpackRepository(application)
    private var latestSnapshot = BackpackSnapshotData()
    private val ensuredDefaultChannelCourses = mutableSetOf<String>()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val _courses = MutableStateFlow<List<CourseItem>>(emptyList())
    val courses: StateFlow<List<CourseItem>> = _courses

    private val _organizations = MutableStateFlow<List<Organization>>(emptyList())
    val organizations: StateFlow<List<Organization>> = _organizations

    private val _selectedCourse = MutableStateFlow<CourseItem?>(null)
    val selectedCourse: StateFlow<CourseItem?> = _selectedCourse

    private val _userProgress = MutableStateFlow<UserProgress?>(null)
    val userProgress: StateFlow<UserProgress?> = _userProgress

    private val _lessons = MutableStateFlow<List<LessonNote>>(emptyList())
    val lessons: StateFlow<List<LessonNote>> = _lessons

    private val _materials = MutableStateFlow<List<CourseMaterial>>(emptyList())
    val materials: StateFlow<List<CourseMaterial>> = _materials

    private val _assessments = MutableStateFlow<List<Assessment>>(emptyList())
    val assessments: StateFlow<List<Assessment>> = _assessments

    private val _submissions = MutableStateFlow<List<Submission>>(emptyList())
    val submissions: StateFlow<List<Submission>> = _submissions

    private val _courseMembers = MutableStateFlow<List<OrgMember>>(emptyList())
    val courseMembers: StateFlow<List<OrgMember>> = _courseMembers

    private val _courseEnrollmentRequests = MutableStateFlow<List<EnrollmentRequest>>(emptyList())
    val courseEnrollmentRequests: StateFlow<List<EnrollmentRequest>> = _courseEnrollmentRequests

    private val _discussionChannels = MutableStateFlow<List<DiscussionChannel>>(emptyList())
    val discussionChannels: StateFlow<List<DiscussionChannel>> = _discussionChannels

    private val _activeChannelId = MutableStateFlow<String?>(null)
    val activeChannelId: StateFlow<String?> = _activeChannelId

    private val _discussionMessages = MutableStateFlow<List<DiscussionMessage>>(emptyList())
    val discussionMessages: StateFlow<List<DiscussionMessage>> = _discussionMessages

    private val _discussionPolls = MutableStateFlow<List<DiscussionPoll>>(emptyList())
    val discussionPolls: StateFlow<List<DiscussionPoll>> = _discussionPolls

    private val _coursePresence = MutableStateFlow<List<CoursePresence>>(emptyList())
    val coursePresence: StateFlow<List<CoursePresence>> = _coursePresence

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords

    private val _scheduleEvents = MutableStateFlow<List<ScheduleEvent>>(emptyList())
    val scheduleEvents: StateFlow<List<ScheduleEvent>> = _scheduleEvents

    private val _savedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val savedMessageIds: StateFlow<Set<String>> = _savedMessageIds

    private val _activeVideoUrl = MutableStateFlow<String?>(null)
    val activeVideoUrl: StateFlow<String?> = _activeVideoUrl

    private val _activePdfName = MutableStateFlow<String?>(null)
    val activePdfName: StateFlow<String?> = _activePdfName

    private val _activePdfUrl = MutableStateFlow<String?>(null)
    val activePdfUrl: StateFlow<String?> = _activePdfUrl

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback

    init {
        loadSavedMessages()
        viewModelScope.launch {
            repository.observeUserProfile().collectLatest { profile ->
                _userProfile.value = profile
                recomputeFromSnapshot(latestSnapshot)
            }
        }
        viewModelScope.launch {
            repository.observeBackpackSnapshot().collectLatest { snap ->
                latestSnapshot = snap
                recomputeFromSnapshot(snap)
            }
        }
    }

    private fun loadSavedMessages() {
        val uid = repository.currentUserId()
        if (uid.isBlank()) return
        val prefs = getApplication<Application>().getSharedPreferences("bp_saved_msgs", Context.MODE_PRIVATE)
        _savedMessageIds.value = prefs.getStringSet("saved_$uid", emptySet())?.toSet() ?: emptySet()
    }

    fun toggleSavedMessage(messageId: String) {
        val uid = repository.currentUserId()
        if (uid.isBlank()) return
        val current = _savedMessageIds.value.toMutableSet()
        if (messageId in current) current.remove(messageId) else current.add(messageId)
        _savedMessageIds.value = current
        val prefs = getApplication<Application>().getSharedPreferences("bp_saved_msgs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("saved_$uid", current).apply()
    }

    private fun recomputeFromSnapshot(snap: BackpackSnapshotData) {
        val uid = repository.currentUserId()
        if (uid.isBlank()) return
        val profile = _userProfile.value ?: UserProfile(
            userId = uid,
            email = repository.currentUserEmail(),
            name = repository.currentUserDisplayName()
        )
        val role = profile.userRole

        _organizations.value = snap.organizations
        val roleCourses = BackpackSnapshotParser.filterCoursesForRole(snap, profile, uid)
        _courses.value = roleCourses

        val currentSelId = _selectedCourse.value?.id
        val nextCourse = roleCourses.firstOrNull { it.id == currentSelId } ?: roleCourses.firstOrNull()
        _selectedCourse.value = nextCourse

        if (nextCourse != null) {
            bindCourseScopedData(nextCourse, snap, uid, role)
        } else {
            clearCourseScopedData()
        }
    }

    private fun bindCourseScopedData(
        course: CourseItem,
        snap: BackpackSnapshotData,
        uid: String,
        role: UserRole
    ) {
        _userProgress.value = snap.userProgressList.firstOrNull { it.userId == uid && it.courseId == course.id }
        _lessons.value = course.modules.flatMap { it.items }
        _materials.value = snap.materials.filter { it.courseId == course.id }
        _assessments.value = snap.assessments.filter { it.courseId == course.id }
        _submissions.value = if (role == UserRole.STUDENT) {
            snap.submissions.filter { it.courseId == course.id && it.userId == uid }
        } else {
            snap.submissions.filter { it.courseId == course.id }
        }
        _courseMembers.value = snap.orgMembers.filter { m ->
            course.id in m.courseIds || m.orgId == course.orgId || m.orgId == "org_${course.orgId}"
        }
        _courseEnrollmentRequests.value = snap.enrollmentRequests.filter { req ->
            req.courseId == course.id && req.status == "approved"
        }

        val channels = snap.discussionChannels
            .filter { it.courseId == course.id }
            .sortedBy { it.order }
        _discussionChannels.value = channels

        if (channels.isEmpty() && ensuredDefaultChannelCourses.add(course.id)) {
            viewModelScope.launch {
                repository.ensureDefaultDiscussionChannel(course.id, course.orgId)
            }
        }
        val activeChan = channels.firstOrNull { it.id == _activeChannelId.value } ?: channels.firstOrNull()
        _activeChannelId.value = activeChan?.id ?: "dchan_general_${course.id}"

        _discussionMessages.value = snap.discussionMessages
            .filter { it.courseId == course.id }
            .sortedBy { it.createdAt }
        _discussionPolls.value = snap.discussionPolls
            .filter { it.courseId == course.id }
            .sortedByDescending { it.createdAt }
        _coursePresence.value = snap.coursePresence.filter { it.courseId == course.id }
        _attendanceRecords.value = snap.attendanceRecords
            .filter { it.courseId == course.id || (!it.courseTitle.isNullOrBlank() && it.courseTitle == course.title) }
            .sortedByDescending { it.date }
        _scheduleEvents.value = snap.scheduleEvents
            .filter { it.courseId == course.id }
            .sortedBy { it.date }
    }

    private fun clearCourseScopedData() {
        _userProgress.value = null
        _lessons.value = emptyList()
        _materials.value = emptyList()
        _assessments.value = emptyList()
        _submissions.value = emptyList()
        _courseMembers.value = emptyList()
        _courseEnrollmentRequests.value = emptyList()
        _discussionChannels.value = emptyList()
        _activeChannelId.value = null
        _discussionMessages.value = emptyList()
        _discussionPolls.value = emptyList()
        _coursePresence.value = emptyList()
        _attendanceRecords.value = emptyList()
        _scheduleEvents.value = emptyList()
    }

    fun selectCourse(course: CourseItem) {
        _selectedCourse.value = course
        _activeVideoUrl.value = null
        _activePdfName.value = null
        _activePdfUrl.value = null
        _activeChannelId.value = null
        val uid = repository.currentUserId()
        val role = _userProfile.value?.userRole ?: UserRole.STUDENT
        bindCourseScopedData(course, latestSnapshot, uid, role)
    }

    fun selectChannel(channelId: String) {
        _activeChannelId.value = channelId
    }

    fun toggleChannelSubscription(channelId: String) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            repository.toggleChannelSubscription(course.id, course.orgId, channelId)
        }
    }

    fun sendMessage(
        text: String,
        attachmentUri: Uri? = null,
        manualAttachmentUrl: String? = null,
        parentId: String? = null,
        onSent: () -> Unit = {}
    ) {
        val course = _selectedCourse.value ?: return
        val channelId = _activeChannelId.value ?: "dchan_general_${course.id}"
        if (text.isBlank() && attachmentUri == null && manualAttachmentUrl.isNullOrBlank()) return

        viewModelScope.launch {
            _isSubmitting.value = true
            var resolvedUrl = manualAttachmentUrl?.trim()?.ifBlank { null }
            var resolvedType: String? = if (resolvedUrl != null) "document" else null

            if (attachmentUri != null) {
                val uploadRes = FileUploadUtil.uploadUri(getApplication(), attachmentUri)
                if (uploadRes.isFailure) {
                    _actionFeedback.value = uploadRes.exceptionOrNull()?.message ?: "File upload failed."
                    _isSubmitting.value = false
                    return@launch
                }
                val uploaded = uploadRes.getOrThrow()
                resolvedUrl = uploaded.url
                resolvedType = uploaded.fileType
            }

            val ok = repository.sendDiscussionMessage(
                courseId = course.id,
                orgId = course.orgId,
                channelId = channelId,
                text = text,
                parentId = parentId,
                fileUrl = resolvedUrl,
                fileType = resolvedType
            )
            _isSubmitting.value = false
            if (ok) {
                onSent()
            } else {
                _actionFeedback.value = "Failed to send message."
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            repository.toggleMessageReaction(course.orgId, messageId, emoji)
        }
    }

    fun votePoll(pollId: String, optionId: String) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            val ok = repository.voteOnPoll(course.orgId, pollId, optionId)
            if (!ok) _actionFeedback.value = "Could not record poll vote."
        }
    }

    fun verifyDiscussionMessage(messageId: String, verified: Boolean) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            val ok = repository.setDiscussionMessageVerified(course.orgId, messageId, verified)
            if (!ok) _actionFeedback.value = "Could not update endorsement."
        }
    }

    fun pinDiscussionMessage(messageId: String, pinned: Boolean) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            val ok = repository.togglePinDiscussionMessage(course.orgId, messageId, pinned)
            if (!ok) _actionFeedback.value = "Could not update pin state."
        }
    }

    fun createChannel(name: String, description: String?, onDone: () -> Unit) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createDiscussionChannel(
                courseId = course.id,
                orgId = course.orgId,
                name = name,
                description = description,
                existingCount = _discussionChannels.value.size
            )
            _isSubmitting.value = false
            res.onSuccess { ch ->
                _activeChannelId.value = ch.id
                _actionFeedback.value = "Channel #${ch.name} created."
                onDone()
            }.onFailure { err ->
                _actionFeedback.value = err.message ?: "Failed to create channel."
            }
        }
    }

    fun createPoll(question: String, options: List<String>, onDone: () -> Unit) {
        val course = _selectedCourse.value ?: return
        val chanId = _activeChannelId.value ?: "dchan_general_${course.id}"
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createDiscussionPoll(
                courseId = course.id,
                orgId = course.orgId,
                channelId = chanId,
                question = question,
                options = options
            )
            _isSubmitting.value = false
            res.onSuccess {
                _actionFeedback.value = "Cohort poll published."
                onDone()
            }.onFailure { err ->
                _actionFeedback.value = err.message ?: "Failed to publish poll."
            }
        }
    }

    fun pingPresence() {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            repository.pingCoursePresence(course.id, course.orgId)
        }
    }

    fun clockInAttendance(mode: String, location: String) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.clockInAttendance(course, mode, location)
            _isSubmitting.value = false
            res.onSuccess { _actionFeedback.value = it }
                .onFailure { _actionFeedback.value = it.message ?: "Check-in failed." }
        }
    }

    fun clockOutAttendance(mode: String) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.clockOutAttendance(course, mode)
            _isSubmitting.value = false
            res.onSuccess { _actionFeedback.value = it }
                .onFailure { _actionFeedback.value = it.message ?: "Check-out failed." }
        }
    }

    fun saveRollCallSession(
        date: String,
        mode: String,
        sessionTitle: String,
        location: String,
        entries: List<AttendanceEntry>,
        existingRecord: AttendanceRecord?,
        onDone: () -> Unit
    ) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.saveAttendanceSession(
                course = course,
                date = date,
                mode = mode,
                sessionTitle = sessionTitle,
                location = location,
                entries = entries,
                existingRecord = existingRecord
            )
            _isSubmitting.value = false
            res.onSuccess {
                _actionFeedback.value = it
                onDone()
            }.onFailure { err ->
                _actionFeedback.value = err.message ?: "Failed to save roll call."
            }
        }
    }

    fun kioskClockParticipant(
        participantId: String,
        participantName: String,
        participantEmail: String?,
        participantRole: String,
        action: String
    ) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.kioskClockParticipant(
                course = course,
                participantId = participantId,
                participantName = participantName,
                participantEmail = participantEmail,
                participantRole = participantRole,
                action = action
            )
            _isSubmitting.value = false
            res.onSuccess { _actionFeedback.value = it }
                .onFailure { _actionFeedback.value = it.message ?: "Kiosk update failed." }
        }
    }

    fun deleteAttendanceRecord(record: AttendanceRecord) {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            val ok = repository.deleteAttendanceRecord(record, course.orgId)
            _actionFeedback.value = if (ok) "Attendance session deleted." else "Could not delete record."
        }
    }

    fun submitAssessmentWork(
        assessment: Assessment,
        content: String,
        attachmentUri: Uri?,
        manualFileUrl: String?,
        existingSubmissionId: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            var resolvedFileUrl = manualFileUrl?.trim()?.ifBlank { null }
            if (attachmentUri != null) {
                val uploadRes = FileUploadUtil.uploadUri(getApplication(), attachmentUri)
                if (uploadRes.isFailure) {
                    _actionFeedback.value = uploadRes.exceptionOrNull()?.message ?: "Upload failed."
                    _isSubmitting.value = false
                    return@launch
                }
                resolvedFileUrl = uploadRes.getOrThrow().url
            }

            val res = repository.submitStudentAssessment(
                assessment = assessment,
                content = content,
                fileUrl = resolvedFileUrl,
                existingSubmissionId = existingSubmissionId
            )
            _isSubmitting.value = false
            res.onSuccess {
                _actionFeedback.value = "${assessment.title} submitted successfully!"
                onSuccess()
            }.onFailure { err ->
                _actionFeedback.value = err.message ?: "Submission failed."
            }
        }
    }

    fun saveEssayScoresDraft(submission: Submission, essayScores: Map<String, Double>) {
        viewModelScope.launch {
            val ok = repository.saveEssayScoresDraft(submission, essayScores)
            _actionFeedback.value = if (ok) "Essay scores draft saved." else "Failed to save essay draft."
        }
    }

    fun gradeSubmission(
        submission: Submission,
        score: Double,
        feedback: String,
        essayScores: Map<String, Double>?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.gradeSubmission(submission, score, feedback, essayScores)
            _isSubmitting.value = false
            res.onSuccess {
                _actionFeedback.value = it
                onDone()
            }.onFailure { err ->
                _actionFeedback.value = err.message ?: "Failed to grade submission."
            }
        }
    }

    fun playVideo(videoUrl: String) { _activeVideoUrl.value = videoUrl }
    fun closeVideo() { _activeVideoUrl.value = null }
    fun openPdfAttachment(name: String, url: String? = null) {
        _activePdfName.value = name
        _activePdfUrl.value = url
    }
    fun closePdfAttachment() {
        _activePdfName.value = null
        _activePdfUrl.value = null
    }
    fun clearFeedback() { _actionFeedback.value = null }

    fun currentUserId(): String = repository.currentUserId()

    override fun onCleared() {
        super.onCleared()
        _courses.value = emptyList()
        _selectedCourse.value = null
        _userProgress.value = null
        _lessons.value = emptyList()
        _materials.value = emptyList()
        _assessments.value = emptyList()
        _submissions.value = emptyList()
        _courseMembers.value = emptyList()
        _courseEnrollmentRequests.value = emptyList()
        _discussionChannels.value = emptyList()
        _discussionMessages.value = emptyList()
        _discussionPolls.value = emptyList()
        _coursePresence.value = emptyList()
        _attendanceRecords.value = emptyList()
        _scheduleEvents.value = emptyList()
        _activeVideoUrl.value = null
        _activePdfName.value = null
        _activePdfUrl.value = null
        _actionFeedback.value = null
        _isSubmitting.value = false
    }
}

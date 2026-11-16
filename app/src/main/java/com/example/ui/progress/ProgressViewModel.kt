package com.example.ui.progress

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

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackpackRepository(application)
    private var latestSnapshot = BackpackSnapshotData()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val _enrolledCourses = MutableStateFlow<List<CourseItem>>(emptyList())
    val enrolledCourses: StateFlow<List<CourseItem>> = _enrolledCourses

    private val _organizations = MutableStateFlow<List<Organization>>(emptyList())
    val organizations: StateFlow<List<Organization>> = _organizations

    private val _userProgressList = MutableStateFlow<List<UserProgress>>(emptyList())
    val userProgressList: StateFlow<List<UserProgress>> = _userProgressList

    private val _assessments = MutableStateFlow<List<Assessment>>(emptyList())
    val assessments: StateFlow<List<Assessment>> = _assessments

    private val _submissions = MutableStateFlow<List<Submission>>(emptyList())
    val submissions: StateFlow<List<Submission>> = _submissions

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords

    init {
        viewModelScope.launch {
            repository.observeUserProfile().collectLatest { profile ->
                _userProfile.value = profile
                recompute(latestSnapshot)
            }
        }
        viewModelScope.launch {
            repository.observeBackpackSnapshot().collectLatest { snap ->
                latestSnapshot = snap
                recompute(snap)
            }
        }
    }

    private fun recompute(snap: BackpackSnapshotData) {
        val uid = repository.currentUserId()
        if (uid.isBlank()) return
        val profile = _userProfile.value
        val email = profile?.email ?: repository.currentUserEmail()

        _organizations.value = snap.organizations
        val enrolled = BackpackSnapshotParser.filterEnrolledCoursesForStudent(snap, uid, email)
        _enrolledCourses.value = enrolled
        val enrolledIds = enrolled.map { it.id }.toSet()

        _userProgressList.value = snap.userProgressList.filter { it.userId == uid }
        _assessments.value = snap.assessments.filter { it.courseId in enrolledIds }
        _submissions.value = snap.submissions
            .filter { it.userId == uid }
            .sortedByDescending { it.submittedAt }
        _attendanceRecords.value = snap.attendanceRecords
            .filter { it.courseId in enrolledIds }
            .sortedByDescending { it.date }
    }

    fun currentUserId(): String = repository.currentUserId()

    override fun onCleared() {
        super.onCleared()
        _userProfile.value = null
        _enrolledCourses.value = emptyList()
        _organizations.value = emptyList()
        _userProgressList.value = emptyList()
        _assessments.value = emptyList()
        _submissions.value = emptyList()
        _attendanceRecords.value = emptyList()
    }
}

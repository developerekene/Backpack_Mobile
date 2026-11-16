package com.example.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Submission
import com.example.data.repository.BackpackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class CaptureMode {
    DOCUMENT_SCAN,
    AUDIO_RECORDER
}

class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackpackRepository(application)

    private val _selectedMode = MutableStateFlow(CaptureMode.DOCUMENT_SCAN)
    val selectedMode: StateFlow<CaptureMode> = _selectedMode

    private val _submissions = MutableStateFlow<List<Submission>>(emptyList())
    val submissions: StateFlow<List<Submission>> = _submissions

    // Audio recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds

    private var timerJob: Job? = null

    // Form inputs
    val titleInput = MutableStateFlow("")
    val noteInput = MutableStateFlow("")
    val selectedCourseId = MutableStateFlow("")
    val selectedCourseTitle = MutableStateFlow("")

    private val _submissionSuccess = MutableStateFlow<String?>(null)
    val submissionSuccess: StateFlow<String?> = _submissionSuccess

    init {
        loadSubmissions()
    }

    private fun loadSubmissions() {
        viewModelScope.launch {
            repository.observeSubmissions().collectLatest { list ->
                _submissions.value = list
            }
        }
    }

    fun setMode(mode: CaptureMode) {
        _selectedMode.value = mode
        if (_isRecording.value) {
            stopRecording()
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _isRecording.value = true
        _recordingSeconds.value = 0
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isRecording.value) {
                delay(1000)
                _recordingSeconds.value += 1
            }
        }
    }

    private fun stopRecording() {
        _isRecording.value = false
        timerJob?.cancel()
    }

    fun submitAssignment(capturedFileUri: String? = null) {
        viewModelScope.launch {
            val typeStr = if (_selectedMode.value == CaptureMode.DOCUMENT_SCAN) "Document Scan" else "Voice Note"
            val assessment = com.example.data.model.Assessment(
                id = "mobile_capture_${System.currentTimeMillis()}",
                courseId = selectedCourseId.value,
                title = titleInput.value.ifBlank { typeStr },
                type = "assignment"
            )
            val result = repository.submitStudentAssessment(
                assessment = assessment,
                content = "Capture Type: $typeStr. Note: ${noteInput.value}",
                fileUrl = capturedFileUri
            )
            if (result.isSuccess) {
                _submissionSuccess.value = "Capture submitted successfully to ${selectedCourseId.value}!"
                titleInput.value = ""
                noteInput.value = ""
                stopRecording()
            }
        }
    }

    private fun getCurrentIsoTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    fun clearSuccessMessage() {
        _submissionSuccess.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
        _submissions.value = emptyList()
        titleInput.value = ""
        noteInput.value = ""
        selectedCourseId.value = ""
        selectedCourseTitle.value = ""
        _submissionSuccess.value = null
    }
}

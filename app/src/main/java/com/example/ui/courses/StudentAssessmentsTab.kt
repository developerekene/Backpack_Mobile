package com.example.ui.courses

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Assessment
import com.example.data.model.Submission
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.util.FileUploadUtil
import java.util.Locale

fun LazyListScope.studentAssessmentsTab(
    courseId: String,
    assessments: List<Assessment>,
    submissions: List<Submission>,
    isSubmitting: Boolean,
    onSubmitAssessment: (
        assessment: Assessment,
        content: String,
        attachmentUri: Uri?,
        manualFileUrl: String?,
        existingSubmissionId: String?,
        onDone: () -> Unit
    ) -> Unit,
    onOpenAttachment: (String, String?) -> Unit
) {
    val totalScore = submissions.sumOf { it.score ?: 0.0 }
    val totalMaxScore = assessments.sumOf { it.maxScore }
    val gpaText = if (totalMaxScore > 0) {
        String.format(Locale.US, "%.2f", (totalScore / totalMaxScore.toDouble()) * 4.0)
    } else "N/A"

    // Academic Transcript Card matching Web StudentTranscriptView
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("student_transcript_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackpackPrimaryBlue)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Academic Transcript", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${submissions.size} submitted • ${assessments.size} total assessments",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (totalScore % 1.0 == 0.0) totalScore.toInt() else String.format(Locale.US, "%.1f", totalScore)} / $totalMaxScore",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "GPA Equiv: $gpaText",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Policy Notice Banner
    item {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = BackpackPrimaryBlue)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Assignments, classwork & projects can be submitted here. Quizzes, tests & exams are read-only on mobile and must be taken on Backpack Web.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (assessments.isEmpty()) {
        item {
            EmptyStateText("No assessments scheduled yet for this course.")
        }
    } else {
        items(assessments, key = { it.id }) { assessment ->
            val sub = submissions.firstOrNull { it.assessmentId == assessment.id }
            StudentAssessmentCard(
                courseId = courseId,
                assessment = assessment,
                submission = sub,
                isSubmitting = isSubmitting,
                onSubmitAssessment = onSubmitAssessment,
                onOpenAttachment = onOpenAttachment
            )
        }
    }
}

@Composable
private fun StudentAssessmentCard(
    courseId: String,
    assessment: Assessment,
    submission: Submission?,
    isSubmitting: Boolean,
    onSubmitAssessment: (Assessment, String, Uri?, String?, String?, () -> Unit) -> Unit,
    onOpenAttachment: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var showSubmitModal by remember { mutableStateOf(false) }

    val typeLabel = assessment.type.replaceFirstChar { it.uppercase() }
    val canSubmit = assessment.isMobileSubmittable &&
        (submission == null || (assessment.allowResubmission && submission.status != "graded"))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_assessment_card_${assessment.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = if (assessment.isMobileSubmittable) BackpackPrimaryBlue.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = typeLabel.uppercase(),
                                color = if (assessment.isMobileSubmittable) BackpackPrimaryBlue else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        if (assessment.isGroup) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "GROUP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(assessment.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    val meta = buildList {
                        assessment.subject?.ifBlank { null }?.let { add(it) }
                        if (assessment.dueDate.isNotBlank()) add("Due ${assessment.dueDate.take(10)}")
                        assessment.durationMinutes?.let { add("${it} min") }
                        add("${assessment.maxScore} pts")
                    }.joinToString(" • ")
                    Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Submission Status Badge
                when {
                    submission?.status == "graded" -> {
                        Surface(
                            color = BackpackStatusGreen.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("GRADED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BackpackStatusGreen)
                                Text(
                                    text = "${submission.score ?: 0}/${assessment.maxScore}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BackpackStatusGreen
                                )
                            }
                        }
                    }
                    submission != null -> {
                        Surface(
                            color = BackpackStatusOrange.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (submission.pendingEssayGrading) "ESSAY REVIEW" else "SUBMITTED",
                                color = BackpackStatusOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "NOT SUBMITTED",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Instructions
            assessment.description?.ifBlank { null }?.let { desc ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Instructions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BackpackPrimaryBlue)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(desc, fontSize = 12.sp)
                    }
                }
            }

            // Assessment Instructor Attachments
            if (assessment.attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    assessment.attachments.forEachIndexed { index, url ->
                        if (url.startsWith("data:image/")) {
                            BackpackImagePreview(
                                imageUrl = url,
                                contentDescription = "Attachment ${index + 1}",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            AssistChip(
                                onClick = {
                                    if (url.startsWith("http", ignoreCase = true)) {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    } else {
                                        onOpenAttachment("${assessment.title} Attachment ${index + 1}", url)
                                    }
                                },
                                label = { Text("Attachment ${index + 1}", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            // Existing Submission Details (Response + Attachment + Instructor Feedback)
            if (submission != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Submitted: ${submission.submittedAt.take(16).replace("T", " ")}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (submission.content.isNotBlank()) {
                            Text("Your Response: ${submission.content}", fontSize = 12.sp)
                        }
                        submission.autoGradedMax?.let { maxAuto ->
                            if (maxAuto > 0) {
                                Text(
                                    text = "Auto-graded: ${submission.autoGradedPoints ?: 0.0}/$maxAuto pts",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BackpackPrimaryBlue
                                )
                            }
                        }
                        submission.feedback?.ifBlank { null }?.let { fb ->
                            Text(
                                text = "Instructor Feedback: $fb",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = BackpackStatusGreen
                            )
                        }
                        submission.fileUrl?.ifBlank { null }?.let { fileUrl ->
                            TextButton(
                                onClick = {
                                    if (fileUrl.startsWith("http", ignoreCase = true)) {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl)))
                                        }
                                    } else {
                                        onOpenAttachment("My Submitted File", fileUrl)
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View My Submitted File", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Submit / Read-Only Web Portal Action Button
            if (canSubmit) {
                Button(
                    onClick = { showSubmitModal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("submit_assessment_button_${assessment.id}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (submission != null) "Resubmit $typeLabel" else "Submit $typeLabel",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!assessment.isMobileSubmittable) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (assessment.questions.isNotEmpty()) {
                        Text(
                            text = "${assessment.questions.size} structured questions • View-only on mobile",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (submission == null) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://backpack-edu.com/course/$courseId")
                                    )
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take $typeLabel on Backpack Web")
                        }
                    }
                }
            }
        }
    }

    if (showSubmitModal) {
        AssessmentSubmissionDialog(
            assessment = assessment,
            existingSubmission = submission,
            isSubmitting = isSubmitting,
            onDismiss = { showSubmitModal = false },
            onSubmit = { content, uri, manualUrl ->
                onSubmitAssessment(
                    assessment,
                    content,
                    uri,
                    manualUrl,
                    submission?.id
                ) {
                    showSubmitModal = false
                }
            }
        )
    }
}

@Composable
private fun AssessmentSubmissionDialog(
    assessment: Assessment,
    existingSubmission: Submission?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, Uri?, String?) -> Unit
) {
    val context = LocalContext.current
    var responseText by remember { mutableStateOf(existingSubmission?.content ?: "") }
    var manualUrl by remember { mutableStateOf(existingSubmission?.fileUrl?.takeIf { it.startsWith("http") } ?: "") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Submit ${assessment.type.replaceFirstChar { it.uppercase() }}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = assessment.title,
                    fontSize = 13.sp,
                    color = BackpackPrimaryBlue,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = responseText,
                    onValueChange = { responseText = it },
                    label = { Text("Response / Notes / Work Link") },
                    placeholder = { Text("Enter summary, text answer, or link to your work") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assessment_response_input"),
                    minLines = 3,
                    maxLines = 5
                )

                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text("External File URL (Optional)") },
                    placeholder = { Text("https://drive.google.com/...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Photo/Video", fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            documentPicker.launch(arrayOf("application/pdf", "application/*", "text/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Document", fontSize = 12.sp, maxLines = 1)
                    }
                }

                pickedUri?.let { uri ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Selected: ${FileUploadUtil.getFileName(context, uri)}",
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { pickedUri = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSubmit(responseText, pickedUri, manualUrl.ifBlank { null })
                        },
                        enabled = !isSubmitting && (responseText.isNotBlank() || pickedUri != null || manualUrl.isNotBlank()),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("confirm_submit_assessment_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Submit Work", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

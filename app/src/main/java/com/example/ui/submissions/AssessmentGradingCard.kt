package com.example.ui.submissions

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Assessment
import com.example.data.model.Submission
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.ui.theme.BackpackStatusRed
import java.util.Locale

@Composable
fun AssessmentSubmissionGradingCard(
    submission: Submission,
    assessment: Assessment?,
    courseTitle: String? = null,
    isSubmitting: Boolean,
    onSaveEssayDraft: (Submission, Map<String, Double>) -> Unit,
    onGradeSubmission: (Submission, Double, String, Map<String, Double>?, () -> Unit) -> Unit,
    onOpenAttachment: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val maxScore = assessment?.maxScore ?: 100
    val questions = assessment?.questions.orEmpty()
    val hasStructuredQuestions = questions.isNotEmpty() && submission.answers.isNotEmpty()
    val essayQuestions = remember(questions) {
        questions.filter { it.format == "essay" || it.questionType == "essay" }
    }
    val objectiveQuestions = remember(questions) {
        questions.filter { it.format != "essay" && it.questionType != "essay" }
    }

    val autoPoints = remember(submission, objectiveQuestions) {
        submission.autoGradedPoints ?: objectiveQuestions.sumOf { q ->
            val ans = submission.answers[q.id]?.trim().orEmpty()
            val isCorrect = when {
                q.questionType == "true_false" || q.options.isEmpty() -> {
                    q.correctBoolean?.toString()?.equals(ans, ignoreCase = true) == true
                }
                else -> {
                    val correctOpt = q.options.firstOrNull { it.isCorrect }
                    correctOpt != null && (ans == correctOpt.id || ans.equals(correctOpt.text, ignoreCase = true))
                }
            }
            if (isCorrect) q.points.toDouble() else 0.0
        }
    }

    var expandedDetails by remember(submission.id) { mutableStateOf(submission.status != "graded") }
    var draftEssayScores by remember(submission.id, submission.essayScores) {
        mutableStateOf(
            essayQuestions.associate { q ->
                q.id to (submission.essayScores[q.id]?.toString() ?: "")
            }
        )
    }

    val computedEssaySum = draftEssayScores.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val computedTotal = autoPoints + computedEssaySum

    var manualScoreText by remember(submission.id, submission.score) {
        mutableStateOf(
            if (submission.score != null) {
                if (submission.score % 1.0 == 0.0) submission.score.toInt().toString()
                else String.format(Locale.US, "%.1f", submission.score)
            } else if (hasStructuredQuestions) {
                if (computedTotal % 1.0 == 0.0) computedTotal.toInt().toString()
                else String.format(Locale.US, "%.1f", computedTotal)
            } else ""
        )
    }
    var feedbackText by remember(submission.id, submission.feedback) {
        mutableStateOf(submission.feedback.orEmpty())
    }

    val isGraded = submission.status == "graded"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("grading_submission_card_${submission.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDetails = !expandedDetails },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = submission.userName?.ifBlank { null } ?: submission.userId,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (assessment != null) {
                        Text(
                            text = "${assessment.title} (${assessment.type.uppercase()})",
                            fontSize = 12.sp,
                            color = BackpackPrimaryBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!courseTitle.isNullOrBlank()) {
                        Text(courseTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "Submitted: ${submission.submittedAt.take(16).replace("T", " ")}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = if (isGraded) BackpackStatusGreen.copy(alpha = 0.14f)
                        else BackpackStatusOrange.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when {
                                isGraded -> "GRADED ${submission.score ?: 0}/$maxScore"
                                submission.pendingEssayGrading -> "ESSAY REVIEW"
                                else -> "NEEDS GRADING"
                            },
                            color = if (isGraded) BackpackStatusGreen else BackpackStatusOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        imageVector = if (expandedDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expandedDetails) "Collapse grading" else "Expand grading",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expandedDetails) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (submission.content.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Student Submission", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BackpackPrimaryBlue)
                            Spacer(modifier = Modifier.height(3.dp))
                            if (submission.content.startsWith("http", ignoreCase = true)) {
                                TextButton(
                                    onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(submission.content))) }
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(submission.content, fontSize = 12.sp)
                                }
                            } else {
                                Text(submission.content, fontSize = 12.sp)
                            }
                        }
                    }
                }

                submission.fileUrl?.ifBlank { null }?.let { fileUrl ->
                    OutlinedButton(
                        onClick = {
                            if (fileUrl.startsWith("http", ignoreCase = true)) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))) }
                            } else {
                                onOpenAttachment("${submission.userName ?: "Student"} Submission File", fileUrl)
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Inspect Student Attachment", fontSize = 12.sp)
                    }
                }

                if (hasStructuredQuestions) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Auto-graded: ${String.format(Locale.US, "%.1f", autoPoints)}/${submission.autoGradedMax ?: objectiveQuestions.sumOf { it.points }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BackpackPrimaryBlue
                            )
                            if (essayQuestions.isNotEmpty()) {
                                Text(
                                    text = "Essays: ${String.format(Locale.US, "%.1f", computedEssaySum)}/${essayQuestions.sumOf { it.points }}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BackpackStatusOrange
                                )
                            }
                        }
                    }

                    questions.forEachIndexed { idx, q ->
                        val ans = submission.answers[q.id].orEmpty()
                        val isEssay = q.format == "essay" || q.questionType == "essay"
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Q${idx + 1} (${q.points} pts): ${q.prompt}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isEssay) {
                                    Text(
                                        text = "Answer: ${ans.ifBlank { "(No answer submitted)" }}",
                                        fontSize = 12.sp
                                    )
                                    q.rubric?.ifBlank { null }?.let { rubric ->
                                        Text(
                                            text = "Rubric: $rubric",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedTextField(
                                        value = draftEssayScores[q.id].orEmpty(),
                                        onValueChange = { raw ->
                                            val updated = draftEssayScores.toMutableMap()
                                            updated[q.id] = raw
                                            draftEssayScores = updated
                                        },
                                        label = { Text("Essay Score (max ${q.points})") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    val optLabel = q.options.firstOrNull { it.id == ans }?.text ?: ans
                                    val isCorrect = if (q.questionType == "true_false" || q.options.isEmpty()) {
                                        q.correctBoolean?.toString()?.equals(ans, ignoreCase = true) == true
                                    } else {
                                        val correctOpt = q.options.firstOrNull { it.isCorrect }
                                        correctOpt != null && (ans == correctOpt.id || ans.equals(correctOpt.text, ignoreCase = true))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = if (isCorrect) "Correct" else "Incorrect",
                                            tint = if (isCorrect) BackpackStatusGreen else BackpackStatusRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Chosen: ${optLabel.ifBlank { "—" }}",
                                            fontSize = 12.sp,
                                            color = if (isCorrect) BackpackStatusGreen else BackpackStatusRed,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (essayQuestions.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val parsed = draftEssayScores.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                                onSaveEssayDraft(submission, parsed)
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save Essay Score Draft", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Final Overall Score + Feedback + Commit Grade
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = if (hasStructuredQuestions && essayQuestions.isNotEmpty()) {
                            String.format(Locale.US, "%.1f", computedTotal)
                        } else manualScoreText,
                        onValueChange = { manualScoreText = it },
                        readOnly = hasStructuredQuestions && essayQuestions.isNotEmpty(),
                        label = { Text("Score / $maxScore") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.42f)
                            .testTag("grade_score_input_${submission.id}")
                    )
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text("Feedback (optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.58f)
                            .testTag("grade_feedback_input_${submission.id}")
                    )
                }

                Button(
                    onClick = {
                        val finalEssayMap = if (essayQuestions.isNotEmpty()) {
                            draftEssayScores.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                        } else null
                        val finalScore = if (hasStructuredQuestions && essayQuestions.isNotEmpty()) {
                            computedTotal.coerceIn(0.0, maxScore.toDouble())
                        } else {
                            (manualScoreText.toDoubleOrNull() ?: autoPoints).coerceIn(0.0, maxScore.toDouble())
                        }
                        onGradeSubmission(submission, finalScore, feedbackText, finalEssayMap) {
                            expandedDetails = false
                        }
                    },
                    enabled = !isSubmitting && (
                        (hasStructuredQuestions && essayQuestions.isNotEmpty()) ||
                            manualScoreText.toDoubleOrNull() != null
                        ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("commit_grade_button_${submission.id}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                ) {
                    Icon(Icons.Default.Grading, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGraded) "Update Final Grade" else "Finalize & Save Grade",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

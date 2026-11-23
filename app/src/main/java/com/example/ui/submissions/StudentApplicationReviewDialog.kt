package com.example.ui.submissions

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.ui.theme.BackpackStatusRed
import java.util.Locale

@Composable
fun StudentApplicationReviewDialog(
    request: EnrollmentRequest,
    course: CourseItem?,
    organization: Organization?,
    vocationalGate: VocationalAdmissionGate?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: (reason: String?) -> Unit,
    onPreviewDocument: (title: String, url: String) -> Unit
) {
    val context = LocalContext.current
    var showRejectReasonBox by remember(request.id) { mutableStateOf(false) }
    var rejectionReasonText by remember(request.id) { mutableStateOf(request.rejectionReason.orEmpty()) }

    val openDoc: (String, String) -> Unit = { title, url ->
        if (url.startsWith("http", ignoreCase = true)) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        } else {
            onPreviewDocument(title, url)
        }
    }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .testTag("application_review_modal_${request.id}"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Applicant Dossier", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(request.userName.ifBlank { "Applicant" }, fontSize = 13.sp, color = BackpackPrimaryBlue, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Icon(Icons.Default.Close, contentDescription = "Close application dialog")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metadata Summary
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Course: ${course?.title ?: request.courseTitle}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            request.userEmail?.ifBlank { null }?.let {
                                Text("Email: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            request.sessionName?.ifBlank { null }?.let {
                                Text("Intake Session: $it", fontSize = 12.sp, color = BackpackPrimaryBlue, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "Status: ${request.status.uppercase()} • Applied ${request.appliedAt.take(10)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val paymentInfo = listOfNotNull(
                                request.paymentStatus?.let { "Payment: $it" },
                                request.paymentMethod?.let { "Plan: $it" },
                                course?.let { if (it.price > 0) "Tuition: ${it.currency} ${it.price}" else "Tuition: Free" }
                            ).joinToString(" • ")
                            if (paymentInfo.isNotBlank()) {
                                Text(paymentInfo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (request.isSponsored) {
                                Text(
                                    text = "Sponsored Student: ${request.sponsorName ?: "Sponsor"} (${request.sponsorEmail ?: "—"})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BackpackStatusGreen
                                )
                            }
                        }
                    }

                    // Vocational Sponsorship Admission Gate (if applicable)
                    if (vocationalGate != null) {
                        Surface(
                            color = if (vocationalGate.canAdmitMore) {
                                BackpackStatusGreen.copy(alpha = 0.1f)
                            } else {
                                BackpackStatusRed.copy(alpha = 0.12f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Vocational Donation Admission Gate",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (vocationalGate.canAdmitMore) BackpackStatusGreen else BackpackStatusRed
                                )
                                Text(
                                    text = "Donations: ${vocationalGate.currency} ${String.format(Locale.US, "%.2f", vocationalGate.totalDonations)} • Cost/Student: ${vocationalGate.currency} ${String.format(Locale.US, "%.2f", vocationalGate.tuitionCostPerStudent)}",
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Admitted: ${vocationalGate.currentlyAdmittedCount} / ${if (vocationalGate.maxAdmissibleStudents < 0) "∞" else vocationalGate.maxAdmissibleStudents} • Remaining Spots: ${if (vocationalGate.remainingSpots == Int.MAX_VALUE) "Unlimited" else vocationalGate.remainingSpots}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!vocationalGate.canAdmitMore) {
                                    Text(
                                        text = "Admission blocked until ${vocationalGate.currency} ${String.format(Locale.US, "%.2f", vocationalGate.nextSeatNeededAmount)} in additional sponsorships/donations is received.",
                                        fontSize = 11.sp,
                                        color = BackpackStatusRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Special Needs Accommodations
                    request.accommodations?.takeIf { it.enabled }?.let { acc ->
                        Surface(
                            color = BackpackPrimaryBlue.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Exam & Learning Accommodations", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BackpackPrimaryBlue)
                                if (acc.examTimeMultiplier > 1.0) {
                                    Text("Extra Time Multiplier: ${acc.examTimeMultiplier}x", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (acc.disabilityCategories.isNotEmpty()) {
                                    Text("Categories: ${acc.disabilityCategories.joinToString(", ")}", fontSize = 11.sp)
                                }
                                if (acc.requestedServices.isNotEmpty()) {
                                    Text("Services: ${acc.requestedServices.joinToString(", ")}", fontSize = 11.sp)
                                }
                                acc.emergencyHealthNotice?.ifBlank { null }?.let {
                                    Text("Health Notice: $it", fontSize = 11.sp, color = BackpackStatusOrange, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Applicant Statement
                    request.studentNotes?.ifBlank { null }?.let { notes ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Personal Statement / Notes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(notes, fontSize = 12.sp)
                            }
                        }
                    }

                    // Submitted Documents + Additional Documents
                    if (request.documents.isNotEmpty() || request.additionalDocuments.isNotEmpty()) {
                        Text(
                            text = "Submitted Documents (${request.documents.size + request.additionalDocuments.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        request.documents.forEach { (docName, url) ->
                            OutlinedButton(
                                onClick = { openDoc(docName, url) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(docName, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                        request.additionalDocuments.forEach { addDoc ->
                            OutlinedButton(
                                onClick = { openDoc(addDoc.name, addDoc.url) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(addDoc.name, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Reapplication History
                    if (request.reapplicationHistory.isNotEmpty()) {
                        Text(
                            text = "Prior Reapplication Attempts (${request.reapplicationHistory.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BackpackStatusOrange
                        )
                        request.reapplicationHistory.forEach { hist ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${hist.sessionName ?: "Session"} • ${hist.status.uppercase()} (${hist.appliedAt.take(10)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    hist.rejectionReason?.ifBlank { null }?.let { r ->
                                        Text("Reason: $r", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    course?.requirements?.ifBlank { null }?.let { reqs ->
                        Text("Course Admission Requirements: $reqs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (showRejectReasonBox) {
                        OutlinedTextField(
                            value = rejectionReasonText,
                            onValueChange = { rejectionReasonText = it },
                            label = { Text("Feedback / Rejection Reason (Optional)") },
                            placeholder = { Text("Provide constructive feedback for the applicant...") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rejection_reason_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (request.status == "pending") {
                    if (showRejectReasonBox) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showRejectReasonBox = false },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Back")
                            }
                            Button(
                                onClick = { onReject(rejectionReasonText.ifBlank { null }) },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("confirm_reject_application_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = BackpackStatusRed)
                            ) {
                                Text("Confirm Decline", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showRejectReasonBox = true },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("reject_application_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Decline", color = BackpackStatusRed, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onApprove,
                                enabled = !isSubmitting && (vocationalGate?.canAdmitMore != false),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .heightIn(min = 48.dp)
                                    .testTag("approve_application_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = BackpackStatusGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Approve Admission", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

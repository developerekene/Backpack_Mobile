package com.example.ui.courses

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import kotlin.math.roundToInt

@Composable
fun BackpackImagePreview(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (imageUrl.startsWith("data:image", ignoreCase = true) && imageUrl.contains(",")) {
        val bitmap = remember(imageUrl) {
            runCatching {
                val b64 = imageUrl.substringAfter(",")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

fun LazyListScope.studentOverviewTab(
    course: CourseItem,
    organizations: List<Organization>,
    materials: List<CourseMaterial>,
    onOpenDocument: (String, String?) -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val org = organizations.firstOrNull { it.id == course.orgId || it.ownerId == course.orgId }

    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("student_course_overview_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                course.coverImageUrl?.ifBlank { null }?.let { cover ->
                    BackpackImagePreview(
                        imageUrl = cover,
                        contentDescription = course.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Text("Course Overview (Read-Only)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (course.description.isNotBlank()) {
                    Text(
                        text = course.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (org != null) {
                    OverviewMetaRow("Institution", org.name)
                }
                course.instructorName?.ifBlank { null }?.let {
                    OverviewMetaRow("Instructor", it)
                }
                course.qualificationTitle?.ifBlank { null }?.let {
                    OverviewMetaRow("Qualification", it)
                }
                OverviewMetaRow("Pacing", course.pacing.replaceFirstChar { it.uppercase() })
                course.durationWeeks?.let { OverviewMetaRow("Duration", "$it weeks") }
                course.totalHours?.let { OverviewMetaRow("Study Hours", "$it hours") }
                course.language?.ifBlank { null }?.let { OverviewMetaRow("Language", it) }
                course.activeSessionName?.ifBlank { null }?.let { OverviewMetaRow("Active Session", it) }

                course.requirements?.ifBlank { null }?.let { reqs ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text("Requirements", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(reqs, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                course.applicationProcess?.ifBlank { null }?.let { proc ->
                    Text("Enrollment Procedure", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(proc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    item {
        Text(
            text = "Course Materials & Resources (${materials.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }

    if (materials.isEmpty()) {
        item {
            EmptyStateText("No additional course materials uploaded yet.")
        }
    } else {
        items(materials, key = { it.id }) { mat ->
            val context = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (mat.type == "video" && mat.url.isNotBlank()) {
                                onPlayVideo(mat.url)
                            } else if (mat.url.startsWith("http", ignoreCase = true)) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mat.url)))
                                }
                            } else {
                                onOpenDocument(mat.title, mat.url.ifBlank { null })
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (mat.type.lowercase()) {
                            "video" -> Icons.Default.PlayCircle
                            "pdf", "doc" -> Icons.Default.Description
                            else -> Icons.Default.Link
                        },
                        contentDescription = mat.type,
                        tint = BackpackPrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mat.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = mat.type.uppercase(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open material", tint = BackpackPrimaryBlue)
                }
            }
        }
    }
}

@Composable
private fun OverviewMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun LazyListScope.studentModulesTab(
    course: CourseItem,
    userProgress: UserProgress?,
    onPlayVideo: (String) -> Unit,
    onOpenDocument: (String, String?) -> Unit
) {
    val pct = course.calculateProgressPercent(userProgress)
    val completedModIds = userProgress?.completedModuleIds?.toSet() ?: emptySet()
    val completedItemIds = userProgress?.completedItemIds?.toSet() ?: emptySet()

    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("student_modules_progress_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Curriculum Progress (Read-Only)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = "${pct.roundToInt()}%",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = BackpackPrimaryBlue
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp),
                    color = BackpackPrimaryBlue,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Synced from your Backpack Web learning progress.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (course.modules.isEmpty()) {
        item {
            EmptyStateText("No curriculum modules published for this course yet.")
        }
    } else {
        items(course.modules, key = { it.id }) { module ->
            ModuleReadOnlyCard(
                module = module,
                isModuleCompleted = module.id in completedModIds,
                completedItemIds = completedItemIds,
                onPlayVideo = onPlayVideo,
                onOpenDocument = onOpenDocument
            )
        }
    }
}

@Composable
private fun ModuleReadOnlyCard(
    module: CourseModule,
    isModuleCompleted: Boolean,
    completedItemIds: Set<String>,
    onPlayVideo: (String) -> Unit,
    onOpenDocument: (String, String?) -> Unit
) {
    var expanded by remember(module.id) { mutableStateOf(true) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isModuleCompleted) Icons.Default.CheckCircle else Icons.Default.MenuBook,
                        contentDescription = if (isModuleCompleted) "Completed" else "Module",
                        tint = if (isModuleCompleted) BackpackStatusGreen else BackpackPrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = module.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${module.items.size} items" + if (isModuleCompleted) " • Completed" else "",
                            fontSize = 11.sp,
                            color = if (isModuleCompleted) BackpackStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                if (module.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = module.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (module.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = module.content, fontSize = 13.sp)
                }

                if (module.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        module.items.forEach { item ->
                            val isItemDone = item.id in completedItemIds
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when {
                                                isItemDone -> Icons.Default.CheckCircle
                                                item.type == "video" -> Icons.Default.PlayCircle
                                                item.type == "document" -> Icons.Default.PictureAsPdf
                                                item.type == "audio" -> Icons.Default.AudioFile
                                                item.type == "picture" -> Icons.Default.Image
                                                else -> Icons.Default.Article
                                            },
                                            contentDescription = item.type,
                                            tint = if (isItemDone) BackpackStatusGreen else BackpackPrimaryBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            val meta = listOfNotNull(
                                                item.type.uppercase(),
                                                item.duration?.ifBlank { null }
                                            ).joinToString(" • ")
                                            Text(meta, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    item.description?.ifBlank { null }?.let {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    item.content?.ifBlank { null }?.let {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(it, fontSize = 12.sp)
                                    }

                                    val videoLink = item.videoUrl?.ifBlank { null }
                                    val docLink = item.documentUrl?.ifBlank { null } ?: item.url?.ifBlank { null } ?: item.embedUrl?.ifBlank { null }
                                    val picLink = item.pictureUrl?.ifBlank { null }
                                    val audioLink = item.audioUrl?.ifBlank { null }

                                    if (picLink != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        BackpackImagePreview(
                                            imageUrl = picLink,
                                            contentDescription = item.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }

                                    if (videoLink != null || docLink != null || audioLink != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            videoLink?.let { url ->
                                                AssistChip(
                                                    onClick = { onPlayVideo(url) },
                                                    label = { Text("Watch Video", fontSize = 11.sp) },
                                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)) }
                                                )
                                            }
                                            docLink?.let { url ->
                                                AssistChip(
                                                    onClick = {
                                                        if (url.startsWith("http", ignoreCase = true)) {
                                                            runCatching {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                            }
                                                        } else {
                                                            onOpenDocument(item.title, url)
                                                        }
                                                    },
                                                    label = { Text("Open Resource", fontSize = 11.sp) },
                                                    leadingIcon = { Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)) }
                                                )
                                            }
                                            audioLink?.let { url ->
                                                AssistChip(
                                                    onClick = { onPlayVideo(url) },
                                                    label = { Text("Play Audio", fontSize = 11.sp) },
                                                    leadingIcon = { Icon(Icons.Default.VolumeUp, null, Modifier.size(16.dp)) }
                                                )
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
}

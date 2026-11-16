package com.example.ui.courses

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.data.model.*
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.example.util.FileUploadUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val REACTION_CHOICES = listOf("👍", "🎉", "💡", "🚀", "❤️")

fun LazyListScope.studentDiscussionTab(
    currentUserId: String,
    channels: List<DiscussionChannel>,
    activeChannelId: String?,
    messages: List<DiscussionMessage>,
    polls: List<DiscussionPoll>,
    presenceList: List<CoursePresence>,
    savedMessageIds: Set<String>,
    isSubmitting: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterTab: String,
    onFilterTabChange: (String) -> Unit,
    onSelectChannel: (String) -> Unit,
    onToggleSubscribe: (String) -> Unit,
    onSendMessage: (text: String, uri: Uri?, parentId: String?, onDone: () -> Unit) -> Unit,
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
    onVotePoll: (pollId: String, optionId: String) -> Unit,
    onToggleSave: (messageId: String) -> Unit,
    onPlayVideo: (String) -> Unit,
    onOpenDocument: (String, String?) -> Unit,
    isModerator: Boolean = false,
    onToggleVerifyMessage: ((String, Boolean) -> Unit)? = null,
    onTogglePinMessage: ((String, Boolean) -> Unit)? = null,
    onCreateChannel: ((name: String, description: String?, onDone: () -> Unit) -> Unit)? = null,
    onCreatePoll: ((question: String, options: List<String>, onDone: () -> Unit) -> Unit)? = null
) {
    val activeChannel = channels.firstOrNull { it.id == activeChannelId } ?: channels.firstOrNull()
    val resolvedChannelId = activeChannel?.id ?: activeChannelId.orEmpty()
    val isSubscribed = activeChannel?.subscriberIds?.contains(currentUserId) == true

    val now = System.currentTimeMillis()
    val onlineCount = presenceList
        .filter { now - it.lastActiveAt <= 2 * 60 * 1000 }
        .distinctBy { it.userId }
        .size

    val channelPolls = polls.filter { it.channelId == resolvedChannelId }
    val repliesByParent = messages
        .filter { !it.parentId.isNullOrBlank() }
        .groupBy { it.parentId!! }

    val visibleTopLevel = messages
        .filter {
            (it.channelId == resolvedChannelId || (it.channelId.isBlank() && channels.size <= 1)) &&
                it.parentId.isNullOrBlank()
        }
        .filter { msg ->
            if (searchQuery.isBlank()) true
            else {
                val q = searchQuery.trim().lowercase()
                msg.text.lowercase().contains(q) ||
                    msg.senderName.lowercase().contains(q) ||
                    (repliesByParent[msg.id]?.any { it.text.lowercase().contains(q) } == true)
            }
        }
        .filter { msg ->
            val verifiedThread = msg.verified || (repliesByParent[msg.id]?.any { it.verified } == true)
            when (filterTab) {
                "Unresolved" -> !verifiedThread
                "Verified" -> verifiedThread
                "Pinned" -> msg.pinned
                "Saved" -> msg.id in savedMessageIds
                else -> true
            }
        }
        .sortedWith(
            compareByDescending<DiscussionMessage> { it.pinned }.thenBy { it.createdAt }
        )

    // Channel Switcher + Online Telemetry + Subscribe Toggle
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Forum, contentDescription = null, tint = BackpackPrimaryBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Channels", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = BackpackStatusGreen.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$onlineCount online",
                                color = BackpackStatusGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (activeChannel != null) {
                            IconButton(
                                onClick = { onToggleSubscribe(activeChannel.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSubscribed) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                    contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
                                    tint = if (isSubscribed) BackpackPrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (channels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        channels.forEach { chan ->
                            FilterChip(
                                selected = chan.id == resolvedChannelId,
                                onClick = { onSelectChannel(chan.id) },
                                label = { Text("# ${chan.name}", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                activeChannel?.description?.ifBlank { null }?.let { desc ->
                    Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Search & Filters
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search messages or replies...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("All", "Unresolved", "Verified", "Pinned", "Saved").forEach { tab ->
                        FilterChip(
                            selected = filterTab == tab,
                            onClick = { onFilterTabChange(tab) },
                            label = { Text(tab, fontSize = 11.sp) }
                        )
                    }
                }

                if (isModerator && (onCreateChannel != null || onCreatePoll != null)) {
                    var showChannelDialog by remember { mutableStateOf(false) }
                    var showPollDialog by remember { mutableStateOf(false) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onCreateChannel != null) {
                            OutlinedButton(
                                onClick = { showChannelDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Channel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (onCreatePoll != null) {
                            OutlinedButton(
                                onClick = { showPollDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Poll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Poll", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (showChannelDialog && onCreateChannel != null) {
                        CreateDiscussionChannelDialog(
                            isSubmitting = isSubmitting,
                            onDismiss = { showChannelDialog = false },
                            onCreate = onCreateChannel
                        )
                    }
                    if (showPollDialog && onCreatePoll != null) {
                        CreateDiscussionPollDialog(
                            isSubmitting = isSubmitting,
                            onDismiss = { showPollDialog = false },
                            onCreate = onCreatePoll
                        )
                    }
                }
            }
        }
    }

    // Channel Polls
    if (channelPolls.isNotEmpty()) {
        items(channelPolls, key = { "poll_${it.id}" }) { poll ->
            DiscussionPollCard(
                poll = poll,
                currentUserId = currentUserId,
                onVote = { optId -> onVotePoll(poll.id, optId) }
            )
        }
    }

    // Message Composer (Top or bottom? Keeping accessible right above or below feed—let's put Main Composer above messages OR at bottom. Wait: putting composer right after channels/polls inside LazyColumn OR sticky works great; inside LazyColumn before messages or after messages makes scrolling easy without keyboard occlusion!)
    item {
        DiscussionMainComposer(
            isSubmitting = isSubmitting,
            onSend = { text, uri, onDone ->
                onSendMessage(text, uri, null, onDone)
            }
        )
    }

    if (visibleTopLevel.isEmpty()) {
        item {
            EmptyStateText("No messages in this channel yet. Start a conversation!")
        }
    } else {
        items(visibleTopLevel, key = { it.id }) { msg ->
            DiscussionMessageCard(
                msg = msg,
                replies = repliesByParent[msg.id]?.sortedBy { it.createdAt } ?: emptyList(),
                currentUserId = currentUserId,
                isSaved = msg.id in savedMessageIds,
                isSubmitting = isSubmitting,
                onToggleReaction = onToggleReaction,
                onToggleSave = { onToggleSave(msg.id) },
                onSendReply = { replyText, onDone ->
                    onSendMessage(replyText, null, msg.id, onDone)
                },
                onPlayVideo = onPlayVideo,
                onOpenDocument = onOpenDocument,
                isModerator = isModerator,
                onToggleVerifyMessage = onToggleVerifyMessage,
                onTogglePinMessage = onTogglePinMessage
            )
        }
    }
}

@Composable
private fun DiscussionMainComposer(
    isSubmitting: Boolean,
    onSend: (String, Uri?, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pickedUri = uri }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pickedUri = uri }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discussion_composer_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Share an update or ask a question...", fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discussion_message_input"),
                minLines = 2,
                maxLines = 4
            )

            pickedUri?.let { uri ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = FileUploadUtil.getFileName(context, uri),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Attach image or video", tint = BackpackPrimaryBlue)
                    }
                    IconButton(
                        onClick = {
                            documentPicker.launch(arrayOf("application/pdf", "application/*", "text/*"))
                        }
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach document", tint = BackpackPrimaryBlue)
                    }
                }

                Button(
                    onClick = {
                        onSend(text, pickedUri) {
                            text = ""
                            pickedUri = null
                        }
                    },
                    enabled = !isSubmitting && (text.isNotBlank() || pickedUri != null),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("discussion_send_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BackpackPrimaryBlue)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Post", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionPollCard(
    poll: DiscussionPoll,
    currentUserId: String,
    onVote: (String) -> Unit
) {
    val now = System.currentTimeMillis()
    val totalVotes = poll.options.sumOf { it.votes.size }
    val isClosed = poll.closesAt > 0L && now > poll.closesAt
    val hoursLeft = if (poll.closesAt > now) ((poll.closesAt - now) / (1000 * 60 * 60) + 1) else 0L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discussion_poll_${poll.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "COHORT CONSENSUS POLL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BackpackPrimaryBlue
                )
                Text(
                    text = if (isClosed) "Closed" else "Closes in ${hoursLeft}h",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(poll.question, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            poll.options.forEach { opt ->
                val pct = if (totalVotes > 0) (opt.votes.size * 100f / totalVotes).toInt() else 0
                val isMine = currentUserId in opt.votes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isMine) BackpackPrimaryBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isClosed) { onVote(opt.id) }
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = (if (isMine) "● " else "") + opt.label,
                                fontSize = 12.sp,
                                fontWeight = if (isMine) FontWeight.Bold else FontWeight.Medium,
                                color = if (isMine) BackpackPrimaryBlue else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$pct% (${opt.votes.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (pct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = if (isMine) BackpackPrimaryBlue else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Text(
                text = "$totalVotes vote${if (totalVotes == 1) "" else "s"}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscussionMessageCard(
    msg: DiscussionMessage,
    replies: List<DiscussionMessage>,
    currentUserId: String,
    isSaved: Boolean,
    isSubmitting: Boolean,
    onToggleReaction: (String, String) -> Unit,
    onToggleSave: () -> Unit,
    onSendReply: (String, () -> Unit) -> Unit,
    onPlayVideo: (String) -> Unit,
    onOpenDocument: (String, String?) -> Unit,
    isModerator: Boolean = false,
    onToggleVerifyMessage: ((String, Boolean) -> Unit)? = null,
    onTogglePinMessage: ((String, Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var showReplies by remember(msg.id) { mutableStateOf(false) }
    var showReactionBar by remember(msg.id) { mutableStateOf(false) }
    var replyDraft by remember(msg.id) { mutableStateOf("") }
    val timeStr = remember(msg.createdAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(msg.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discussion_msg_${msg.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (msg.verified) BackpackStatusGreen.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (msg.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = BackpackStatusOrange, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PINNED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BackpackStatusOrange)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (msg.senderId == currentUserId) "You" else msg.senderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (!msg.senderRole.isNullOrBlank() && msg.senderRole != "student") {
                        Surface(
                            color = BackpackPrimaryBlue.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = msg.senderRole.uppercase(),
                                color = BackpackPrimaryBlue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (msg.verified) {
                        Surface(
                            color = BackpackStatusGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Endorsed Solution",
                                color = BackpackStatusGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(timeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (msg.text.isNotBlank()) {
                Text(text = msg.text, fontSize = 13.sp)
            }

            if (msg.links.isNotEmpty()) {
                msg.links.forEach { link ->
                    TextButton(
                        onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(link.label.ifBlank { link.url }, fontSize = 12.sp)
                    }
                }
            }

            msg.fileUrl?.ifBlank { null }?.let { url ->
                when (msg.fileType) {
                    "image" -> BackpackImagePreview(
                        imageUrl = url,
                        contentDescription = "Message attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    "video" -> AssistChip(
                        onClick = { onPlayVideo(url) },
                        label = { Text("Watch Video Attachment", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.PlayCircle, null, Modifier.size(16.dp)) }
                    )
                    else -> AssistChip(
                        onClick = {
                            if (url.startsWith("http", ignoreCase = true)) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            } else {
                                onOpenDocument("Discussion Attachment", url)
                            }
                        },
                        label = { Text("View Attachment", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp)) }
                    )
                }
            }

            // Reaction Chips & Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    REACTION_CHOICES.forEach { emoji ->
                        val users = msg.reactions[emoji].orEmpty()
                        if (users.isNotEmpty()) {
                            val reacted = currentUserId in users
                            Surface(
                                color = if (reacted) BackpackPrimaryBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { onToggleReaction(msg.id, emoji) }
                            ) {
                                Text(
                                    text = "$emoji ${users.size}",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showReactionBar = !showReactionBar }
                    ) {
                        Text(
                            text = "+",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isModerator && (onToggleVerifyMessage != null || onTogglePinMessage != null)) {
                        IconButton(
                            onClick = { onToggleVerifyMessage?.invoke(msg.id, !msg.verified) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = if (msg.verified) "Remove verification" else "Verify solution",
                                tint = if (msg.verified) BackpackStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onTogglePinMessage?.invoke(msg.id, !msg.pinned) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = if (msg.pinned) "Unpin message" else "Pin message",
                                tint = if (msg.pinned) BackpackStatusOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(onClick = onToggleSave, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove bookmark" else "Save message",
                            tint = if (isSaved) BackpackStatusOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(onClick = { showReplies = !showReplies }) {
                        val totalReplies = maxOf(replies.size, msg.replyCount)
                        Text(
                            text = "$totalReplies ${if (totalReplies == 1) "reply" else "replies"}",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (showReactionBar) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    REACTION_CHOICES.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clickable {
                                    onToggleReaction(msg.id, emoji)
                                    showReactionBar = false
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }

            if (showReplies) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp)) {
                    replies.forEach { reply ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (reply.senderId == currentUserId) "You" else reply.senderName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = BackpackPrimaryBlue
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (reply.verified) {
                                            Text("Endorsed", fontSize = 10.sp, color = BackpackStatusGreen, fontWeight = FontWeight.Bold)
                                        }
                                        if (isModerator && onToggleVerifyMessage != null) {
                                            Text(
                                                text = if (reply.verified) "Unverify" else "Verify",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BackpackPrimaryBlue,
                                                modifier = Modifier
                                                    .clickable { onToggleVerifyMessage(reply.id, !reply.verified) }
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(reply.text, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = replyDraft,
                            onValueChange = { replyDraft = it },
                            placeholder = { Text("Write a reply...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (replyDraft.isNotBlank()) {
                                    onSendReply(replyDraft) { replyDraft = "" }
                                }
                            },
                            enabled = !isSubmitting && replyDraft.isNotBlank(),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply", tint = BackpackPrimaryBlue)
                        }
                    }
                }
            }
        }
    }
}

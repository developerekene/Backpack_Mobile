package com.example.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationItem
import com.example.data.model.UserDocument
import com.example.data.model.UserProfile
import com.example.ui.courses.BackpackImagePreview
import com.example.ui.courses.DocumentPreviewModal
import com.example.ui.theme.BackpackBluePrimary
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAlertsScreen(
    viewModel: ProfileAlertsViewModel,
    userProfile: UserProfile?,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val firebaseUser = Firebase.auth.currentUser
    val organizations by viewModel.organizations.collectAsState()

    var previewDoc by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val displayName = userProfile?.name?.ifBlank { null }
        ?: userProfile?.displayName?.ifBlank { null }
        ?: firebaseUser?.displayName?.ifBlank { null }
        ?: "Backpack User"

    val email = userProfile?.email?.ifBlank { null }
        ?: firebaseUser?.email
        ?: "Authenticated User"

    val roleLabel = userProfile?.userRole?.label ?: "Student"
    val uid = userProfile?.userId?.ifBlank { null } ?: firebaseUser?.uid ?: ""
    val orgName = organizations.firstOrNull {
        it.id == userProfile?.orgId || it.ownerId == userProfile?.orgId
    }?.name
    val associatedOrgText = orgName?.ifBlank { null }
        ?: userProfile?.institutionName?.ifBlank { null }
        ?: userProfile?.orgId?.ifBlank { null }
        ?: "Independent Learner"

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_profile_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BackpackBluePrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarUrl = userProfile?.logoUrl?.ifBlank { null }
                            if (avatarUrl != null) {
                                BackpackImagePreview(
                                    imageUrl = avatarUrl,
                                    contentDescription = displayName,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = displayName,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = email,
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            userProfile?.headline?.ifBlank { null }?.let { headline ->
                                Text(
                                    text = headline,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = roleLabel,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                                if (userProfile?.kycVerified == true) {
                                    Surface(
                                        color = BackpackStatusGreen.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "KYC VERIFIED",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Read-Only Notice + Web Edit Button
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Profile is read-only on mobile.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { openUrl("https://backpack-edu.com/profile") }) {
                        Text("Edit on Web", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Headline & Bio (only when populated in Firestore)
        if (!userProfile?.bio.isNullOrBlank() || !userProfile?.headline.isNullOrBlank()) {
            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        userProfile?.headline?.ifBlank { null }?.let {
                            Text(it, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        userProfile?.bio?.ifBlank { null }?.let {
                            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Account Details Card
        item {
            Text(
                text = "Account Details (Read-Only)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileDetailRow(Icons.Default.Person, "Full Name", displayName)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ProfileDetailRow(Icons.Default.Email, "Email", email)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ProfileDetailRow(Icons.Default.Badge, "Role", roleLabel)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ProfileDetailRow(Icons.Default.Business, "Organization", associatedOrgText)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ProfileDetailRow(
                        icon = Icons.Default.VerifiedUser,
                        label = "KYC Status",
                        value = if (userProfile?.kycVerified == true) "Verified" else "Unverified",
                        valueColor = if (userProfile?.kycVerified == true) BackpackStatusGreen else MaterialTheme.colorScheme.onSurface
                    )

                    userProfile?.createdAtIso?.ifBlank { null }?.let { created ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ProfileDetailRow(Icons.Default.CalendarToday, "Member Since", created.take(10))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileDetailRow(Icons.Default.Fingerprint, "Account UID", uid.take(16) + if (uid.length > 16) "..." else "")
                }
            }
        }

        // Organization & Institutional Details (Read-Only)
        val hasOrgFields = !userProfile?.orgType.isNullOrBlank() ||
            !userProfile?.accreditationStatus.isNullOrBlank() ||
            !userProfile?.accreditingBody.isNullOrBlank() ||
            !userProfile?.registrationId.isNullOrBlank() ||
            !userProfile?.location.isNullOrBlank() ||
            !userProfile?.address.isNullOrBlank() ||
            !userProfile?.baseCurrency.isNullOrBlank() ||
            !userProfile?.phone.isNullOrBlank() ||
            !userProfile?.website.isNullOrBlank() ||
            !userProfile?.motto.isNullOrBlank() ||
            !userProfile?.plan.isNullOrBlank()

        if (hasOrgFields) {
            item {
                Text(
                    text = "Institution & Compliance Details (Read-Only)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        userProfile?.orgType?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.Category, "Organization Type", it.replaceFirstChar { c -> c.uppercase() })
                        }
                        userProfile?.plan?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.WorkspacePremium, "Subscription Plan", it.uppercase())
                        }
                        userProfile?.accreditationStatus?.ifBlank { null }?.let {
                            ProfileDetailRow(
                                icon = Icons.Default.Verified,
                                label = "Accreditation",
                                value = it.replaceFirstChar { c -> c.uppercase() },
                                valueColor = if (userProfile.isAccredited) BackpackStatusGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        userProfile?.accreditingBody?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.AccountBalance, "Accrediting Body", it)
                        }
                        userProfile?.registrationId?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.Badge, "Registration ID", it)
                        }
                        userProfile?.location?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.LocationOn, "Location", it)
                        }
                        userProfile?.address?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.Home, "Address", it)
                        }
                        userProfile?.baseCurrency?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.Payments, "Base Currency", it.uppercase())
                        }
                        userProfile?.phone?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.Phone, "Phone", it)
                        }
                        userProfile?.website?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.Language, "Website", it)
                        }
                        userProfile?.motto?.ifBlank { null }?.let {
                            ProfileDetailRow(Icons.Default.FormatQuote, "Motto", it)
                        }
                    }
                }
            }
        }

        // CV / Resume
        userProfile?.cvUrl?.ifBlank { null }?.let { cv ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = BackpackBluePrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Curriculum Vitae (CV / Resume)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Synced from Backpack Web profile", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedButton(onClick = {
                            if (cv.startsWith("http", ignoreCase = true)) openUrl(cv)
                            else previewDoc = "Curriculum Vitae" to cv
                        }) {
                            Text("View", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Special Needs & Accommodations (Read-Only)
        userProfile?.accommodations?.let { acc ->
            item {
                Text(
                    text = "Accessibility & Exam Accommodations",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Accommodations Profile", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(
                                color = if (acc.enabled) BackpackStatusGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (acc.enabled) "ACTIVE" else "DISABLED",
                                    color = if (acc.enabled) BackpackStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (acc.examTimeMultiplier > 1.0) {
                            Text(
                                text = "Exam Time Multiplier: ${acc.examTimeMultiplier}x",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BackpackBluePrimary
                            )
                        }
                        if (acc.disabilityCategories.isNotEmpty()) {
                            Text("Categories: ${acc.disabilityCategories.joinToString(", ")}", fontSize = 12.sp)
                        }
                        acc.otherCategoryDescription?.ifBlank { null }?.let {
                            Text("Other Category: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (acc.preferredFormatting.isNotEmpty()) {
                            Text("Formatting: ${acc.preferredFormatting.joinToString(", ")}", fontSize = 12.sp)
                        }
                        if (acc.requestedServices.isNotEmpty()) {
                            Text("Requested Services: ${acc.requestedServices.joinToString(", ")}", fontSize = 12.sp)
                        }
                        acc.emergencyHealthNotice?.ifBlank { null }?.let {
                            Text("Emergency Notice: $it", fontSize = 12.sp, color = BackpackStatusOrange, fontWeight = FontWeight.SemiBold)
                        }
                        acc.medicalNotes?.ifBlank { null }?.let {
                            Text("Medical Notes: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Certificates & Uploaded Credentials
        val docs = userProfile?.userDocuments.orEmpty()
        if (docs.isNotEmpty()) {
            item {
                Text(
                    text = "Credentials & Documents (${docs.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(docs, key = { it.id.ifBlank { it.title + it.uploadedAt } }) { doc: UserDocument ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (doc.url.startsWith("http", ignoreCase = true)) openUrl(doc.url)
                            else previewDoc = doc.title to doc.url
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = BackpackBluePrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(doc.title.ifBlank { "Document" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                val meta = listOfNotNull(
                                    doc.category.uppercase(),
                                    doc.uploadedAt.take(10).ifBlank { null }
                                ).joinToString(" • ")
                                Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = "View document", tint = BackpackBluePrimary)
                    }
                }
            }
        }

        // Sign Out Button
        item {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out of Backpack", fontWeight = FontWeight.Bold)
            }
        }
    }

    previewDoc?.let { (title, url) ->
        DocumentPreviewModal(
            name = title,
            url = url,
            onDismiss = { previewDoc = null }
        )
    }
}

@Composable
fun ProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BackpackBluePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsModalSheet(
    viewModel: ProfileAlertsViewModel,
    onDismiss: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()

    val filteredNotifications = when (selectedFilter) {
        "Unread" -> notifications.filter { !it.read }
        "Reminders" -> notifications.filter {
            it.type.equals("Reminder", ignoreCase = true) ||
                it.title.contains("schedule", ignoreCase = true) ||
                it.title.contains("attend", ignoreCase = true) ||
                it.title.contains("reminder", ignoreCase = true)
        }
        "Grades" -> notifications.filter {
            it.type.equals("Grade", ignoreCase = true) ||
                it.title.contains("grade", ignoreCase = true) ||
                it.title.contains("assessment", ignoreCase = true) ||
                it.title.contains("submission", ignoreCase = true)
        }
        else -> notifications
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Notifications & Alerts",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "$unreadCount New",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = { viewModel.markAllAsRead() }) {
                    Icon(Icons.Default.MarkEmailRead, contentDescription = "Mark all read", tint = BackpackBluePrimary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Unread", "Reminders", "Grades").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter, fontSize = 12.sp) }
                    )
                }
            }

            if (filteredNotifications.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No notifications", fontWeight = FontWeight.Bold)
                        Text(
                            "You're all caught up with your Backpack alerts.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(filteredNotifications) { item ->
                        NotificationCardItem(
                            notification = item,
                            onMarkRead = { viewModel.markAsRead(item) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun NotificationCardItem(
    notification: NotificationItem,
    onMarkRead: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMarkRead() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.read) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = BackpackBluePrimary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = notification.type.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = notification.createdAt.take(10),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notification.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                text = notification.message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

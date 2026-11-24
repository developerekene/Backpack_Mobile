package com.example.ui.accessibility

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.ui.theme.BackpackPrimaryBlue
import com.example.ui.theme.BackpackStatusGreen
import com.example.ui.theme.BackpackStatusOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityMenuScreen(
    userProfile: UserProfile?,
    onNavigateBack: () -> Unit
) {
    val acc = userProfile?.accommodations

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility & Special Needs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("accessibility_back_btn")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BackpackPrimaryBlue)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Accessible,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Special Needs & Accommodations",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Configured for ${userProfile?.name ?: "Student"} • Synced with Backpack Profile",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (acc == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No Accommodations Configured", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "You have not requested any special exam accommodations or accessibility notes on your Backpack profile.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Accommodations Status", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Surface(
                                    color = if (acc.enabled) BackpackStatusGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (acc.enabled) "ACTIVE" else "DISABLED",
                                        color = if (acc.enabled) BackpackStatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            HorizontalDivider()

                            if (acc.examTimeMultiplier > 1.0) {
                                Text(
                                    text = "Exam Time Multiplier: ${acc.examTimeMultiplier}x",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BackpackPrimaryBlue
                                )
                            }
                            if (acc.disabilityCategories.isNotEmpty()) {
                                Text("Disability Categories: ${acc.disabilityCategories.joinToString(", ")}", fontSize = 13.sp)
                            }
                            acc.otherCategoryDescription?.ifBlank { null }?.let {
                                Text("Other Category: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (acc.preferredFormatting.isNotEmpty()) {
                                Text("Preferred Formatting: ${acc.preferredFormatting.joinToString(", ")}", fontSize = 13.sp)
                            }
                            if (acc.requestedServices.isNotEmpty()) {
                                Text("Requested Services: ${acc.requestedServices.joinToString(", ")}", fontSize = 13.sp)
                            }
                            acc.emergencyHealthNotice?.ifBlank { null }?.let {
                                Text("Emergency Notice: $it", fontSize = 13.sp, color = BackpackStatusOrange, fontWeight = FontWeight.Bold)
                            }
                            acc.medicalNotes?.ifBlank { null }?.let {
                                Text("Medical Notes: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

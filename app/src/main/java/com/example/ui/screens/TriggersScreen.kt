package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TriggerEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersScreen(
    triggers: List<TriggerEntity>,
    onAddTrigger: (TriggerEntity) -> Unit,
    onToggleTrigger: (TriggerEntity) -> Unit,
    onDeleteTrigger: (TriggerEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("TIME_REMINDER") }

    var titleInput by remember { mutableStateOf("") }
    var timeInput by remember { mutableStateOf("08:30") }
    var textInput by remember { mutableStateOf("") }
    var shortcutInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("triggers_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ট্রিগারস",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "রিমাইন্ডার, শর্টকাট ও ফোন ইভেন্ট অটোমেশন",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("add_trigger_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "নতুন ট্রিগার")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (triggers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "কোনো ট্রিগার যোগ করা হয়নি। উপরের '+' বাটনে ট্যাপ করে নতুন রিমাইন্ডার বা শর্টকাট যোগ করুন।",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)
            ) {
                items(triggers) { trigger ->
                    TriggerCardItem(
                        trigger = trigger,
                        onToggle = { onToggleTrigger(trigger) },
                        onDelete = { onDeleteTrigger(trigger) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("নতুন ট্রিগার যোগ করুন") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedType == "TIME_REMINDER",
                            onClick = { selectedType = "TIME_REMINDER" },
                            label = { Text("রিমাইন্ডার", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedType == "PHRASE_SHORTCUT",
                            onClick = { selectedType = "PHRASE_SHORTCUT" },
                            label = { Text("শর্টকাট", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedType == "PHONE_EVENT",
                            onClick = { selectedType = "PHONE_EVENT" },
                            label = { Text("ফোন ইভেন্ট", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("শিরোনাম") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedType == "TIME_REMINDER") {
                        OutlinedTextField(
                            value = timeInput,
                            onValueChange = { timeInput = it },
                            label = { Text("সময় (HH:mm)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (selectedType == "PHRASE_SHORTCUT") {
                        OutlinedTextField(
                            value = shortcutInput,
                            onValueChange = { shortcutInput = it },
                            label = { Text("শর্টকাট ফ্রেজ (যেমন: অফিসে যাচ্ছি)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("মাইরা যা বলবে বা করবে...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTrigger = TriggerEntity(
                            title = titleInput.ifBlank { "নতুন ট্রিগার" },
                            type = selectedType,
                            timeOfDay = if (selectedType == "TIME_REMINDER") timeInput else null,
                            shortcutPhrase = if (selectedType == "PHRASE_SHORTCUT") shortcutInput else null,
                            spokenText = textInput
                        )
                        onAddTrigger(newTrigger)
                        showAddDialog = false
                        titleInput = ""
                        textInput = ""
                    }
                ) {
                    Text("সংরক্ষণ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun TriggerCardItem(
    trigger: TriggerEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(trigger.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val detailStr = when (trigger.type) {
                    "TIME_REMINDER" -> "সময়: ${trigger.timeOfDay ?: ""} - ${trigger.spokenText}"
                    "PHRASE_SHORTCUT" -> "ফ্রেজ: '${trigger.shortcutPhrase ?: ""}'"
                    else -> "ইভেন্ট: ${trigger.phoneEventType ?: ""}"
                }
                Text(detailStr, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = trigger.enabled,
                    onCheckedChange = { onToggle() }
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "মুছুন", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

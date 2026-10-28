package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MairaLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val logs by MairaLogger.logsFlow.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, ERROR, WARN, INFO

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "ERROR" -> logs.filter { it.level == MairaLogger.Level.ERROR }
            "WARN" -> logs.filter { it.level == MairaLogger.Level.WARN }
            "INFO" -> logs.filter { it.level == MairaLogger.Level.INFO }
            else -> logs
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("logs_screen")
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("logs_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "সিস্টেম ও কানেকশন লগ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "সংযোগ, এরর ও অ্যাকশন রিয়েল-টাইম লগ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("সব") }
            )
            FilterChip(
                selected = selectedFilter == "ERROR",
                onClick = { selectedFilter = "ERROR" },
                label = { Text("ত্রুটি", color = MaterialTheme.colorScheme.error) }
            )
            FilterChip(
                selected = selectedFilter == "WARN",
                onClick = { selectedFilter = "WARN" },
                label = { Text("সতর্কতা") }
            )
            FilterChip(
                selected = selectedFilter == "INFO",
                onClick = { selectedFilter = "INFO" },
                label = { Text("তথ্য") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                val allText = logs.joinToString("\n") { "${it.timestamp} [${it.level}] [${it.category}]: ${it.message}" }
                clipboardManager.setText(AnnotatedString(allText))
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("কপি করুন")
            }

            TextButton(onClick = {
                val allText = logs.joinToString("\n") { "${it.timestamp} [${it.level}] [${it.category}]: ${it.message}" }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, allText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(intent, "লগ শেয়ার করুন"))
            }) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("শেয়ার করুন")
            }

            TextButton(onClick = { MairaLogger.clearLogs() }) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("মুছে ফেলুন", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logs List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(filteredLogs) { entry ->
                val levelColor = when (entry.level) {
                    MairaLogger.Level.ERROR -> MaterialTheme.colorScheme.error
                    MairaLogger.Level.WARN -> Color(0xFFFFA000)
                    MairaLogger.Level.INFO -> MaterialTheme.colorScheme.primary
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "[${entry.category}]",
                                fontSize = 11.sp,
                                color = levelColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entry.timestamp,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.message,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.GeminiImageClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SketchScreen(
    imageClient: GeminiImageClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMode by remember { mutableIntStateOf(0) } // 0: Draw, 1: Sketch Photo
    var promptInput by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedStyle by remember { mutableStateOf("পেন্সিল স্কেচ") }

    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedPhotoUri = uri
    }

    val styles = listOf("পেন্সিল স্কেচ", "কার্টুন", "অ্যানিমে", "জলরঙ")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("sketch_screen")
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("sketch_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ছবি ও স্কেচ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "ছবি আঁকো অথবা ছবি দিয়ে স্কেচ বানাও",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mode Selector Tabs
        TabRow(
            selectedTabIndex = selectedMode,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Tab(selected = selectedMode == 0, onClick = { selectedMode = 0 }) {
                Text("ছবি আঁকো", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedMode == 1, onClick = { selectedMode = 1 }) {
                Text("ছবিকে স্কেচ বানাও", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedMode == 0) {
            // Mode 0: Prompt to Image
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                label = { Text("ছবির বিবরণ দাও...") },
                placeholder = { Text("যেমন: পাহাড়ি নদীর ধারে সুন্দর সূর্যাস্ত") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    isLoading = true
                    statusMsg = null
                    scope.launch {
                        val res = imageClient.generateImageFromPrompt(promptInput)
                        isLoading = false
                        if (res.success) {
                            generatedBitmap = res.bitmap
                        } else {
                            statusMsg = res.errorMessage
                        }
                    }
                },
                enabled = promptInput.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.Brush, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ছবি তৈরি করো")
                }
            }
        } else {
            // Mode 1: Photo to Sketch
            Button(
                onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedPhotoUri != null) "ছবি নির্বাচিত হয়েছে ✓" else "গ্যালারি থেকে ছবি বেছে নাও")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("স্টাইল বেছে নাও:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                styles.forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style,
                        onClick = { selectedStyle = style },
                        label = { Text(style) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (selectedPhotoUri != null) {
                        isLoading = true
                        statusMsg = null
                        scope.launch {
                            val res = imageClient.convertPhotoToSketch(selectedPhotoUri!!, selectedStyle)
                            isLoading = false
                            if (res.success) {
                                generatedBitmap = res.bitmap
                            } else {
                                statusMsg = res.errorMessage
                            }
                        }
                    }
                },
                enabled = selectedPhotoUri != null && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("স্কেচ তৈরি করো")
                }
            }
        }

        if (statusMsg != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(statusMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Generated Image Result Display
        generatedBitmap?.let { bitmap ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "তৈরিকৃত ছবি",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    scope.launch {
                        val saved = imageClient.saveBitmapToPicturesFolder(bitmap)
                        statusMsg = if (saved) "ছবি সেভ হয়েছে (Pictures/Maira)" else "ছবি সেভ করতে ব্যর্থতা"
                    }
                }) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("সেভ করো")
                }

                Button(onClick = {
                    generatedBitmap = null
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("আবার বানাও")
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

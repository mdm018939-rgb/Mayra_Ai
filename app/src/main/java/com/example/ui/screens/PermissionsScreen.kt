package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val permissions = listOf(
        Pair("মাইক্রোফোন (RECORD_AUDIO)", Manifest.permission.RECORD_AUDIO),
        Pair("নোটিফিকেশন (POST_NOTIFICATIONS)", Manifest.permission.POST_NOTIFICATIONS),
        Pair("যোগাযোগ দেখা (READ_CONTACTS)", Manifest.permission.READ_CONTACTS),
        Pair("সরাসরি কল (CALL_PHONE)", Manifest.permission.CALL_PHONE)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("permissions_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("permissions_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "অনুমতিসমূহ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "অ্যাপের সকল সিস্টেম পারমিশন এবং স্ট্যাটাস",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Overlay / Floating Avatar permission item
            item {
                val isOverlayGranted = Settings.canDrawOverlays(context)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("অন্যান্য অ্যাপের উপরে প্রদর্শন (SYSTEM_ALERT_WINDOW)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (isOverlayGranted) "অনুমতি পাওয়া গেছে ✓" else "অনুমতি দেওয়া হয়নি ✕ (ভাসমান অ্যাভাটারের জন্য প্রয়োজন)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("অনুমতি দিন")
                        }
                    }
                }
            }

            items(permissions) { (name, perm) ->
                val isGranted = context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (isGranted) "অনুমতি পাওয়া গেছে ✓" else "অনুমতি দেওয়া হয়নি ✕",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("সেটিংস")
                        }
                    }
                }
            }
        }
    }
}

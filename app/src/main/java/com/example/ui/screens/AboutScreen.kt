package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("about_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("about_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "অ্যাপ সম্পর্কে",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "মাইরা - তোমার এআই সঙ্গিনী",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.maira_body),
                    contentDescription = "মাইরা",
                    modifier = Modifier.size(90.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("মাইরা (Maira)", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("ভার্সন ১.০.০", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("বৈশিষ্ট্যসমূহ:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Gemini Live দিয়ে রিয়েল-টাইম বাংলা ভয়েস কথোপকথন", fontSize = 13.sp)
                    Text("• Groq দিয়ে অতি দ্রুত বাংলা চ্যাট", fontSize = 13.sp)
                    Text("• ১০টি আকর্ষণীয় পারসোনালিটি ও স্বভাব", fontSize = 13.sp)
                    Text("• অ্যাপ খোলা, কল করা, ইউটিউবে গান চালানো", fontSize = 13.sp)
                    Text("• ওয়েবসাইট বিল্ডার ও স্কেচ জেনারেটর", fontSize = 13.sp)
                    Text("• মেসেঞ্জারিং অটোমেশন ও ভাসমান অ্যাভাটার", fontSize = 13.sp)
                    Text("• সম্পূর্ণ স্থানীয় এনক্রিপশনের মাধ্যমে API Key সুরক্ষা", fontSize = 13.sp)
                }
            }
        }
    }
}

package com.example.ui.screens.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ZevoraCyan
import com.example.ui.theme.ZevoraDarkBg

@Composable
fun LegalScreen(
    type: String, // "terms" or "privacy"
    onBack: () -> Unit
) {
    val isTerms = (type == "terms")
    val title = if (isTerms) "Terms of Service" else "Privacy Policy"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZevoraDarkBg)
            .statusBarsPadding()
            .testTag("legal_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 60.dp)
        ) {
            Text(
                text = "Last updated: October 2026",
                color = TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isTerms) {
                LegalSection(
                    title = "1. Acceptance of Terms",
                    content = "By creating an account, browsing content, uploading videos, or interacting with others on Rivo, you agree to comply with and be bound by these Terms of Service. If you do not agree, you must discontinue using Rivo immediately."
                )

                LegalSection(
                    title = "2. User Conduct & Acceptable Use",
                    content = "Rivo is dedicated to fostering an inspiring and safe community. You agree not to upload, transmit, or share content that:\n• Infringes third-party copyright, trademark, or intellectual property rights.\n• Promotes hate speech, harassment, defamation, or discrimination.\n• Depicts gratuitous violence, self-harm, or dangerous illegal activities.\n• Constitutes commercial spam, unauthorized phishing, or malware distribution."
                )

                LegalSection(
                    title = "3. Intellectual Property & Ownership",
                    content = "You retain ownership of the short videos, captions, and audio original works you create and publish on Rivo. By posting content, you grant Rivo a worldwide, non-exclusive, royalty-free license to stream, host, and display your public videos within the platform."
                )

                LegalSection(
                    title = "4. Content Moderation & Enforcement",
                    content = "Rivo employs real-time safety monitoring and user reporting queues. Administrators reserve the right to review, hide, or permanently remove videos that violate platform rules, and may issue warnings, temporary 7-day suspensions, or permanent account bans."
                )

                LegalSection(
                    title = "5. Account Termination",
                    content = "You may delete your account at any time via the Profile Settings menu. Rivo reserves the right to suspend or terminate accounts that repeatedly breach safety protocols."
                )
            } else {
                LegalSection(
                    title = "1. Information We Collect",
                    content = "When you register and interact on Rivo, we collect:\n• Account profile details: username, display name, email, biography, and profile picture.\n• Uploaded media: short video files, thumbnails, audio titles, and hashtags.\n• Interaction telemetry: videos watched, likes given, comments posted, and followed accounts."
                )

                LegalSection(
                    title = "2. How We Use Your Data",
                    content = "We use your personal data to:\n• Deliver an ultra-responsive, personalized video feed.\n• Power the social graph (followers, comments, mentions, and notifications).\n• Prevent spam, abuse, and protect community safety through our Moderation Team.\n• Ensure high-availability video streaming via multi-region cloud CDNs."
                )

                LegalSection(
                    title = "3. Data Security & Storage",
                    content = "All user credentials and sensitive account parameters are stored using industry-standard hashing algorithms. Videos are held in enterprise-grade cloud object storage with encrypted transit (HTTPS/TLS)."
                )

                LegalSection(
                    title = "4. Your Rights (GDPR & CCPA)",
                    content = "You have the right to access, export, or request the complete erasure of your personal data and uploaded content. You can submit an official Account Deletion Request directly from your Profile settings, which is reviewed and processed by our Data Protection Compliance Team."
                )
            }
        }
    }
}

@Composable
private fun LegalSection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = title,
            color = ZevoraCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = content,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.5.sp
        )
    }
}

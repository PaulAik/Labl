package com.paulaik.labl.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    currentKey: String,
    currentBackendUrl: String,
    onSave: (apiKey: String, backendUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var keyDraft by remember { mutableStateOf(currentKey) }
    var urlDraft by remember { mutableStateOf(currentBackendUrl) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111111)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // ── Claude API Key ──────────────────────────────────────────
            Text(
                text = "Claude API Key",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Get your key at console.anthropic.com. Stored locally on device only.",
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-ant-…", color = Color.White.copy(alpha = 0.35f)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                label = { Text("API Key", color = Color.White.copy(alpha = 0.60f)) }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(24.dp))

            // ── Training Backend URL ────────────────────────────────────
            Text(
                text = "Training Backend URL",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Go backend that stores training images in S3. Use 10.0.2.2:8080 for an emulator pointing to localhost.",
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("http://10.0.2.2:8080", color = Color.White.copy(alpha = 0.35f))
                },
                singleLine = true,
                label = { Text("Backend URL", color = Color.White.copy(alpha = 0.60f)) }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onSave(keyDraft, urlDraft) },
                enabled = keyDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save & Start Scanning")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

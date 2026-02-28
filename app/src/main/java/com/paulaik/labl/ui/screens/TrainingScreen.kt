package com.paulaik.labl.ui.screens

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paulaik.labl.data.model.ApplianceType
import com.paulaik.labl.data.model.ShotType
import com.paulaik.labl.data.model.TrainingSession
import com.paulaik.labl.ui.theme.ScannerTeal
import com.paulaik.labl.viewmodel.TrainingUiState
import com.paulaik.labl.viewmodel.TrainingViewModel
import java.util.concurrent.Executors

@Composable
fun TrainingScreen(
    backendUrl: String,
    onDismiss: () -> Unit,
    vm: TrainingViewModel = viewModel()
) {
    LaunchedEffect(backendUrl) { vm.setBackendUrl(backendUrl) }

    val state by vm.uiState.collectAsState()
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { captureExecutor.shutdown() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val s = state) {
            is TrainingUiState.AppliancePicker ->
                AppliancePickerPage(
                    onAppliance = vm::startSession,
                    onClose = {
                        vm.reset()
                        onDismiss()
                    }
                )

            is TrainingUiState.ReadyToCapture ->
                CapturePage(
                    session = s.session,
                    shot = s.shot,
                    onSetCapture = { vm.imageCapture = it },
                    onCapture = { vm.captureShot(captureExecutor) },
                    onClose = {
                        vm.reset()
                        onDismiss()
                    }
                )

            is TrainingUiState.Uploading ->
                UploadingPage(session = s.session, shot = s.shot)

            is TrainingUiState.Complete ->
                CompletePage(
                    session = s.session,
                    onDone = {
                        vm.reset()
                        onDismiss()
                    },
                    onTrainAnother = { vm.reset() }
                )

            is TrainingUiState.Error ->
                ErrorPage(
                    message = s.message,
                    onRetry = { vm.retryUpload() },
                    onAbort = {
                        vm.reset()
                        onDismiss()
                    }
                )
        }
    }
}

// ── Appliance Picker ───────────────────────────────────────────────────────

@Composable
private fun AppliancePickerPage(
    onAppliance: (ApplianceType) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = "Collect Training Data",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = "What kind of appliance?",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "You'll be guided through 7 photos from different angles and distances — the more variety the better for training.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(ApplianceType.entries) { type ->
                ApplianceTile(type = type, onClick = { onAppliance(type) })
            }
        }
    }
}

@Composable
private fun ApplianceTile(type: ApplianceType, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, ScannerTeal.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = type.emoji, fontSize = 34.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = type.displayName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Capture Page ───────────────────────────────────────────────────────────

@Composable
private fun CapturePage(
    session: TrainingSession,
    shot: ShotType,
    onSetCapture: (ImageCapture) -> Unit,
    onCapture: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Camera preview ────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    startTrainingCamera(ctx, previewView, lifecycleOwner, onSetCapture)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Instruction card at top ───────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Abort", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${session.applianceType.emoji}  ${session.applianceType.displayName}",
                        color = ScannerTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = shot.label,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${session.currentIndex + 1} / ${session.totalShots}",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedContent(
                targetState = shot.instruction,
                transitionSpec = {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(150))
                },
                label = "instruction"
            ) { instruction ->
                Text(
                    text = instruction,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.40f))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // ── Bottom controls ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.50f))
                .navigationBarsPadding()
                .padding(bottom = 24.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ShotProgressRow(session = session, currentShot = shot)

            // Capture shutter button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(3.dp, ScannerTeal, CircleShape)
                    .clickable(onClick = onCapture),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ScannerTeal)
                )
            }

            Text(
                text = "Tap to capture",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ShotProgressRow(session: TrainingSession, currentShot: ShotType) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        session.shots.forEach { shot ->
            val isDone = shot in session.completedShots
            val isCurrent = shot == currentShot
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDone -> ScannerTeal
                            isCurrent -> Color.White
                            else -> Color.White.copy(alpha = 0.25f)
                        }
                    )
            )
        }
    }
}

// ── Uploading Page ─────────────────────────────────────────────────────────

@Composable
private fun UploadingPage(session: TrainingSession, shot: ShotType) {
    val progress by animateFloatAsState(
        targetValue = session.completedCount.toFloat() / session.totalShots,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(72.dp),
            color = ScannerTeal,
            trackColor = Color.White.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round,
            strokeWidth = 5.dp
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Uploading…",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = shot.label,
            color = ScannerTeal,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${session.completedCount} of ${session.totalShots} complete",
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 13.sp
        )
    }
}

// ── Complete Page ──────────────────────────────────────────────────────────

@Composable
private fun CompletePage(
    session: TrainingSession,
    onDone: () -> Unit,
    onTrainAnother: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ScannerTeal.copy(alpha = 0.15f))
                .border(2.dp, ScannerTeal, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = ScannerTeal,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "All done!",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${session.totalShots} photos of your ${session.applianceType.displayName} uploaded successfully.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Session: ${session.id.take(8)}…",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ScannerTeal)
                .clickable(onClick = onDone)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Back to Scanner", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onTrainAnother) {
            Text("Train Another Appliance", color = ScannerTeal)
        }
    }
}

// ── Error Page ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorPage(message: String, onRetry: () -> Unit, onAbort: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚠️", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text("Upload failed", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message.take(120),
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(36.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ScannerTeal)
                .clickable(onClick = onRetry)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Retry Shot", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onAbort) {
            Text("Abort Session", color = Color.White.copy(alpha = 0.50f))
        }
    }
}

// ── CameraX setup for training ─────────────────────────────────────────────

private fun startTrainingCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onImageCapture: (ImageCapture) -> Unit
) {
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        val provider = future.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
        onImageCapture(imageCapture)
    }, ContextCompat.getMainExecutor(context))
}

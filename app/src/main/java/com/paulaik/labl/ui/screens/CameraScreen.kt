package com.paulaik.labl.ui.screens

import android.Manifest
import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.paulaik.labl.data.model.Symbol
import com.paulaik.labl.ui.components.SymbolDetailSheet
import com.paulaik.labl.ui.components.SymbolOverlay
import com.paulaik.labl.ui.theme.ScannerTeal
import com.paulaik.labl.viewmodel.CameraViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(vm: CameraViewModel = viewModel()) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        cameraPermission.status.isGranted -> LiveViewScreen(vm)
        cameraPermission.status.shouldShowRationale -> PermissionRationaleScreen {
            cameraPermission.launchPermissionRequest()
        }
        else -> {
            LaunchedEffect(Unit) { cameraPermission.launchPermissionRequest() }
            PermissionRationaleScreen { cameraPermission.launchPermissionRequest() }
        }
    }
}

// ── Live camera + AR overlay ───────────────────────────────────────────────

@Composable
private fun LiveViewScreen(vm: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysisResult by vm.analysisResult.collectAsState()
    val isAnalysing by vm.isAnalysing.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val apiKey by vm.apiKey.collectAsState()

    var selectedSymbol by remember { mutableStateOf<Symbol?>(null) }
    var showSettings by remember { mutableStateOf(apiKey.isBlank()) }

    // Build analyzer once; it references the latest apiKey via lambda
    val analyzer = remember { vm.buildAnalyzer() }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Camera preview ──────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    startCamera(ctx, previewView, lifecycleOwner, analyzer)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── AR symbol overlay ───────────────────────────────────────
        SymbolOverlay(
            symbols = analysisResult?.symbols ?: emptyList(),
            isScanning = isAnalysing,
            onSymbolTapped = { selectedSymbol = it },
            modifier = Modifier.fillMaxSize()
        )

        // ── Top bar ─────────────────────────────────────────────────
        TopBar(
            onSettingsTapped = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        )

        // ── Status bar at bottom ────────────────────────────────────
        StatusBar(
            isAnalysing = isAnalysing,
            symbolCount = analysisResult?.symbols?.size ?: 0,
            errorMessage = errorMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
        )
    }

    // ── Modals ───────────────────────────────────────────────────────
    selectedSymbol?.let { sym ->
        SymbolDetailSheet(symbol = sym, onDismiss = { selectedSymbol = null })
    }

    if (showSettings) {
        SettingsSheet(
            currentKey = apiKey,
            onSave = { key ->
                vm.saveApiKey(key)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }
}

// ── CameraX setup helper ───────────────────────────────────────────────────

private fun startCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    analyzer: ImageAnalysis.Analyzer
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer) }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis
        )
    }, ContextCompat.getMainExecutor(context))
}

// ── Top bar ────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onSettingsTapped: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "LABL",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            letterSpacing = 4.sp
        )
        IconButton(onClick = onSettingsTapped) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White.copy(alpha = 0.80f)
            )
        }
    }
}

// ── Status bar ─────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(
    isAnalysing: Boolean,
    symbolCount: Int,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = LinearEasing), RepeatMode.Reverse
        ),
        label = "dot"
    )

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(), exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            errorMessage != null -> Color.Red.copy(alpha = dotAlpha)
                            isAnalysing -> ScannerTeal.copy(alpha = dotAlpha)
                            else -> Color.Green.copy(alpha = 0.80f)
                        }
                    )
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = when {
                    errorMessage != null -> "Error: ${errorMessage.take(50)}"
                    isAnalysing -> "Analysing…"
                    symbolCount > 0 -> "$symbolCount symbol${if (symbolCount == 1) "" else "s"} detected · tap to learn more"
                    else -> "Point camera at appliance symbols"
                },
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Permission screen ──────────────────────────────────────────────────────

@Composable
private fun PermissionRationaleScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷", fontSize = 48.sp)
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
            Text(
                text = "Camera access needed",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text(
                text = "Labl uses your camera to identify\nappliance symbols in real time.",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
            androidx.compose.material3.Button(onClick = onRequest) {
                Text("Grant Camera Access")
            }
        }
    }
}

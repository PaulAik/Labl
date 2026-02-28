package com.paulaik.labl.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paulaik.labl.data.model.SymbolLabel
import com.paulaik.labl.ui.theme.ScannerTeal
import com.paulaik.labl.viewmodel.ArTestResult
import com.paulaik.labl.viewmodel.ValidationState
import com.paulaik.labl.viewmodel.ValidationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlin.math.abs

@Composable
fun ValidationScreen(
    backendUrl: String,
    onDismiss: () -> Unit,
    vm: ValidationViewModel = viewModel()
) {
    LaunchedEffect(backendUrl) {
        vm.setBackendUrl(backendUrl)
        vm.load()
    }

    val state by vm.state.collectAsState()
    val arTestResult by vm.arTestResult.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Header ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("✕  Close", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "VALIDATE LABELS",
                    color = ScannerTeal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.weight(1f))
                val remaining = (state as? ValidationState.Ready)?.remaining ?: 0
                if (remaining > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ScannerTeal.copy(alpha = 0.15f))
                            .border(1.dp, ScannerTeal.copy(alpha = 0.40f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$remaining",
                            color = ScannerTeal,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            // ── Body ──────────────────────────────────────────────────
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (val s = state) {
                    is ValidationState.Loading -> LoadingView()
                    is ValidationState.Empty -> EmptyView(onRefresh = vm::load)
                    is ValidationState.Error -> ErrorView(s.message, onRetry = vm::load)
                    is ValidationState.Ready -> SwipeCard(
                        label = s.current,
                        backendUrl = backendUrl,
                        onApprove = { vm.decide(true) },
                        onReject = { vm.decide(false) },
                        onTestInAr = { vm.testCropInAR(s.current) }
                    )
                }
            }

            // ── Action hint ───────────────────────────────────────────
            if (state is ValidationState.Ready) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        icon = Icons.Default.Close,
                        label = "Wrong",
                        tint = Color(0xFFFF4B4B),
                        onClick = { vm.decide(false) }
                    )
                    Text(
                        text = "or swipe",
                        color = Color.White.copy(alpha = 0.30f),
                        fontSize = 12.sp
                    )
                    ActionButton(
                        icon = Icons.Default.Check,
                        label = "Correct",
                        tint = ScannerTeal,
                        onClick = { vm.decide(true) }
                    )
                }
            }
        }

        // ── AR test result overlay ────────────────────────────────────
        when (val r = arTestResult) {
            is ArTestResult.Loading -> ArLoadingOverlay()
            is ArTestResult.Done -> ArResultOverlay(result = r, onDismiss = vm::dismissArTest)
            is ArTestResult.Error -> ArErrorOverlay(message = r.message, onDismiss = vm::dismissArTest)
            ArTestResult.Idle -> Unit
        }
    }
}

// ── Swipeable card ─────────────────────────────────────────────────────────

@Composable
private fun SwipeCard(
    label: SymbolLabel,
    backendUrl: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onTestInAr: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 120.dp.toPx() }

    val fraction = (offsetX.value / swipeThresholdPx).coerceIn(-1f, 1f)
    val approveAlpha = fraction.coerceAtLeast(0f)
    val rejectAlpha = (-fraction).coerceAtLeast(0f)
    val tiltDeg = fraction * 12f

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .rotate(tiltDeg)
            .pointerInput(label.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offsetX.value > swipeThresholdPx -> {
                                    offsetX.animateTo(2000f, tween(200, easing = FastOutSlowInEasing))
                                    onApprove()
                                    offsetX.snapTo(0f)
                                }
                                offsetX.value < -swipeThresholdPx -> {
                                    offsetX.animateTo(-2000f, tween(200, easing = FastOutSlowInEasing))
                                    onReject()
                                    offsetX.snapTo(0f)
                                }
                                else -> offsetX.animateTo(0f, tween(300))
                            }
                        }
                    },
                    onHorizontalDrag = { _, delta ->
                        scope.launch { offsetX.snapTo(offsetX.value + delta) }
                    }
                )
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161616))
            .border(
                width = 1.5.dp,
                color = when {
                    approveAlpha > 0.1f -> ScannerTeal.copy(alpha = approveAlpha)
                    rejectAlpha > 0.1f -> Color(0xFFFF4B4B).copy(alpha = rejectAlpha)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {

            // Crop thumbnail
            val cropKey = label.cropS3Key ?: label.s3Key
            val imageUrl = "$backendUrl/images?key=${URLEncoder.encode(cropKey, "UTF-8")}"
            RemoteImage(
                url = imageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0F0F))
            )

            Spacer(Modifier.height(16.dp))

            // Category + confidence
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(label.category)
                Spacer(Modifier.weight(1f))
                ConfidenceDot(label.confidence)
            }

            Spacer(Modifier.height(12.dp))

            // Symbol name
            Text(
                text = label.name.ifBlank { "Unknown symbol" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp
            )

            Spacer(Modifier.height(12.dp))

            // Description
            Text(
                text = label.description.ifBlank { "No description available." },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(16.dp))

            // Test in AR button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ScannerTeal.copy(alpha = 0.10f))
                    .border(1.dp, ScannerTeal.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onTestInAr)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Test in AR  →",
                    color = ScannerTeal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Meta
            Text(
                text = "${label.applianceType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}  •  ${label.sessionId.take(8)}",
                color = Color.White.copy(alpha = 0.30f),
                fontSize = 11.sp
            )
        }

        // Swipe overlays
        if (approveAlpha > 0.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ScannerTeal.copy(alpha = approveAlpha * 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("✓  CORRECT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        if (rejectAlpha > 0.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF4B4B).copy(alpha = rejectAlpha * 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("✗  WRONG", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ── Remote image loader ────────────────────────────────────────────────────

private val httpClient = OkHttpClient()

@Composable
private fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        bitmap = null
        failed = false
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).build()
                val bytes = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                }
                if (bytes != null) {
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    failed = true
                }
            } catch (_: Exception) {
                failed = true
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Symbol crop",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            failed -> Text(
                text = "No image",
                color = Color.White.copy(alpha = 0.25f),
                fontSize = 11.sp
            )
            else -> CircularProgressIndicator(
                color = ScannerTeal,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

// ── AR test overlays ───────────────────────────────────────────────────────

@Composable
private fun ArLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ScannerTeal, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Running AR scan…", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun ArResultOverlay(result: ArTestResult.Done, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.80f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF161616))
                .padding(24.dp)
                .navigationBarsPadding()
                .clickable { /* consume so taps inside don't dismiss */ }
                .verticalScroll(rememberScrollState())
        ) {
            // Handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "AR TEST RESULT",
                color = ScannerTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(16.dp))

            // Validated label row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.40f))
                )
                Spacer(Modifier.width(8.dp))
                Text("Validated label", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = result.labelName.ifBlank { "Unknown" },
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(20.dp))

            // Claude's response
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ScannerTeal)
                )
                Spacer(Modifier.width(8.dp))
                Text("Claude identified", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))

            if (result.arResult.symbols.isEmpty()) {
                Text(
                    text = "No symbols found in this crop.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            } else {
                result.arResult.symbols.forEachIndexed { i, symbol ->
                    if (i > 0) Spacer(Modifier.height(12.dp))

                    // Match indicator
                    val nameMatches = symbol.name.trim().equals(result.labelName.trim(), ignoreCase = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = symbol.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (nameMatches) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ScannerTeal.copy(alpha = 0.15f))
                                    .border(0.5.dp, ScannerTeal.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("MATCH", color = ScannerTeal, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF4B4B).copy(alpha = 0.12f))
                                    .border(0.5.dp, Color(0xFFFF4B4B).copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("DIFF", color = Color(0xFFFF4B4B), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = symbol.meaning,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = symbol.category.displayName  +  "  •  " + symbol.confidence.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = Color.White.copy(alpha = 0.30f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Dismiss", color = ScannerTeal, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ArErrorOverlay(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1A0F0F))
                .padding(24.dp)
                .navigationBarsPadding()
                .clickable { }
        ) {
            Text("⚠️  AR test failed", color = Color(0xFFFF4B4B), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message.take(200), color = Color.White.copy(alpha = 0.60f), fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = Color(0xFFFF4B4B))
            }
        }
    }
}

// ── Supporting composables ─────────────────────────────────────────────────

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f))
                .border(2.dp, tint.copy(alpha = 0.60f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = tint.copy(alpha = 0.80f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CategoryBadge(category: String) {
    val color = when (category.lowercase()) {
        "washing" -> Color(0xFF4D9EFF)
        "drying" -> Color(0xFFFF9A3C)
        "ironing" -> Color(0xFFFF4B4B)
        "bleaching" -> Color(0xFFFFD700)
        "dishwasher" -> Color(0xFF00BCD4)
        "oven" -> Color(0xFFAB47BC)
        else -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = category.uppercase().replace("_", " "),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun ConfidenceDot(confidence: String) {
    val color = when (confidence.lowercase()) {
        "high" -> ScannerTeal
        "medium" -> Color(0xFFFFD700)
        else -> Color(0xFFFF9A3C)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            text = confidence.lowercase().replaceFirstChar { it.uppercase() },
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LoadingView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = ScannerTeal, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Loading labels…", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp)
    }
}

@Composable
private fun EmptyView(onRefresh: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text("✅", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text("All done!", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "No pending labels to validate.\nCollect more training data or wait for the backend to process images.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRefresh) {
            Text("Refresh", color = ScannerTeal)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text("⚠️", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text("Could not load labels", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            message.take(120),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRetry) {
            Text("Retry", color = ScannerTeal)
        }
    }
}

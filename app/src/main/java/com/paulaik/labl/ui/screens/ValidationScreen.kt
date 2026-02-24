package com.paulaik.labl.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paulaik.labl.data.model.SymbolLabel
import com.paulaik.labl.ui.theme.ScannerTeal
import com.paulaik.labl.viewmodel.ValidationState
import com.paulaik.labl.viewmodel.ValidationViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
                // Remaining count badge
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
                        onApprove = { vm.decide(true) },
                        onReject = { vm.decide(false) }
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
    }
}

// ── Swipeable card ─────────────────────────────────────────────────────────

@Composable
private fun SwipeCard(
    label: SymbolLabel,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 120.dp.toPx() }

    // Derive visual cues from drag position
    val fraction = (offsetX.value / swipeThresholdPx).coerceIn(-1f, 1f)
    val approveAlpha = (fraction).coerceAtLeast(0f)
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
                                    offsetX.animateTo(
                                        2000f,
                                        tween(200, easing = FastOutSlowInEasing)
                                    )
                                    onApprove()
                                    offsetX.snapTo(0f)
                                }
                                offsetX.value < -swipeThresholdPx -> {
                                    offsetX.animateTo(
                                        -2000f,
                                        tween(200, easing = FastOutSlowInEasing)
                                    )
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

            // Category + confidence
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(label.category)
                Spacer(Modifier.weight(1f))
                ConfidenceDot(label.confidence)
            }

            Spacer(Modifier.height(16.dp))

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

            Spacer(Modifier.height(20.dp))

            // Meta
            Text(
                text = "${label.applianceType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}  •  ${label.sessionId.take(8)}",
                color = Color.White.copy(alpha = 0.30f),
                fontSize = 11.sp
            )
        }

        // Swipe hint overlays
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
        Text(
            "All done!",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
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

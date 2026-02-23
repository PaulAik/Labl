package com.paulaik.labl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulaik.labl.data.model.Symbol
import com.paulaik.labl.data.model.SymbolCategory
import com.paulaik.labl.data.model.SymbolPosition
import com.paulaik.labl.ui.theme.BleachingYellow
import com.paulaik.labl.ui.theme.CardBackground
import com.paulaik.labl.ui.theme.CardBorder
import com.paulaik.labl.ui.theme.DishwasherCyan
import com.paulaik.labl.ui.theme.DryingOrange
import com.paulaik.labl.ui.theme.IroningRed
import com.paulaik.labl.ui.theme.OtherGray
import com.paulaik.labl.ui.theme.OvenPurple
import com.paulaik.labl.ui.theme.ScannerTeal
import com.paulaik.labl.ui.theme.WashingBlue

/**
 * Full-screen transparent overlay drawn on top of the CameraX preview.
 * Renders:
 *  - A scanning-frame bracket animation
 *  - A moving scan line while [isScanning]
 *  - Floating symbol cards positioned according to [SymbolPosition]
 */
@Composable
fun SymbolOverlay(
    symbols: List<Symbol>,
    isScanning: Boolean,
    onSymbolTapped: (Symbol) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")

    // Scan line moves top → bottom, restarting each cycle
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    // Bracket pulse opacity
    val bracketAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bracketAlpha"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // ── Scanning frame + line (Canvas layer) ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawScannerFrame(w, h, bracketAlpha, if (isScanning) scanLineY else -1f)
                }
        )

        // ── Symbol cards ──────────────────────────────────────────────
        symbols.forEach { symbol ->
            val (xFrac, yFrac) = symbol.position.toFractions()
            val xDp = with(LocalDensity.current) { (w * xFrac).toDp() }
            val yDp = with(LocalDensity.current) { (h * yFrac).toDp() }

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .offset(x = xDp - 80.dp, y = yDp - 30.dp)
            ) {
                SymbolCard(symbol = symbol, onClick = { onSymbolTapped(symbol) })
            }
        }
    }
}

// ── Canvas drawing helpers ─────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScannerFrame(
    w: Float, h: Float, alpha: Float, scanLineFrac: Float
) {
    val frameW = w * 0.82f
    val frameH = h * 0.62f
    val left = (w - frameW) / 2f
    val top = (h - frameH) / 2f
    val right = left + frameW
    val bottom = top + frameH
    val corner = 28f
    val bracketLen = 52f
    val stroke = Stroke(width = 3f)
    val color = ScannerTeal.copy(alpha = alpha)

    // Four corner brackets (top-left, top-right, bottom-left, bottom-right)
    // Top-left
    drawLine(color, Offset(left, top + bracketLen), Offset(left, top + corner), stroke.width)
    drawLine(color, Offset(left, top), Offset(left + bracketLen, top), stroke.width)
    // Top-right
    drawLine(color, Offset(right, top + bracketLen), Offset(right, top + corner), stroke.width)
    drawLine(color, Offset(right, top), Offset(right - bracketLen, top), stroke.width)
    // Bottom-left
    drawLine(color, Offset(left, bottom - bracketLen), Offset(left, bottom - corner), stroke.width)
    drawLine(color, Offset(left, bottom), Offset(left + bracketLen, bottom), stroke.width)
    // Bottom-right
    drawLine(color, Offset(right, bottom - bracketLen), Offset(right, bottom - corner), stroke.width)
    drawLine(color, Offset(right, bottom), Offset(right - bracketLen, bottom), stroke.width)

    // Moving scan line (only while scanning)
    if (scanLineFrac in 0f..1f) {
        val lineY = top + (frameH * scanLineFrac)
        drawLine(
            color = ScannerTeal.copy(alpha = 0.55f),
            start = Offset(left + 4f, lineY),
            end = Offset(right - 4f, lineY),
            strokeWidth = 2f
        )
        // Soft glow below the line
        drawRect(
            color = ScannerTeal.copy(alpha = 0.08f),
            topLeft = Offset(left + 4f, lineY),
            size = Size(frameW - 8f, 22f)
        )
    }
}

// ── Symbol Card ────────────────────────────────────────────────────────────

@Composable
fun SymbolCard(symbol: Symbol, onClick: () -> Unit) {
    val accent = symbol.category.accentColor()

    Box(
        modifier = Modifier
            .widthIn(min = 140.dp, max = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            // Category badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.20f))
                    .border(0.5.dp, accent.copy(alpha = 0.60f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${symbol.category.emoji}  ${symbol.category.displayName.uppercase()}",
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = symbol.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = symbol.meaning,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun SymbolCategory.accentColor() = when (this) {
    SymbolCategory.WASHING -> WashingBlue
    SymbolCategory.DRYING -> DryingOrange
    SymbolCategory.IRONING -> IroningRed
    SymbolCategory.BLEACHING -> BleachingYellow
    SymbolCategory.DISHWASHER -> DishwasherCyan
    SymbolCategory.OVEN -> OvenPurple
    SymbolCategory.OTHER -> OtherGray
}

private fun SymbolPosition.toFractions(): Pair<Float, Float> = xFraction to yFraction

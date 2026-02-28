package com.paulaik.labl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulaik.labl.data.model.Symbol
import com.paulaik.labl.ui.theme.ScannerTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolDetailSheet(
    symbol: Symbol,
    onDismiss: () -> Unit,
    onValidate: ((approved: Boolean) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111111),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = symbol.category.emoji,
                    fontSize = 28.sp
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = symbol.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = symbol.category.displayName,
                        color = ScannerTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))

            DetailSection(label = "WHAT IT MEANS", body = symbol.meaning)
            Spacer(Modifier.height(12.dp))
            DetailSection(label = "WHAT TO DO", body = symbol.instructions)

            // ── Validation ───────────────────────────────────────────────
            if (onValidate != null) {
                var voted by remember(symbol.id) { mutableStateOf<Boolean?>(null) }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "WAS THIS CORRECT?",
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(10.dp))

                if (voted == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VoteButton(
                            label = "Wrong",
                            icon = Icons.Default.Close,
                            tint = Color(0xFFFF4B4B),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                voted = false
                                onValidate(false)
                            }
                        )
                        VoteButton(
                            label = "Correct",
                            icon = Icons.Default.Check,
                            tint = ScannerTeal,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                voted = true
                                onValidate(true)
                            }
                        )
                    }
                } else {
                    val isCorrect = voted == true
                    val tint = if (isCorrect) ScannerTeal else Color(0xFFFF4B4B)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(tint.copy(alpha = 0.10f))
                            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = if (isCorrect) "Marked as correct" else "Marked as wrong",
                                color = tint,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VoteButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DetailSection(label: String, body: String) {
    Text(
        text = label,
        color = ScannerTeal.copy(alpha = 0.80f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = body,
        color = Color.White.copy(alpha = 0.88f),
        fontSize = 15.sp,
        lineHeight = 22.sp
    )
}

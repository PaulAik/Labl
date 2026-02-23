package com.paulaik.labl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
fun SymbolDetailSheet(symbol: Symbol, onDismiss: () -> Unit) {
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

            Spacer(Modifier.height(24.dp))
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

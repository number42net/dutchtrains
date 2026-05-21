package net.number42.dutchtrains.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TrainTypeBadge(category: String, modifier: Modifier = Modifier) {
    val (bgColor, label) = when (category.uppercase()) {
        "IC"  -> MaterialTheme.colorScheme.primary to "IC"
        "ICD" -> Color(0xFF1A237E) to "ICD"
        "ICE" -> Color(0xFFB71C1C) to "ICE"
        "SPR" -> Color(0xFFE65100) to "SPR"
        "THA" -> Color(0xFF6A0DAD) to "THA"
        "EUR" -> Color(0xFF004494) to "EUR"
        else  -> MaterialTheme.colorScheme.secondary to category.ifBlank { "?" }
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

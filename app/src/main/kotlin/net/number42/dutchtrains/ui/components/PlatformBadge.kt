package net.number42.dutchtrains.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun PlatformBadge(
    actual: String?,
    planned: String?,
    modifier: Modifier = Modifier,
) {
    val changed = actual != null && planned != null && actual != planned
    val display = when {
        actual != null -> "Platform $actual"
        else -> "Platform —"
    }
    Text(
        text = display,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal,
        color = if (changed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

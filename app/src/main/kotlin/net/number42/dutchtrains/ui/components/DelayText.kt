package net.number42.dutchtrains.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))

@Composable
fun DepartureTimeText(planned: Instant, actual: Instant, modifier: Modifier = Modifier) {
    val delayMinutes = java.time.temporal.ChronoUnit.MINUTES.between(planned, actual)
    val time = timeFormatter.format(actual)

    if (delayMinutes > 0) {
        androidx.compose.foundation.layout.Row(modifier = modifier) {
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = " +${delayMinutes}m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }
    } else {
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier,
        )
    }
}

@Composable
fun ArrivalTimeText(planned: Instant, actual: Instant, modifier: Modifier = Modifier) {
    val delayMinutes = ChronoUnit.MINUTES.between(planned, actual)
    val time = timeFormatter.format(actual)

    if (delayMinutes > 0) {
        Row(modifier = modifier) {
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = " +${delayMinutes}m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }
    } else {
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

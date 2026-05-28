package net.number42.dutchtrains.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import net.number42.dutchtrains.domain.model.TrainMaterial
import net.number42.dutchtrains.domain.model.Trip

private val detailPlatformFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))

@Composable
fun TripDetailCard(
    trip: Trip,
    materialsByJourneyRef: Map<String, TrainMaterial>,
    isFollowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val publicLegs = trip.publicLegs
    if (publicLegs.isEmpty()) return
    val firstLeg = publicLegs.first()
    val lastLeg = publicLegs.last()

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = firstLeg.originName,
                style = MaterialTheme.typography.titleLarge,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .height(16.dp),
            )
            Text(
                text = lastLeg.destinationName,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DepartureTimeText(planned = firstLeg.plannedDeparture, actual = firstLeg.actualDeparture)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .height(16.dp),
            )
            ArrivalTimeText(planned = lastLeg.plannedArrival, actual = lastLeg.actualArrival)
            if (trip.transfers > 0) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${trip.transfers}× change",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        publicLegs.forEachIndexed { index, publicLeg ->
            val legMaterial = materialsByJourneyRef[publicLeg.journeyDetailRef]

            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            }

            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrainTypeBadge(category = publicLeg.category)
                val materialNumbers = legMaterial?.parts?.takeIf { it.isNotEmpty() }?.joinToString(" · ") { part ->
                    part.substringBefore(" (").ifBlank { part }
                }
                Text(
                    text = materialNumbers ?: publicLeg.name.removePrefix(publicLeg.category).trim().ifBlank { publicLeg.name },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (publicLeg.cancelled) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f),
                )
                if (publicLeg.cancelled) {
                    Text(
                        text = "CANCELLED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (isFollowed && index == 0) {
                    Text(
                        text = "●",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val depPlatform = publicLeg.actualDepartureTrack ?: publicLeg.plannedDepartureTrack ?: "-"
                val depPlatformArrivalTime = when {
                    publicLeg.originArrival != null -> detailPlatformFormatter.format(publicLeg.originArrival)
                    Instant.now().let { now ->
                        now.isAfter(publicLeg.plannedDeparture.minus(15, ChronoUnit.MINUTES)) &&
                        now.isBefore(publicLeg.plannedDeparture.plus(5, ChronoUnit.MINUTES))
                    } -> "now"
                    else -> "??:??"
                }
                Text(
                    text = buildAnnotatedString {
                        append("Platform ")
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            append(depPlatform)
                        }
                        append(" (train arrives here: ")
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            append(depPlatformArrivalTime)
                        }
                        append(")")
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val arrPlatform = publicLeg.actualArrivalTrack ?: publicLeg.plannedArrivalTrack ?: "-"
                val arrPlatformTime = detailPlatformFormatter.format(publicLeg.actualArrival)
                Text(
                    text = "Arrival platform $arrPlatform at $arrPlatformTime",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                legMaterial?.length?.let { length ->
                    Text(
                        text = buildAnnotatedString {
                            append("Length: ")
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                ),
                            ) {
                                append(length.toString())
                            }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                publicLeg.crowdForecast?.let { crowd ->
                    if (crowd != "UNKNOWN") {
                        if (legMaterial?.length != null) {
                            Text(text = "·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = when (crowd) {
                                "LOW" -> "Quiet"
                                "MEDIUM" -> "Moderate"
                                "HIGH" -> "Busy"
                                else -> crowd
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            color = when (crowd) {
                                "LOW" -> MaterialTheme.colorScheme.tertiary
                                "MEDIUM" -> MaterialTheme.colorScheme.secondary
                                "HIGH" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                }
            }

            if (!legMaterial?.imageUrl.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(legMaterial?.imageUrl)
                            .memoryCacheKey(legMaterial?.imageUrl)
                            .diskCacheKey(legMaterial?.imageUrl)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .crossfade(false)
                            .build(),
                        contentDescription = "Train image",
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.FillHeight,
                    )
                }
            }
        }
    }
}

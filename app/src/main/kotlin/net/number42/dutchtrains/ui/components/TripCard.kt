package net.number42.dutchtrains.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import net.number42.dutchtrains.domain.model.TrainMaterial
import net.number42.dutchtrains.domain.model.Trip

private val FollowedCardColor = Color(0xFFDCE4FF)
private val DefaultCardColor = Color.White
private val DangerColor = Color(0xFFB3261E)
private val MutedTextColor = Color(0xFF596273)
private val FollowDotColor = Color(0xFF4F5FCF)
private val QuietCrowdColor = Color(0xFF2E7D32)

@Composable
fun TripCard(
    trip: Trip,
    materialsByJourneyRef: Map<String, TrainMaterial>,
    isFollowed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firstLeg = trip.firstPublicLeg ?: return
    val finalLeg = trip.publicLegs.lastOrNull() ?: firstLeg
    val hasCancelledLeg = trip.publicLegs.any { it.cancelled }

    val cardColor by animateColorAsState(
        targetValue = if (isFollowed) FollowedCardColor else DefaultCardColor,
        label = "cardColor",
    )
    val strikeColor = DangerColor.copy(alpha = 0.55f)

    val legImageUrls = trip.publicLegs
        .mapNotNull { publicLeg ->
            publicLeg.journeyDetailRef
                .takeIf(String::isNotBlank)
                ?.let(materialsByJourneyRef::get)
                ?.imageUrl
                ?.takeIf(String::isNotBlank)
        }

    // Duration
    val durationMin = trip.actualDurationMinutes ?: trip.plannedDurationMinutes

    // Crowd from first leg that has known forecast
    val crowd = trip.publicLegs
        .firstNotNullOfOrNull { it.crowdForecast?.takeIf { forecast -> forecast != "UNKNOWN" } }

    val cardDescription = when {
        hasCancelledLeg -> "Cancelled trip"
        trip.transfers == 0 -> "Direct trip"
        else -> "${trip.transfers}× change trip"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription }
            .drawWithContent {
                drawContent()
                if (hasCancelledLeg) {
                    drawLine(
                        color = strikeColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 4.dp.toPx(),
                    )
                }
            }
            .clickable(enabled = !hasCancelledLeg, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            // ── Times row ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DepartureTimeText(
                    planned = firstLeg.plannedDeparture,
                    actual = firstLeg.actualDeparture,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MutedTextColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                ArrivalTimeText(
                    planned = finalLeg.plannedArrival,
                    actual = finalLeg.actualArrival,
                )

                Spacer(Modifier.weight(1f))

                // Direct / changes badge
                val badgeText = when {
                    hasCancelledLeg -> "Cancelled"
                    trip.transfers == 0 -> "Direct"
                    else -> "${trip.transfers}× change"
                }
                val badgeColor = when {
                    hasCancelledLeg -> Color(0xFFFDE2E1)
                    trip.transfers == 0 -> Color(0xFFBDC9FF)
                    else -> Color(0xFFD7DEFF)
                }
                val badgeTextColor = when {
                    hasCancelledLeg -> Color(0xFFB3261E)
                    trip.transfers == 0 -> Color(0xFF4F5FCF)
                    else -> Color(0xFF4E596F)
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                if (isFollowed) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "●",
                        color = FollowDotColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MutedTextColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            // ── Duration + crowd row ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, bottom = if (legImageUrls.isNotEmpty()) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MutedTextColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$durationMin min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedTextColor,
                )
                if (crowd != null) {
                    Text(
                        text = "  ·  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedTextColor,
                    )
                    Text(
                        text = when (crowd) {
                            "LOW" -> "Quiet"
                            "MEDIUM" -> "Moderate"
                            "HIGH" -> "Busy"
                            else -> crowd
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (crowd) {
                            "LOW" -> QuietCrowdColor
                            "HIGH" -> DangerColor
                            else -> MutedTextColor
                        },
                        fontWeight = if (crowd == "HIGH") FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            // ── Train images ──────────────────────────────────────────────────
            if (legImageUrls.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    legImageUrls.forEach { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCacheKey(url)
                                    .diskCacheKey(url)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = "Train image",
                                modifier = Modifier
                                    .height(27.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.FillHeight,
                            )
                        }
                    }
                }
            }
        }
    }
}

package net.number42.dutchtrains.ui.screen.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import net.number42.dutchtrains.domain.model.TrainMaterial
import net.number42.dutchtrains.domain.model.Trip
import net.number42.dutchtrains.ui.components.StationSearchField
import net.number42.dutchtrains.ui.components.TripCard
import net.number42.dutchtrains.ui.theme.AppCardBackground
import net.number42.dutchtrains.ui.theme.AppScreenBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToTripDetail: (String, Map<String, TrainMaterial>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val pullToRefreshState = rememberPullToRefreshState()

    // Location permission launcher — used for startup nearest-station auto-switch
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
            || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.onAutoSwitchByNearestStation()
        }
    }

    // Notification permission launcher — required on Android 13+ before starting follow service
    var pendingFollowTrip by remember { mutableStateOf<Trip?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start following regardless of grant result — foreground service still shows on deny
        pendingFollowTrip?.let { viewModel.onFollowTrain(it) }
        pendingFollowTrip = null
    }

    fun requestFollowTrain(trip: Trip) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingFollowTrip = trip
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onFollowTrain(trip)
        }
    }

    // Show location error in snackbar
    LaunchedEffect(uiState.locationError) {
        uiState.locationError?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            viewModel.onAutoSwitchByNearestStation()
        } else {
            locationLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
    }

    Scaffold(
        containerColor = AppScreenBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppScreenBackground),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Train,
                            contentDescription = null,
                            tint = Color(0xFF5E6FA8),
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Dutch",
                            color = Color(0xFF1F2638),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Trains",
                            color = Color(0xFF5B63E6),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                actions = {
                    uiState.refreshErrorMessage?.let {
                        Text(
                            text = "Refresh failed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF5B63E6),
                            trackColor = Color(0xFFD7DEFF),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refreshNow,
            state = pullToRefreshState,
            indicator = {
                if (uiState.isRefreshing || pullToRefreshState.distanceFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFF5B63E6),
                            trackColor = Color(0xFFD7DEFF),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                ) {
                    Spacer(Modifier.height(4.dp))

            // ── Station input card ────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = AppCardBackground,
                    shadowElevation = 2.dp,
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFFE8ECFF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color(0xFF5B63E6)),
                                )
                            }
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                repeat(4) {
                                    Box(
                                        modifier = Modifier
                                            .size(2.5.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Color(0xFFB8BED8)),
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFFE8ECFF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFF5B63E6),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            StationSearchField(
                                label = "From",
                                query = uiState.fromStation.query,
                                suggestions = uiState.fromStation.suggestions,
                                isLoading = uiState.fromStation.isLoading || uiState.isLocationLoading,
                                isError = uiState.fromStation.query.isNotBlank() && uiState.fromStation.selected == null && !uiState.fromStation.isLoading,
                                showDot = false,
                                labelColor = Color(0xFF67718B),
                                textColor = Color(0xFF2A3040),
                                onQueryChange = viewModel::onFromQueryChange,
                                onStationSelected = viewModel::onFromStationSelected,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 48.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            StationSearchField(
                                label = "To",
                                query = uiState.toStation.query,
                                suggestions = uiState.toStation.suggestions,
                                isLoading = uiState.toStation.isLoading,
                                isError = uiState.toStation.query.isNotBlank() && uiState.toStation.selected == null && !uiState.toStation.isLoading,
                                showDot = false,
                                labelColor = Color(0xFF67718B),
                                textColor = Color(0xFF2A3040),
                                onQueryChange = viewModel::onToQueryChange,
                                onStationSelected = viewModel::onToStationSelected,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                // Swap button centred on the divider, anchored to the right
                IconButton(
                    onClick = viewModel::onSwapStations,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = "Swap stations",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Filter chips ──────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = uiState.directOnly,
                    onClick = { viewModel.onDirectOnlyToggle(!uiState.directOnly) },
                    label = { Text("Direct", style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF5B63E6),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0x00000000),
                        labelColor = Color(0xFF4E596F),
                    ),
                )
                FilterChip(
                    selected = uiState.icOnly,
                    onClick = { viewModel.onIcOnlyToggle(!uiState.icOnly) },
                    label = { Text("IC only", style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF5B63E6),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0x00000000),
                        labelColor = Color(0xFF4E596F),
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

                // ── Trip list / states ────────────────────────────────────────────
                when (val state = uiState.tripsState) {
                is TripsState.Idle -> {
                    EmptyMessage("Select departure and destination stations.")
                }
                is TripsState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is TripsState.Error -> {
                    EmptyMessage("Could not load trains: ${state.message}")
                }
                is TripsState.NoApiKey -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Enter your NS API key in Settings to get started.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onNavigateToSettings) {
                            Text("Open Settings")
                        }
                    }
                }
                is TripsState.Success -> {
                    val displayed = state.trips.filter { trip ->
                        (!uiState.directOnly || trip.isDirect) &&
                        (!uiState.icOnly || trip.firstPublicLeg?.category == "IC")
                    }

                    if (displayed.isEmpty()) {
                        EmptyMessage("No trains found for this route.")
                    } else {
                        TripList(
                            trips = displayed,
                            materials = state.materials,
                            followedCtxRecon = uiState.followedCtxRecon,
                            onTripClick = { trip ->
                                val tripRefs = trip.publicLegs
                                    .mapNotNull { it.journeyDetailRef.takeIf(String::isNotBlank) }
                                    .toSet()
                                val tripMaterials = state.materials.filterKeys { it in tripRefs }
                                onNavigateToTripDetail(trip.ctxRecon, tripMaterials)
                            },
                        )
                    }
                }
                }

            }
        }
    }
}

}


@Composable
private fun TripList(
    trips: List<Trip>,
    materials: Map<String, net.number42.dutchtrains.domain.model.TrainMaterial>,
    followedCtxRecon: String?,
    onTripClick: (Trip) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(trips, key = { it.ctxRecon }) { trip ->
            TripCard(
                trip = trip,
                materialsByJourneyRef = materials,
                isFollowed = trip.ctxRecon == followedCtxRecon,
                onClick = { onTripClick(trip) },
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

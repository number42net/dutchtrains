package net.number42.dutchtrains.ui.screen.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.number42.dutchtrains.data.datastore.AppPreferences
import net.number42.dutchtrains.data.repository.StationRepository
import net.number42.dutchtrains.data.repository.TripRepository
import net.number42.dutchtrains.domain.model.Station
import net.number42.dutchtrains.domain.model.TrainMaterial
import net.number42.dutchtrains.domain.model.Trip
import net.number42.dutchtrains.service.TrainFollowService
import net.number42.dutchtrains.util.LocationHelper
import java.time.ZoneId
import java.time.Instant
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class StationSearchState(
    val query: String = "",
    val suggestions: List<Station> = emptyList(),
    val selected: Station? = null,
    val isLoading: Boolean = false,
)

sealed class TripsState {
    object Idle : TripsState()
    object Loading : TripsState()
    data class Success(
        val trips: List<Trip>,
        val materials: Map<String, TrainMaterial> = emptyMap(),
    ) : TripsState()
    data class Error(val message: String) : TripsState()
    object NoApiKey : TripsState()
}

data class HomeUiState(
    val fromStation: StationSearchState = StationSearchState(),
    val toStation: StationSearchState = StationSearchState(),
    val directOnly: Boolean = false,
    val icOnly: Boolean = false,
    val displayWindowMinutes: Int = 120,
    val tripsState: TripsState = TripsState.Idle,
    val followedCtxRecon: String? = null,
    val isRefreshing: Boolean = false,
    val refreshErrorMessage: String? = null,
    val isLocationLoading: Boolean = false,
    val locationError: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val stationRepository: StationRepository,
    private val appPreferences: AppPreferences,
    private val locationHelper: LocationHelper,
) : ViewModel() {
    private companion object {
        private const val CARD_DATA_WAIT_TIMEOUT_MS = 1_000L
        private const val MIN_REFRESH_INDICATOR_MS = 650L
        private val FOLLOW_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var fromSearchJob: Job? = null
    private var toSearchJob: Job? = null
    private var startupAutoSwitchAttempted: Boolean = false
    private var latestTrips: List<Trip> = emptyList()
    private var homeInitializedFromPrefs: Boolean = false
    private val _prefsLoaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            appPreferences.apiKeyFlow.collectLatest { apiKey ->
                if (apiKey.isBlank()) {
                    homeInitializedFromPrefs = false
                    _prefsLoaded.value = false
                    _uiState.update { it.copy(tripsState = TripsState.NoApiKey) }
                    return@collectLatest
                }

                if (!homeInitializedFromPrefs) {
                    // Restore persisted stations and toggles
                    val from = appPreferences.fromStationFlow.first()
                    val to = appPreferences.toStationFlow.first()
                    val directOnly = appPreferences.directOnlyFlow.first()
                    val icOnly = appPreferences.icOnlyFlow.first()
                    val displayWindowMinutes = appPreferences.displayWindowMinutesFlow.first()

                    _uiState.update { state ->
                        state.copy(
                            fromStation = state.fromStation.copy(selected = from, query = from?.name ?: ""),
                            toStation = state.toStation.copy(selected = to, query = to?.name ?: ""),
                            directOnly = directOnly,
                            icOnly = icOnly,
                            displayWindowMinutes = displayWindowMinutes,
                            tripsState = TripsState.Idle,
                        )
                    }

                    if (from != null && to != null) startAutoRefresh()
                    homeInitializedFromPrefs = true
                    _prefsLoaded.value = true
                }
            }
        }

        viewModelScope.launch {
            appPreferences.displayWindowMinutesFlow.collectLatest { minutes ->
                _uiState.update { it.copy(displayWindowMinutes = minutes) }

                val state = _uiState.value
                val success = state.tripsState as? TripsState.Success
                if (success != null) {
                    val visibleTrips = applyDisplayWindow(latestTrips, minutes)
                    val visibleRefs = visibleTrips
                        .flatMap { it.legs }
                        .mapNotNull { it.journeyDetailRef.takeIf(String::isNotBlank) }
                        .toSet()
                    val visibleMaterials = success.materials.filterKeys { it in visibleRefs }
                    _uiState.update {
                        it.copy(tripsState = TripsState.Success(trips = visibleTrips, materials = visibleMaterials))
                    }
                } else if (state.fromStation.selected != null && state.toStation.selected != null) {
                    loadTrips()
                }
            }
        }

        viewModelScope.launch {
            appPreferences.followedCtxReconFlow.collectLatest { followedCtxRecon ->
                _uiState.update { it.copy(followedCtxRecon = followedCtxRecon) }
            }
        }
    }

    // ── Station search ────────────────────────────────────────────────────────

    fun onFromQueryChange(query: String) {
        _uiState.update { it.copy(fromStation = it.fromStation.copy(query = query, selected = null)) }
        searchDebounced(query, isFrom = true)
    }

    fun onToQueryChange(query: String) {
        _uiState.update { it.copy(toStation = it.toStation.copy(query = query, selected = null)) }
        searchDebounced(query, isFrom = false)
    }

    private fun searchDebounced(query: String, isFrom: Boolean) {
        if (isFrom) fromSearchJob?.cancel() else toSearchJob?.cancel()
        val job = viewModelScope.launch {
            delay(300L)
            if (query.length < 2) {
                updateSearchSuggestions(isFrom, emptyList())
                return@launch
            }
            updateSearchLoading(isFrom, true)
            val results = stationRepository.searchStations(query)
            updateSearchSuggestions(isFrom, results)
            updateSearchLoading(isFrom, false)
        }
        if (isFrom) fromSearchJob = job else toSearchJob = job
    }

    private fun updateSearchSuggestions(isFrom: Boolean, suggestions: List<Station>) {
        _uiState.update { state ->
            if (isFrom) state.copy(fromStation = state.fromStation.copy(suggestions = suggestions))
            else state.copy(toStation = state.toStation.copy(suggestions = suggestions))
        }
    }

    private fun updateSearchLoading(isFrom: Boolean, loading: Boolean) {
        _uiState.update { state ->
            if (isFrom) state.copy(fromStation = state.fromStation.copy(isLoading = loading))
            else state.copy(toStation = state.toStation.copy(isLoading = loading))
        }
    }

    fun onFromStationSelected(station: Station) {
        fromSearchJob?.cancel()
        _uiState.update { it.copy(fromStation = StationSearchState(query = station.name, selected = station)) }
        viewModelScope.launch { appPreferences.saveFromStation(station) }
        checkAndStartRefresh()
    }

    fun onToStationSelected(station: Station) {
        toSearchJob?.cancel()
        _uiState.update { it.copy(toStation = StationSearchState(query = station.name, selected = station)) }
        viewModelScope.launch { appPreferences.saveToStation(station) }
        checkAndStartRefresh()
    }

    fun onSwapStations() {
        val state = _uiState.value
        val from = state.fromStation.selected
        val to = state.toStation.selected
        _uiState.update { it.copy(
            fromStation = StationSearchState(query = to?.name ?: "", selected = to),
            toStation   = StationSearchState(query = from?.name ?: "", selected = from),
        ) }
        viewModelScope.launch {
            appPreferences.saveFromStation(to)
            appPreferences.saveToStation(from)
        }
        checkAndStartRefresh()
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    fun onDirectOnlyToggle(enabled: Boolean) {
        _uiState.update { it.copy(directOnly = enabled) }
        viewModelScope.launch { appPreferences.saveDirectOnly(enabled) }
    }

    fun onIcOnlyToggle(enabled: Boolean) {
        _uiState.update { it.copy(icOnly = enabled) }
        viewModelScope.launch { appPreferences.saveIcOnly(enabled) }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private fun checkAndStartRefresh() {
        val state = _uiState.value
        if (state.fromStation.selected != null && state.toStation.selected != null) {
            startAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                loadTrips()
                delay(30_000L)
            }
        }
    }

    private suspend fun loadTrips() {
        val state = _uiState.value
        val from = state.fromStation.selected ?: return
        val to   = state.toStation.selected   ?: return

        // Only show full loading spinner on first load; subsequent refreshes are silent
        if (_uiState.value.tripsState is TripsState.Idle) {
            _uiState.update { it.copy(tripsState = TripsState.Loading) }
        }
        val refreshStartedAt = System.currentTimeMillis()
        _uiState.update { it.copy(isRefreshing = true) }

        tripRepository.findTrips(from, to, displayWindowMinutes = state.displayWindowMinutes)
            .onSuccess { trips ->
                latestTrips = trips
                val displayWindowMinutes = _uiState.value.displayWindowMinutes
                val visibleTrips = applyDisplayWindow(trips, displayWindowMinutes)

                val currentState = _uiState.value.tripsState
                val currentSuccess = currentState as? TripsState.Success
                val currentMaterials = currentSuccess?.materials.orEmpty()

                val currentRefs = visibleTrips
                    .flatMap { it.legs }
                    .mapNotNull { it.journeyDetailRef.takeIf(String::isNotBlank) }
                    .toSet()
                val retainedMaterials = currentMaterials.filterKeys { it in currentRefs }

                val tripsChanged = currentSuccess?.trips != visibleTrips
                val missingRefs = currentRefs - retainedMaterials.keys

                val readyMaterials = if (missingRefs.isNotEmpty()) {
                    val fetchedWithinTimeout = withTimeoutOrNull(CARD_DATA_WAIT_TIMEOUT_MS) {
                        fetchMaterials(missingRefs, from.code)
                    }.orEmpty()
                    retainedMaterials + fetchedWithinTimeout
                } else {
                    retainedMaterials
                }

                val materialsChanged = currentMaterials != readyMaterials

                if (currentSuccess == null || tripsChanged || materialsChanged) {
                    _uiState.update { it.copy(tripsState = TripsState.Success(trips = visibleTrips, materials = readyMaterials)) }
                }

                val remainingRefs = missingRefs - readyMaterials.keys
                if (remainingRefs.isNotEmpty()) {
                    loadMaterialsAsync(remainingRefs, from.code)
                }
                val refreshElapsed = System.currentTimeMillis() - refreshStartedAt
                if (refreshElapsed < MIN_REFRESH_INDICATOR_MS) {
                    delay(MIN_REFRESH_INDICATOR_MS - refreshElapsed)
                }
                _uiState.update { it.copy(isRefreshing = false, refreshErrorMessage = null) }
            }
            .onFailure { e ->
                if (e is CancellationException) {
                    return@onFailure
                }

                // Keep old results visible on refresh failure; only show error on first load
                if (_uiState.value.tripsState !is TripsState.Success) {
                    _uiState.update { it.copy(tripsState = TripsState.Error(e.message ?: "Unknown error")) }
                }
                val refreshElapsed = System.currentTimeMillis() - refreshStartedAt
                if (refreshElapsed < MIN_REFRESH_INDICATOR_MS) {
                    delay(MIN_REFRESH_INDICATOR_MS - refreshElapsed)
                }
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        refreshErrorMessage = e.message ?: "Refresh failed",
                    )
                }
            }
    }

    private fun applyDisplayWindow(trips: List<Trip>, displayWindowMinutes: Int): List<Trip> {
        val now = Instant.now()
        val maxSecondsAhead = displayWindowMinutes * 60L
        return trips.filter { trip ->
            val departure = trip.firstPublicLeg?.plannedDeparture ?: return@filter false
            val secondsUntilDeparture = Duration.between(now, departure).seconds
            secondsUntilDeparture in 0..maxSecondsAhead
        }
    }

    private suspend fun fetchMaterials(refs: Set<String>, stationCode: String): Map<String, TrainMaterial> = coroutineScope {
        refs.map { ref ->
            async {
                val material = tripRepository.getMaterial(ref, stationCode)
                if (material != null) ref to material else null
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private fun loadMaterialsAsync(missingRefs: Set<String>, stationCode: String) {
        viewModelScope.launch {
            val fetched = fetchMaterials(missingRefs, stationCode)
            android.util.Log.d("HomeVM", "loadMaterials done: ${fetched.size} fetched, keys=${fetched.keys}")
            _uiState.update { state ->
                val tripsState = state.tripsState
                if (tripsState is TripsState.Success) {
                    val merged = tripsState.materials + fetched
                    if (merged != tripsState.materials) {
                        android.util.Log.d("HomeVM", "state updated with materials")
                        state.copy(tripsState = tripsState.copy(materials = merged))
                    } else {
                        state
                    }
                } else {
                    android.util.Log.w("HomeVM", "state is not Success (${tripsState::class.simpleName}), skipping materials update")
                    state
                }
            }
        }
    }

    // ── GPS ───────────────────────────────────────────────────────────────────

    fun onDetectLocationForFrom() = detectLocation(isFrom = true)
    fun onDetectLocationForTo()   = detectLocation(isFrom = false)

    fun onAutoSwitchByNearestStation() {
        if (startupAutoSwitchAttempted) return
        startupAutoSwitchAttempted = true
        viewModelScope.launch {
            _prefsLoaded.first { it }
            locationHelper.getCurrentLocation()
                .onSuccess { location ->
                    val nearest = stationRepository.getNearestStation(location.latitude, location.longitude) ?: return@onSuccess
                    val state = _uiState.value
                    val from = state.fromStation.selected
                    val to = state.toStation.selected

                    when {
                        from == null -> {
                            onFromStationSelected(nearest)
                        }
                        to != null && to.code == nearest.code && from.code != nearest.code -> {
                            onSwapStations()
                        }
                    }
                }
                .onFailure {
                    // Startup auto-detect is best-effort only
                }
        }
    }

    private fun detectLocation(isFrom: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocationLoading = true, locationError = null) }
            locationHelper.getCurrentLocation()
                .onSuccess { location ->
                    stationRepository.getNearestStation(location.latitude, location.longitude)
                        ?.let { station ->
                            if (isFrom) onFromStationSelected(station) else onToStationSelected(station)
                        }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(locationError = e.message) }
                }
            _uiState.update { it.copy(isLocationLoading = false) }
        }
    }

    // ── Train follow ──────────────────────────────────────────────────────────

    fun onFollowTrain(trip: Trip) {
        val leg = trip.firstPublicLeg ?: return
        val lastLeg = trip.publicLegs.lastOrNull() ?: leg

        val tripTitle = "${leg.originName} -> ${lastLeg.destinationName}"
        val tripTimes = "${FOLLOW_TIME_FORMATTER.format(leg.actualDeparture)} -> ${FOLLOW_TIME_FORMATTER.format(lastLeg.actualArrival)}"

        val intent = Intent(context, TrainFollowService::class.java).apply {
            action = TrainFollowService.ACTION_START
            putExtra(TrainFollowService.EXTRA_CTX_RECON, trip.ctxRecon)
            putExtra(TrainFollowService.EXTRA_TRAIN_NAME, leg.name)
            putExtra(TrainFollowService.EXTRA_PLANNED_DEP, leg.plannedDeparture.toString())
            putExtra(TrainFollowService.EXTRA_FROM_STATION_CODE, _uiState.value.fromStation.selected?.code)
            putExtra(TrainFollowService.EXTRA_TRIP_TITLE, tripTitle)
            putExtra(TrainFollowService.EXTRA_TRIP_TIMES, tripTimes)
        }
        context.startForegroundService(intent)
        viewModelScope.launch { appPreferences.saveFollowedCtxRecon(trip.ctxRecon) }
    }

    fun onStopFollowing() {
        context.startService(Intent(context, TrainFollowService::class.java).apply {
            action = TrainFollowService.ACTION_STOP
        })
        viewModelScope.launch { appPreferences.saveFollowedCtxRecon(null) }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }

    fun refreshNow() {
        val state = _uiState.value
        if (state.fromStation.selected == null || state.toStation.selected == null || state.isRefreshing) return
        startAutoRefresh()
    }
}

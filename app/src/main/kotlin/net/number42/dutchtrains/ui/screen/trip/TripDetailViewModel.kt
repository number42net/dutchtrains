package net.number42.dutchtrains.ui.screen.trip

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.number42.dutchtrains.data.datastore.AppPreferences
import net.number42.dutchtrains.data.repository.TripRepository
import net.number42.dutchtrains.domain.model.TrainMaterial
import net.number42.dutchtrains.domain.model.Trip
import net.number42.dutchtrains.service.TrainFollowService
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TripDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val trip: Trip? = null,
    val materials: Map<String, TrainMaterial> = emptyMap(),
    val isFollowing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private companion object {
        private val FOLLOW_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
        private const val REFRESH_INTERVAL_MS = 30_000L
        private const val MIN_REFRESH_INDICATOR_MS = 650L
    }

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    private val ctxRecon: String = Uri.decode(savedStateHandle["ctxRecon"] ?: "")
    private val initialMaterials: Map<String, TrainMaterial> =
        (savedStateHandle.get<HashMap<String, TrainMaterial>>("tripMaterials") ?: hashMapOf()).toMap()

    init {
        viewModelScope.launch {
            appPreferences.followedCtxReconFlow.collect { followedCtx ->
                _uiState.update { state -> state.copy(isFollowing = followedCtx == ctxRecon) }
            }
        }
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                loadTrip()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refreshNow() {
        if (_uiState.value.isRefreshing) return
        startAutoRefresh()
    }

    private suspend fun loadTrip() {
        if (ctxRecon.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Missing trip id") }
            return
        }

        val hasTrip = _uiState.value.trip != null
        val refreshStartedAt = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isLoading = !hasTrip,
                isRefreshing = hasTrip,
                error = if (hasTrip) it.error else null,
            )
        }

        val fromCode = appPreferences.fromStationFlow.first()?.code
        tripRepository.getUpdatedTrip(ctxRecon, fromCode)
            .onSuccess { trip ->
                val materials = _uiState.value.materials.toMutableMap().apply { putAll(initialMaterials) }
                trip.publicLegs.forEach { leg ->
                    if (leg.journeyDetailRef.isNotBlank() && !materials.containsKey(leg.journeyDetailRef)) {
                        tripRepository.getMaterial(leg.journeyDetailRef, fromCode ?: "")
                            ?.let { materials[leg.journeyDetailRef] = it }
                    }
                }

                val refreshElapsed = System.currentTimeMillis() - refreshStartedAt
                if (hasTrip && refreshElapsed < MIN_REFRESH_INDICATOR_MS) {
                    delay(MIN_REFRESH_INDICATOR_MS - refreshElapsed)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        trip = trip,
                        materials = materials,
                        error = null,
                    )
                }
            }
            .onFailure { e ->
                val refreshElapsed = System.currentTimeMillis() - refreshStartedAt
                if (hasTrip && refreshElapsed < MIN_REFRESH_INDICATOR_MS) {
                    delay(MIN_REFRESH_INDICATOR_MS - refreshElapsed)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (hasTrip) it.error else (e.message ?: "Failed to load trip"),
                    )
                }
            }
    }

    fun onStartFollowing() {
        val trip = _uiState.value.trip ?: return
        val leg = trip.firstPublicLeg ?: return
        val lastLeg = trip.publicLegs.lastOrNull() ?: leg
        viewModelScope.launch {
            val fromCode = appPreferences.fromStationFlow.first()?.code
            val tripTitle = "${leg.originName} -> ${lastLeg.destinationName}"
            val tripTimes = "${FOLLOW_TIME_FORMATTER.format(leg.actualDeparture)} -> ${FOLLOW_TIME_FORMATTER.format(lastLeg.actualArrival)}"
            val intent = Intent(context, TrainFollowService::class.java).apply {
                action = TrainFollowService.ACTION_START
                putExtra(TrainFollowService.EXTRA_CTX_RECON, trip.ctxRecon)
                putExtra(TrainFollowService.EXTRA_TRAIN_NAME, leg.name)
                putExtra(TrainFollowService.EXTRA_PLANNED_DEP, leg.plannedDeparture.toString())
                putExtra(TrainFollowService.EXTRA_ACTUAL_DEP, leg.actualDeparture.toString())
                putExtra(TrainFollowService.EXTRA_FROM_STATION_CODE, fromCode)
                putExtra(TrainFollowService.EXTRA_TRIP_TITLE, tripTitle)
                putExtra(TrainFollowService.EXTRA_TRIP_TIMES, tripTimes)
            }
            context.startForegroundService(intent)
            appPreferences.saveFollowedCtxRecon(trip.ctxRecon)
        }
    }

    fun onStopFollowing() {
        context.startService(Intent(context, TrainFollowService::class.java).apply {
            action = TrainFollowService.ACTION_STOP
        })
        viewModelScope.launch {
            appPreferences.saveFollowedCtxRecon(null)
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }
}

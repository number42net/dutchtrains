package net.number42.dutchtrains.ui.screen.home

import android.content.Context
import android.location.Location
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.number42.dutchtrains.data.datastore.AppPreferences
import net.number42.dutchtrains.data.repository.StationRepository
import net.number42.dutchtrains.data.repository.TripRepository
import net.number42.dutchtrains.domain.model.Station
import net.number42.dutchtrains.util.LocationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelAutoSwitchTest {

    private val testDispatcher = StandardTestDispatcher()

    private val context: Context = mockk(relaxed = true)
    private val tripRepository: TripRepository = mockk(relaxed = true)
    private val stationRepository: StationRepository = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val locationHelper: LocationHelper = mockk()

    private val amsterdam = Station(code = "ASD", uicCode = "8400058", name = "Amsterdam Centraal", lat = 52.378, lng = 4.9)
    private val almere    = Station(code = "ALM", uicCode = "8400059", name = "Almere Centrum",    lat = 52.375, lng = 5.217)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { tripRepository.findTrips(any(), any(), any()) } coAnswers { awaitCancellation() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun location(lat: Double, lng: Double): Location = mockk<Location>().also {
        every { it.latitude } returns lat
        every { it.longitude } returns lng
    }

    private fun makeViewModel(from: Station? = null, to: Station? = null): HomeViewModel {
        every { appPreferences.apiKeyFlow } returns flowOf("test-key")
        every { appPreferences.fromStationFlow } returns flowOf(from)
        every { appPreferences.toStationFlow } returns flowOf(to)
        every { appPreferences.directOnlyFlow } returns flowOf(false)
        every { appPreferences.icOnlyFlow } returns flowOf(false)
        every { appPreferences.displayWindowMinutesFlow } returns flowOf(120)
        every { appPreferences.followedCtxReconFlow } returns flowOf(null)
        return HomeViewModel(context, tripRepository, stationRepository, appPreferences, locationHelper)
    }

    @Test
    fun `sets from station when none is selected`() = runTest {
        val vm = makeViewModel(from = null, to = null)
        runCurrent()

        coEvery { locationHelper.getCurrentLocation() } returns Result.success(location(amsterdam.lat, amsterdam.lng))
        coEvery { stationRepository.getNearestStation(amsterdam.lat, amsterdam.lng) } returns amsterdam

        vm.onAutoSwitchByNearestStation()
        runCurrent()

        assertEquals(amsterdam, vm.uiState.value.fromStation.selected)
        assertNull(vm.uiState.value.toStation.selected)
    }

    @Test
    fun `swaps stations when to matches nearest and from differs`() = runTest {
        val vm = makeViewModel(from = amsterdam, to = almere)
        runCurrent()

        coEvery { locationHelper.getCurrentLocation() } returns Result.success(location(almere.lat, almere.lng))
        coEvery { stationRepository.getNearestStation(almere.lat, almere.lng) } returns almere

        vm.onAutoSwitchByNearestStation()
        runCurrent()

        assertEquals(almere, vm.uiState.value.fromStation.selected)
        assertEquals(amsterdam, vm.uiState.value.toStation.selected)
    }

    @Test
    fun `does not change stations when from already matches nearest`() = runTest {
        val vm = makeViewModel(from = amsterdam, to = almere)
        runCurrent()

        coEvery { locationHelper.getCurrentLocation() } returns Result.success(location(amsterdam.lat, amsterdam.lng))
        coEvery { stationRepository.getNearestStation(amsterdam.lat, amsterdam.lng) } returns amsterdam

        vm.onAutoSwitchByNearestStation()
        runCurrent()

        assertEquals(amsterdam, vm.uiState.value.fromStation.selected)
        assertEquals(almere, vm.uiState.value.toStation.selected)
    }

    @Test
    fun `does not change stations when location fails`() = runTest {
        val vm = makeViewModel(from = amsterdam, to = almere)
        runCurrent()

        coEvery { locationHelper.getCurrentLocation() } returns Result.failure(Exception("GPS unavailable"))

        vm.onAutoSwitchByNearestStation()
        runCurrent()

        assertEquals(amsterdam, vm.uiState.value.fromStation.selected)
        assertEquals(almere, vm.uiState.value.toStation.selected)
    }

    @Test
    fun `only runs once across multiple calls`() = runTest {
        val vm = makeViewModel(from = null, to = null)
        runCurrent()

        coEvery { locationHelper.getCurrentLocation() } returns Result.success(location(amsterdam.lat, amsterdam.lng))
        coEvery { stationRepository.getNearestStation(any(), any()) } returns amsterdam

        vm.onAutoSwitchByNearestStation()
        runCurrent()

        assertEquals(amsterdam, vm.uiState.value.fromStation.selected)

        // Second call should be a no-op; nearest station changes to almere
        coEvery { stationRepository.getNearestStation(any(), any()) } returns almere
        vm.onAutoSwitchByNearestStation()
        runCurrent()

        assertEquals(amsterdam, vm.uiState.value.fromStation.selected)
    }
}

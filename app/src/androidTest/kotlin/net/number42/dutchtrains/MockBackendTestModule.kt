package net.number42.dutchtrains

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.serialization.json.Json
import net.number42.dutchtrains.data.api.NsStationsService
import net.number42.dutchtrains.data.api.NsTripsService
import net.number42.dutchtrains.data.api.VirtualTrainService
import net.number42.dutchtrains.di.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import retrofit2.Retrofit
import javax.inject.Singleton
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class],
)
object MockBackendTestModule {

    private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    val mockDepartureTime: ZonedDateTime =
        ZonedDateTime.now(ZoneId.of("Europe/Amsterdam")).plusMinutes(30).withSecond(0).withNano(0)
    private val mockArrivalTime: ZonedDateTime = mockDepartureTime.plusMinutes(20)
    private val depStr: String = mockDepartureTime.format(TIMESTAMP_FMT)
    private val arrStr: String = mockArrivalTime.format(TIMESTAMP_FMT)
    @Volatile var updatedSingleTripTrack: String? = null


    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideMockWebServer(): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: ""
                return when {
                    path.endsWith("/nsapp-stations/v3") -> MockResponse().setResponseCode(200).setBody(stationsResponse())
                    path.endsWith("/nsapp-stations/v3/nearest") -> MockResponse().setResponseCode(200).setBody(stationsResponse())
                    path.endsWith("/reisinformatie-api/api/v3/trips") -> MockResponse().setResponseCode(200).setBody(tripsResponse())
                    path.endsWith("/reisinformatie-api/api/v3/trips/trip") -> MockResponse().setResponseCode(200).setBody(singleTripResponse())
                    path.endsWith("/reisinformatie-api/api/v2/arrivals") -> MockResponse().setResponseCode(200).setBody(arrivalsResponse())
                    path.startsWith("/virtual-train-api/api/v1/trein/") -> MockResponse().setResponseCode(200).setBody(trainDetailResponse())
                    else -> MockResponse().setResponseCode(404).setBody("{}")
                }
            }
        }
        val latch = CountDownLatch(1)
        var startupError: Throwable? = null
        Thread {
            try {
                start()
            } catch (t: Throwable) {
                startupError = t
            } finally {
                latch.countDown()
            }
        }.start()
        latch.await(10, TimeUnit.SECONDS)
        startupError?.let { throw it }
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, server: MockWebServer): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1:${server.port}/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideNsTripsService(retrofit: Retrofit): NsTripsService =
        retrofit.create(NsTripsService::class.java)

    @Provides
    @Singleton
    fun provideNsStationsService(retrofit: Retrofit): NsStationsService =
        retrofit.create(NsStationsService::class.java)

    @Provides
    @Singleton
    fun provideVirtualTrainService(retrofit: Retrofit): VirtualTrainService =
        retrofit.create(VirtualTrainService::class.java)

    private fun stationsResponse(): String =
        """
        {"payload":[{"id":{"code":"ASD","uicCode":"8400058"},"names":{"long":"Amsterdam Centraal"},"location":{"lat":52.378,"lng":4.9},"country":"NL"},{"id":{"code":"ALM","uicCode":"8400059"},"names":{"long":"Almere Centrum"},"location":{"lat":52.375,"lng":5.217},"country":"NL"}]}
        """.trimIndent()

    private fun tripsResponse(): String =
        """{"trips":[{"ctxRecon":"mock-ctx-1","transfers":0,"status":"NORMAL","plannedDurationInMinutes":20,"actualDurationInMinutes":20,"legs":[{"travelType":"PUBLIC_TRANSIT","name":"IC 1234","journeyDetailRef":"1234","cancelled":false,"product":{"categoryCode":"IC","shortCategoryName":"IC"},"origin":{"name":"Amsterdam Centraal","plannedDateTime":"$depStr","actualDateTime":"$depStr","plannedTrack":"4","actualTrack":"4"},"destination":{"name":"Almere Centrum","plannedDateTime":"$arrStr","actualDateTime":"$arrStr","plannedTrack":"2","actualTrack":"2"}}]}]}"""

    private fun singleTripResponse(): String {
        val track = updatedSingleTripTrack ?: "4"
        return """{"ctxRecon":"mock-ctx-1","transfers":0,"status":"NORMAL","plannedDurationInMinutes":20,"actualDurationInMinutes":20,"legs":[{"travelType":"PUBLIC_TRANSIT","name":"IC 1234","journeyDetailRef":"1234","cancelled":false,"product":{"categoryCode":"IC","shortCategoryName":"IC"},"origin":{"name":"Amsterdam Centraal","plannedDateTime":"$depStr","actualDateTime":"$depStr","plannedTrack":"$track","actualTrack":"$track"},"destination":{"name":"Almere Centrum","plannedDateTime":"$arrStr","actualDateTime":"$arrStr","plannedTrack":"2","actualTrack":"2"}}]}"""
    }

    private fun arrivalsResponse(): String =
        """
        {"payload":{"arrivals":[]}}
        """.trimIndent()

    private fun trainDetailResponse(): String =
        """
        {"lengte":6,"materieeldelen":[{"materieelnummer":1234,"type":"ICM 3","afbeeldingsSpecs":{"imageUrl":"https://example.test/icm3.png"}}]}
        """.trimIndent()
}

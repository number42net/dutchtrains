package net.number42.dutchtrains

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import androidx.test.platform.app.InstrumentationRegistry
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
import okio.Buffer
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
                val baseUrl = "http://127.0.0.1:${request.requestUrl?.port ?: 80}"
                return when {
                    path.endsWith("/nsapp-stations/v3") -> MockResponse().setResponseCode(200).setBody(stationsResponse())
                    path.endsWith("/nsapp-stations/v3/nearest") -> MockResponse().setResponseCode(200).setBody(stationsResponse())
                    path.endsWith("/reisinformatie-api/api/v3/trips") -> MockResponse().setResponseCode(200).setBody(tripsResponse())
                    path.endsWith("/reisinformatie-api/api/v3/trips/trip") -> {
                        val ctxRecon = request.requestUrl?.queryParameter("ctxRecon")
                        MockResponse().setResponseCode(200).setBody(singleTripResponse(ctxRecon))
                    }
                    path.endsWith("/reisinformatie-api/api/v2/arrivals") -> MockResponse().setResponseCode(200).setBody(arrivalsResponse())
                    path.startsWith("/virtual-train-api/api/v1/trein/") -> {
                        val trainId = path.substringAfterLast('/').substringBefore('?')
                        MockResponse().setResponseCode(200).setBody(trainDetailResponse(trainId, baseUrl))
                    }
                    path == "/assets/train-ic.jpg" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "image/jpeg")
                        .setBody(Buffer().write(loadAssetBytes("mock-trains/train-ic.jpg")))
                    path == "/assets/train-spr.jpg" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "image/jpeg")
                        .setBody(Buffer().write(loadAssetBytes("mock-trains/train-spr.jpg")))
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
        """{"trips":[
            {"ctxRecon":"mock-ctx-ic","transfers":0,"status":"NORMAL","plannedDurationInMinutes":20,"actualDurationInMinutes":20,
             "legs":[{"travelType":"PUBLIC_TRANSIT","name":"IC 1234","journeyDetailRef":"1234","cancelled":false,"product":{"categoryCode":"IC","shortCategoryName":"IC"},"origin":{"name":"Amsterdam Centraal","plannedDateTime":"$depStr","actualDateTime":"$depStr","plannedTrack":"4","actualTrack":"4"},"destination":{"name":"Almere Centrum","plannedDateTime":"$arrStr","actualDateTime":"$arrStr","plannedTrack":"2","actualTrack":"2"}}]},
            {"ctxRecon":"mock-ctx-spr","transfers":1,"status":"NORMAL","plannedDurationInMinutes":29,"actualDurationInMinutes":29,
             "legs":[
               {"travelType":"PUBLIC_TRANSIT","name":"SPR 4321","journeyDetailRef":"4321","cancelled":false,"product":{"categoryCode":"SPR","shortCategoryName":"SPR"},"origin":{"name":"Amsterdam Centraal","plannedDateTime":"$depStr","actualDateTime":"$depStr","plannedTrack":"5","actualTrack":"5"},"destination":{"name":"Amsterdam Amstel","plannedDateTime":"$sprMidStr","actualDateTime":"$sprMidStr","plannedTrack":"1","actualTrack":"1"}},
               {"travelType":"TRANSFER","name":"Transfer","origin":{"name":"Amsterdam Amstel","plannedDateTime":"$sprMidStr","actualDateTime":"$sprMidStr"},"destination":{"name":"Amsterdam Amstel","plannedDateTime":"$sprSecondDepStr","actualDateTime":"$sprSecondDepStr"}},
               {"travelType":"PUBLIC_TRANSIT","name":"SPR 9876","journeyDetailRef":"9876","cancelled":false,"product":{"categoryCode":"SPR","shortCategoryName":"SPR"},"origin":{"name":"Amsterdam Amstel","plannedDateTime":"$sprSecondDepStr","actualDateTime":"$sprSecondDepStr","plannedTrack":"3","actualTrack":"3"},"destination":{"name":"Almere Centrum","plannedDateTime":"$sprArrStr","actualDateTime":"$sprArrStr","plannedTrack":"5","actualTrack":"5"}}
             ]}
        ]}"""

    private fun singleTripResponse(ctxRecon: String?): String {
        if (ctxRecon == "mock-ctx-spr") {
            return """{"ctxRecon":"mock-ctx-spr","transfers":1,"status":"NORMAL","plannedDurationInMinutes":29,"actualDurationInMinutes":29,
                "legs":[
                  {"travelType":"PUBLIC_TRANSIT","name":"SPR 4321","journeyDetailRef":"4321","cancelled":false,"product":{"categoryCode":"SPR","shortCategoryName":"SPR"},"origin":{"name":"Amsterdam Centraal","plannedDateTime":"$depStr","actualDateTime":"$depStr","plannedTrack":"5","actualTrack":"5"},"destination":{"name":"Amsterdam Amstel","plannedDateTime":"$sprMidStr","actualDateTime":"$sprMidStr","plannedTrack":"1","actualTrack":"1"}},
                  {"travelType":"TRANSFER","name":"Transfer","origin":{"name":"Amsterdam Amstel","plannedDateTime":"$sprMidStr","actualDateTime":"$sprMidStr"},"destination":{"name":"Amsterdam Amstel","plannedDateTime":"$sprSecondDepStr","actualDateTime":"$sprSecondDepStr"}},
                  {"travelType":"PUBLIC_TRANSIT","name":"SPR 9876","journeyDetailRef":"9876","cancelled":false,"product":{"categoryCode":"SPR","shortCategoryName":"SPR"},"origin":{"name":"Amsterdam Amstel","plannedDateTime":"$sprSecondDepStr","actualDateTime":"$sprSecondDepStr","plannedTrack":"3","actualTrack":"3"},"destination":{"name":"Almere Centrum","plannedDateTime":"$sprArrStr","actualDateTime":"$sprArrStr","plannedTrack":"5","actualTrack":"5"}}
                ]}
            """.trimIndent()
        }
        val track = updatedSingleTripTrack ?: "4"
        return """{"ctxRecon":"mock-ctx-ic","transfers":0,"status":"NORMAL","plannedDurationInMinutes":20,"actualDurationInMinutes":20,"legs":[{"travelType":"PUBLIC_TRANSIT","name":"IC 1234","journeyDetailRef":"1234","cancelled":false,"product":{"categoryCode":"IC","shortCategoryName":"IC"},"origin":{"name":"Amsterdam Centraal","plannedDateTime":"$depStr","actualDateTime":"$depStr","plannedTrack":"$track","actualTrack":"$track"},"destination":{"name":"Almere Centrum","plannedDateTime":"$arrStr","actualDateTime":"$arrStr","plannedTrack":"2","actualTrack":"2"}}]}"""
    }

    private fun arrivalsResponse(): String =
        """{"payload":{"arrivals":[
            {"plannedTrack":"4","actualTrack":"4","plannedDateTime":"$originArrivalStr","actualDateTime":"$originArrivalStr"},
            {"plannedTrack":"5","actualTrack":"5","plannedDateTime":"$originArrivalStr","actualDateTime":"$originArrivalStr"}
        ]}}"""

    private fun trainDetailResponse(trainId: String, baseUrl: String): String {
        val imagePath = if (trainId == "4321" || trainId == "9876") "train-spr.jpg" else "train-ic.jpg"
        val length = if (imagePath == "train-spr.jpg") 4 else 6
        return """
        {"lengte":$length,"materieeldelen":[{"materieelnummer":$trainId,"type":"${if (imagePath == "train-spr.jpg") "SPR" else "ICM"}","afbeeldingsSpecs":{"imageUrl":"$baseUrl/assets/$imagePath"}}]}
        """.trimIndent()
    }

    private val sprMidTime: ZonedDateTime = mockDepartureTime.plusMinutes(12)
    private val sprSecondDepTime: ZonedDateTime = sprMidTime.plusMinutes(4)
    private val sprArrTime: ZonedDateTime = sprSecondDepTime.plusMinutes(13)
    private val sprMidStr: String = sprMidTime.format(TIMESTAMP_FMT)
    private val sprSecondDepStr: String = sprSecondDepTime.format(TIMESTAMP_FMT)
    private val sprArrStr: String = sprArrTime.format(TIMESTAMP_FMT)
    private val originArrivalStr: String = mockDepartureTime.minusMinutes(10).format(TIMESTAMP_FMT)

    private fun loadAssetBytes(path: String): ByteArray {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.open(path).use { it.readBytes() }
    }

}

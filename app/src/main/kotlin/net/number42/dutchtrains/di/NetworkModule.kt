package net.number42.dutchtrains.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import net.number42.dutchtrains.data.api.NsApiKeyInterceptor
import net.number42.dutchtrains.data.api.NsStationsService
import net.number42.dutchtrains.data.api.NsTripsService
import net.number42.dutchtrains.data.api.VirtualTrainService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://gateway.apiportal.ns.nl/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        apiKeyInterceptor: NsApiKeyInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
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
}

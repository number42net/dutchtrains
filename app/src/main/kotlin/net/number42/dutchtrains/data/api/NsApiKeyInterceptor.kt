package net.number42.dutchtrains.data.api

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import net.number42.dutchtrains.DutchTrainsApp
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class NsApiKeyInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .addHeader("Ocp-Apim-Subscription-Key",
                    (context.applicationContext as DutchTrainsApp).cachedApiKey)
                .build()
        )
}

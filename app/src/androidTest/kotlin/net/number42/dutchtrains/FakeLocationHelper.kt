package net.number42.dutchtrains

import android.content.Context
import android.location.Location
import net.number42.dutchtrains.util.LocationHelper

class FakeLocationHelper(context: Context) : LocationHelper(context) {
    companion object {
        @Volatile var nextResult: Result<Location> = Result.failure(Exception("No mock location configured"))
    }

    override suspend fun getCurrentLocation(): Result<Location> = nextResult
}

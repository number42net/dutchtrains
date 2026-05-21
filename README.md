# Dutch Trains

Android app for checking NS train departures and following live train journeys.

## Features

- Search trips between any two Dutch stations
- View trip details: departure/arrival times, platform, duration, and rolling stock
- Follow a trip — a foreground service tracks it and notifies you of platform changes, delays, and arrival times
- Nearest station detection via GPS

## Requirements

- Android 8.0+ (API 26)
- An [NS API key](https://apiportal.ns.nl/) (free, requires registration)

## Getting started

1. Clone the repo
2. Open in Android Studio
3. Run the app on a device or emulator
4. Enter your NS API key in Settings on first launch

## Configuration

The NS API key is stored in `local.properties` for local development (not committed):

```
NS_API_KEY=your_key_here
```

The app also reads it from a `.env` file in the repo root, which is used by the dev container setup.

## Building

```sh
# Debug APK
./scripts/build-debug.sh

# Or directly
./gradlew assembleDebug
```

The output APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Testing

```sh
# Instrumented tests (requires a connected device or running emulator)
./scripts/run-tests.sh

# Or directly
./gradlew connectedDebugAndroidTest
```

The instrumented test suite uses a `MockWebServer` in place of the real NS API, so no API key is needed.

## Tech stack

- Kotlin + Jetpack Compose
- Hilt (dependency injection)
- Retrofit + OkHttp + kotlinx.serialization (networking)
- DataStore (preferences)
- Foreground service for live trip following
- UiAutomator2 + Hilt for instrumented tests

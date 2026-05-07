# Claude Code guidance for this repo

## What this project is

Minimal Android-to-Android text chat over **Bluetooth Classic (RFCOMM)**. Java, no Kotlin. Single-module Android Studio project (`:app`).

The targeted hardware is fixed and unusual: **Samsung Galaxy S3 (Android 4.3, API 18)** and **Galaxy Note 10 Lite (Android 13, API 33)**. The constraints below all flow from supporting those two devices simultaneously.

## Hard constraints (don't change without checking)

- **`minSdk = 18`** — Galaxy S3's ceiling. Raising this drops S3 support.
- **`compileSdk = 34` / `targetSdk = 34`** — needed for the runtime `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` constants on the Note 10 Lite.
- **AppCompat is pinned to `1.3.1`.** Versions ≥ 1.4 require `minSdk = 19` and will silently break the S3 build. If you see a Dependabot bump for `androidx.appcompat:appcompat`, reject it.
- **AGP 8.2.2 + Gradle 8.4 + JDK 17.** AGP 8.x is required for the modern Bluetooth permission constants; it also forbids `package=` in `AndroidManifest.xml` — the namespace lives in `app/build.gradle.kts` instead.
- **Java 8 source/target with desugaring.** The lambdas in `MainActivity` get desugared by D8 down to API 18.

## Permission model — easy to break

The manifest is split deliberately:

- `BLUETOOTH` and `BLUETOOTH_ADMIN` are capped at `maxSdkVersion="30"` — they're deprecated on Android 12+.
- `ACCESS_FINE_LOCATION` is also capped at `maxSdkVersion="30"`. On API 31+ we use `BLUETOOTH_SCAN` with `usesPermissionFlags="neverForLocation"` instead.
- `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` are unconditional but only granted via runtime dialog on API 31+.

`MainActivity.requestNeededPermissions()` mirrors this split:

- API < 23 (S3): early-return — everything is install-time granted.
- API 23–30: request `ACCESS_FINE_LOCATION`.
- API ≥ 31: request `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN`.

Don't merge these branches. The S3 in particular cannot accept a request for `BLUETOOTH_CONNECT` — the constant inlines fine at compile time, but the platform doesn't recognize the permission, and the request just falls through silently.

## Threading / state machine

`BluetoothChatService` runs three thread types and four states (`NONE`, `LISTEN`, `CONNECTING`, `CONNECTED`). The lifecycle invariants worth preserving:

- `start()` is idempotent and the canonical "go back to listening" call.
- A failed `ConnectThread.run()` must `setState(NONE)` *before* calling `start()`, so the UI flips to "Not connected" before "Listening".
- A dead `ConnectedThread.run()` must call `BluetoothChatService.this.start()` so we resume listening — without it, the service stays stuck in `CONNECTED` after a disconnect.
- `AcceptThread` null-guards `mmServerSocket` because `listenUsingRfcommWithServiceRecord` can throw, which would otherwise NPE in `cancel()`.

If you change any of these, walk through all four state transitions on paper.

## Build / run commands

```sh
# Open in Android Studio (Hedgehog or newer) — first sync generates gradlew.
# Or, with system Gradle 8.x:
gradle wrapper --gradle-version 8.4

# Then:
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The wrapper jar is intentionally not committed — Android Studio regenerates it.

## What's deliberately *not* here

- No Kotlin. Don't introduce it; the deps and AppCompat version are pinned for old hardware.
- No Jetpack Compose, no ConstraintLayout, no Material Components — they all bump minSdk past 18.
- No release signing config / Play Store flow — this is a personal-installation app.
- No tests. Adding a `:test` source set is fine if useful, but there's nothing there now.

## Common pitfalls

- The `package` in Java sources must be `com.bluetoothchat` — matching the directory under `app/src/main/java/`. An earlier version had `main.java.com.bluetoothchat` (literal dirs from project root); that breaks once Gradle treats `app/src/main/java` as the source root.
- The layout file is `activity_device_list.xml` (matches `R.layout.activity_device_list` in `DeviceListActivity`). Renaming one without the other breaks resource resolution.
- IDE diagnostics like "BluetoothServerSocket cannot be resolved" or "Build cannot be resolved" usually mean Gradle hasn't synced yet — not a real code error.

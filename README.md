# BluetoothChat

Minimal offline Android-to-Android text chat over Bluetooth Classic (RFCOMM).

Built and tested for two specific devices:

| Device              | Android | API |
| ------------------- | ------- | --- |
| Samsung Galaxy S3   | 4.3     | 18  |
| Galaxy Note 10 Lite | 13      | 33  |

No internet, no servers, no BLE — just an RFCOMM socket between two paired phones, ~10 m range.

## How it works

One device listens (`AcceptThread` → `BluetoothServerSocket.accept()`), the other connects (`ConnectThread` → `createRfcommSocketToServiceRecord`). Both sides share a hard-coded UUID. Once a socket is open, a single `ConnectedThread` reads incoming bytes and writes outgoing ones; UI updates flow through a `Handler` on the main thread.

| File                          | Role                                                                |
| ----------------------------- | ------------------------------------------------------------------- |
| `MainActivity.java`           | UI: status, message list, send box, connect button                  |
| `DeviceListActivity.java`     | Picker of paired Bluetooth devices                                  |
| `BluetoothChatService.java`   | Threaded state machine: LISTEN → CONNECTING → CONNECTED → re-LISTEN |

## Build

Requires **Android Studio Hedgehog (2023.1) or newer** — AGP 8.2 needs JDK 17.

1. Open the project root in Android Studio. On first sync it generates `gradlew` and downloads Gradle 8.4.
2. Plug in a phone with USB debugging enabled and hit Run, or build an APK from the command line:
   ```sh
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

If you don't have Android Studio, run `gradle wrapper --gradle-version 8.4` once from the project root with a system Gradle 8.x installed.

## Use

1. Pair the two phones via Android **Settings → Bluetooth** (one-time).
2. Install and launch the app on both devices. Each one starts listening automatically.
3. On one phone, tap **Connect to Device** and pick the other.
4. Once the status reads "Connected", either side can type and **Send**.

## Permission model

Permissions are split by API level (see `AndroidManifest.xml`):

- **API ≤ 30 (S3):** `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` — install-time grant, no dialog.
- **API ≥ 31 (Note 10 Lite):** `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (with `neverForLocation`) — runtime dialog the first time the app starts.

`MainActivity.requestNeededPermissions()` only asks for what the running OS actually understands.

## Notes

- The UUID in `BluetoothChatService.MY_UUID` must match on both devices — that's the case here because both run the same APK.
- Galaxy S3 (i9300) maxes out at Android 4.3, which is why `minSdk = 18`. Bumping that to 19 would drop S3 support.
- AppCompat is pinned at **1.3.1** — the last release that supports `minSdk < 19`.

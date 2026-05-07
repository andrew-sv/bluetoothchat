#!/usr/bin/env bash
#
# Build a debug APK for BluetoothChat.
#
# The same APK installs on both targeted devices:
#   - Samsung Galaxy S3   (Android 4.3, API 18)
#   - Galaxy Note 10 Lite (Android 13, API 33)
#
# Usage:
#   ./build.sh           # build app-debug.apk
#   ./build.sh install   # build, then `adb install -r` to every connected device
#   ./build.sh dist      # build + copy named APKs into ./dist/ for each phone
#   ./build.sh clean     # ./gradlew clean and exit

set -euo pipefail

cd "$(dirname "$0")"

GRADLE_WRAPPER_VERSION="8.4"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# --- preflight ---------------------------------------------------------------

if [[ ! -d "$SDK_DIR" ]]; then
    echo "error: Android SDK not found at $SDK_DIR" >&2
    echo "set ANDROID_SDK_ROOT or install the SDK there" >&2
    exit 1
fi

if [[ ! -f local.properties ]]; then
    echo "sdk.dir=$SDK_DIR" > local.properties
    echo "wrote local.properties → $SDK_DIR"
fi

if ! java -version 2>&1 | grep -qE 'version "(17|21)\.'; then
    echo "warning: AGP 8.2 wants JDK 17 or 21; got: $(java -version 2>&1 | head -1)" >&2
fi

# --- wrapper bootstrap -------------------------------------------------------

if [[ ! -x ./gradlew ]] || [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
    if ! command -v gradle >/dev/null 2>&1; then
        echo "error: no ./gradlew and no system 'gradle' to bootstrap it" >&2
        echo "install gradle (brew install gradle) or open the project in Android Studio once" >&2
        exit 1
    fi
    echo "bootstrapping Gradle wrapper at $GRADLE_WRAPPER_VERSION..."
    gradle wrapper --gradle-version "$GRADLE_WRAPPER_VERSION" --distribution-type bin
fi

# --- dispatch ----------------------------------------------------------------

case "${1:-build}" in
    clean)
        ./gradlew clean
        exit 0
        ;;
    install)
        ./gradlew :app:assembleDebug
        if ! command -v adb >/dev/null 2>&1; then
            echo "error: adb not found; can't install" >&2
            exit 1
        fi
        DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
        if [[ -z "$DEVICES" ]]; then
            echo "no devices attached (run 'adb devices' to check)"
            exit 1
        fi
        for d in $DEVICES; do
            echo "installing on $d..."
            adb -s "$d" install -r "$APK_PATH"
        done
        ;;
    dist)
        ./gradlew :app:assembleDebug
        mkdir -p dist
        # Same APK, two device-labeled copies. minSdk=18/targetSdk=34 means
        # one binary serves both phones — these files are byte-identical.
        cp "$APK_PATH" dist/bluetoothchat-galaxy-s3.apk
        cp "$APK_PATH" dist/bluetoothchat-note10-lite.apk
        echo
        echo "dist/:"
        ls -lh dist/
        echo
        echo "(both files are identical — single APK covers Android 4.3 and 13)"
        exit 0
        ;;
    build|"")
        ./gradlew :app:assembleDebug
        ;;
    *)
        echo "usage: $0 [build|install|clean]" >&2
        exit 1
        ;;
esac

echo
echo "APK: $(pwd)/$APK_PATH"
ls -lh "$APK_PATH"

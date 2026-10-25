#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "/home/amd/Android/Sdk" ]; then
        export ANDROID_HOME="/home/amd/Android/Sdk"
    fi
fi
export ANDROID_SDK_ROOT="${ANDROID_HOME:-}"

if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -n "${ANDROID_HOME:-}" ]; then
    if [ -d "$ANDROID_HOME/ndk/28.2.13676358" ]; then
        export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
    else
        NDK_LATEST=$(find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -n 1 || true)
        if [ -n "$NDK_LATEST" ]; then
            export ANDROID_NDK_HOME="$NDK_LATEST"
        fi
    fi
fi
export ANDROID_NDK_ROOT="${ANDROID_NDK_HOME:-}"
export ANDROID_USER_HOME="$SCRIPT_DIR/android/.android"
mkdir -p "$ANDROID_USER_HOME"

echo "=== Compilando DmC: Devil May Cry para Android (ARM64-v8a) ==="
echo "SDK: $ANDROID_HOME"
echo "NDK: $ANDROID_NDK_HOME"

cd "$SCRIPT_DIR/android"
./gradlew assembleRelease "$@"

APK_PATH="$SCRIPT_DIR/android/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
    if [ -f "$HOME/.android/debug.keystore" ] && [ -f "$ANDROID_HOME/build-tools/35.0.0/apksigner" ]; then
        "$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
            --ks "$HOME/.android/debug.keystore" \
            --ks-pass pass:android \
            --ks-key-alias androiddebugkey \
            --key-pass pass:android \
            --v1-signing-enabled true \
            --v2-signing-enabled true \
            --v3-signing-enabled true \
            "$APK_PATH" >/dev/null 2>&1 || true
    fi
    echo "=== Build concluído com sucesso! ==="
    echo "APK gerado em: $APK_PATH"
fi

#!/bin/bash
set -e
ADB=/Users/samirkape/Library/Android/sdk/platform-tools/adb
APK=app/build/outputs/apk/standard/debug/app-standard-arm64-v8a-debug.apk

echo "Installing APK..."
$ADB install -r "$APK"
echo "Install done. Starting logcat for PWDebug tag..."
$ADB logcat -c
$ADB logcat -s PWDebug:D

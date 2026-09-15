#!/bin/bash
# Slideshow - сборка, установка и запуск на планшете/эмуляторе
set -e

PROJ="/Users/aleks/Rustore/SlideShow"
APK="$PROJ/app/build/outputs/apk/debug/app-debug.apk"

cd "$PROJ"
echo "==> Сборка..."
./gradlew :app:assembleDebug --no-daemon --console=plain

echo "==> Установка на подключённое устройство..."
adb install -r "$APK"

echo "==> Запуск приложения..."
adb shell am start -n com.example.slideshow/.MainActivity

echo "==> Готово."

#!/bin/bash
# Slideshow - сборка подписанного release APK (R8 + shrink)
set -e

PROJ="/Users/aleks/Rustore/SlideShow"
APK="$PROJ/app/build/outputs/apk/release/app-release.apk"

cd "$PROJ"

echo "==> Очистка предыдущих сборок..."
./gradlew clean --no-daemon --console=plain

echo "==> Сборка release APK (R8 + shrink + подпись)..."
./gradlew :app:assembleRelease --no-daemon --console=plain

if [ ! -f "$APK" ]; then
    echo "ОШИБКА: APK не найден: $APK"
    echo "Проверь keystore.properties или SLIDESHOW_KEYSTORE_* env-переменные."
    exit 1
fi

echo ""
echo "==> Release APK готов:"
ls -lh "$APK"

echo ""
echo "==> Установка на подключённое устройство..."
adb install -r "$APK"

echo "==> Запуск приложения..."
adb shell am start -n com.example.slideshow/.MainActivity

echo "==> Готово."

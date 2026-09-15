# SlideShow — план реализации (технические детали)

## Что нужно (зависимости / библиотеки)
- Android Gradle Plugin + Kotlin (AGP 8.x, Kotlin 2.x)
- Jetpack Compose BOM + Material 3
- Navigation Compose (навигация между экранами)
- Coil (compose) — загрузка/отображение изображений
- DataStore Preferences — настройки (тема, скорость, порядок)
- Accompanist / Window Size Class — адаптивность под планшеты
- (опционально) kotlinx-coroutines для таймера слайд-шоу

## Ключевые технические решения
- Разрешения: НЕ используем READ_EXTERNAL_STORAGE / MediaStore с runtime-запросами.
  Всё через SAF:
  - Фото/галерея: `ActivityResultContracts.PickMultipleVisualMedia` (фото-пикер).
  - Флэшка: `ActivityResultContracts.OpenDocumentTree` + `takePersistableUriPermission`.
- Слайд-шоу: `LaunchedEffect` + `delay(speedMs)` на Compose-экране; состояние
  CurrentIndex в `remember`/`rememberSaveable`. Shuffle — предварительно перемешанный
  список индексов, без повторов.
- Таймер остановки: при паузе экрана/приложения (Lifecycle) таймер сбрасывается.
- Тема: DataStore хранит enum; MaterialTheme цветовая схема собирается по нему,
  системная берётся из `isSystemInDarkTheme()`.

## Структура проекта
```
app/
  src/main/java/.../
    MainActivity.kt
    ui/
      theme/        (Theme.kt, Color.kt, Type.kt)
      selection/    (экран выбора картинок + ViewModel)
      slideshow/    (экран слайд-шоу + ViewModel)
      settings/     (экран настроек)
    data/
      SettingsRepository.kt   (DataStore)
      ImageRepository.kt      (SAF URI persistence + провайдер списка)
    model/
      Settings.kt
```

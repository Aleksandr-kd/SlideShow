package com.example.slideshow.ui.slideshow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshow.data.ImageRepository
import com.example.slideshow.data.SettingsRepository
import com.example.slideshow.model.PlayOrder
import com.example.slideshow.model.TransitionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

data class SlideshowUiState(
    val images: List<Uri> = emptyList(),
    val order: List<Int> = listOf(),
    val position: Int = 0,
    val speedMs: Long = 3000L,
    val playOrder: PlayOrder = PlayOrder.SEQUENTIAL,
    val transition: TransitionMode = TransitionMode.CROSSFADE,
    val playing: Boolean = true
) {
    val current: Uri? get() = images.getOrNull(order.getOrNull(position) ?: 0)
    val next: Uri?
        get() {
            if (total <= 0) return null
            val index = order.getOrNull((position + 1) % total) ?: return null
            return images.getOrNull(index)
        }
    val total: Int get() = images.size
}

// Инкремент с рамками безопасности: total 0 → остаёмся на месте.
private fun advanceTotal(total: Int, position: Int, delta: Int): Int {
    if (total <= 0) return 0
    return ((position + delta) % total + total) % total
}

class SlideshowViewModel(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Сериализует доступ к playJob, чтобы исключить гонку между коллектором
    // настроек (restartTimer) и прямыми вызовами (next/previous/toggle).
    private val timerMutex = Mutex()
    private var playJob: Job? = null

    // Собственный scope для записи снимка сессии: срабатывает в onCleared,
    // когда viewModelScope уже сворачивается, и в onStop/onDispose.
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Множество кадров, которые уже успели отрисоваться (Coil loaded). Таймер,
    // встречая кадр, которого в этом наборе нет, перепрыгивает к ближайшему
    // отрисованному кадру вместо того чтобы показывать «чёрный экран».
    // Потокобезопасный (ConcurrentHashMap.newKeySet): onFrameLoaded вызывается
    // и с main (onSuccess у SubcomposeAsyncImage), и с Dispatchers.IO (prefetch).
    private val readyUris = ConcurrentHashMap.newKeySet<Uri>()

    // Сколько неудачных попыток предзагрузки подряд уже было для кадра.
    private val retryAttempts = ConcurrentHashMap<Uri, Int>()

    // До какого момента времени (мс epoch) кадр НЕ трогаем предзагрузкой.
    // После серии сбоев (битый файл, отвалившаяся флешка) попытки разнесены
    // с растущим backoff, чтобы prefetch-цикл не долбил один и тот же uri
    // каждые 50 мс бесконечно и не блокировал дозаливку остального буфера.
    private val retryCooldownUntil = ConcurrentHashMap<Uri, Long>()

    // Стартовый список читаем один раз (два синхронных getUris() на главном
    // потоке в инициализаторе были лишней нагрузкой для 500+ URI).
    private val initialUris = imageRepository.getUris()

    private val _uiState = MutableStateFlow(
        SlideshowUiState(
            images = initialUris,
            order = buildOrder(initialUris, PlayOrder.SEQUENTIAL)
        )
    )
    val uiState: StateFlow<SlideshowUiState> = _uiState.asStateFlow()

    init {
        // Актуальный список картинок: если картинки добавили/удалили, пока слайдшоу
        // открыто — подхватываем без пересоздания VM (фикс расимметрии кэша).
        imageRepository.observeUris()
            .onEach { uris ->
                _uiState.update { state ->
                    // Список не менялся (типично для replay-эмиссии при входе на экран) —
                    // порядок и позицию не пересоздаём, чтобы shuffle не перетасовывался
                    // повторно прямо на старте слайд-шоу.
                    if (state.images == uris) return@update state
                    val wasShuffling = state.playOrder == PlayOrder.SHUFFLE
                    // Порядок пересчитываем по НОВОМУ списку (добавление/удаление),
                    // чтобы индексы не «уезжали» в shuffle-перестановке.
                    val newOrder = if (wasShuffling) buildOrder(uris, state.playOrder) else defaultOrder(uris)
                    // Сохраняем именно текущую картинку, а не позицию: при удалении
                    // или перетасовке индексы в новом order смещаются, и простой
                    // coerceAtMost перепрыгнул бы на соседнее фото.
                    val currentUri = state.current
                    val indexInNewOrder = currentUri?.let { uri ->
                        newOrder.indexOfFirst { uris.getOrNull(it) == uri }
                    }
                    val position = indexInNewOrder?.takeIf { it >= 0 }
                        ?: state.position.coerceIn(0, (uris.size - 1).coerceAtLeast(0))
                    state.copy(
                        images = uris,
                        order = newOrder,
                        position = position
                    )
                }
                restartTimer()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { state ->
                    val needsShuffle = settings.playOrder != state.playOrder
                    val nextOrder = if (needsShuffle) buildOrder(state.images, settings.playOrder) else state.order
                    // При смене порядка показа (в т.ч. по гонке с resumeFromLastSession)
                    // сохраняем текущий кадр: позиция пересчитывается по currentUri
                    // в НОВОМ порядке, а не остаётся слепым индексом.
                    val currentUri = state.current
                    val posInNewOrder = if (needsShuffle) {
                        currentUri?.let { uri ->
                            nextOrder.indexOfFirst { state.images.getOrNull(it) == uri }
                        }
                    } else null
                    state.copy(
                        speedMs = settings.speedMs,
                        playOrder = settings.playOrder,
                        transition = settings.transition,
                        order = nextOrder,
                        position = posInNewOrder?.takeIf { it >= 0 } ?: state.position
                    )
                }
                restartTimer()
            }
        }
        syncTimer()
    }

    fun togglePlay() {
        val playing = !_uiState.value.playing
        _uiState.update { it.copy(playing = playing) }
        // Синхронизируем таймер по ТЕКУЩЕМУ флагу playing атомарно под мутексом:
        // это исключает окно, когда отмена старого job приходила после запуска
        // нового при быстром spam-нажатии (слайд-шоу «замирало» при playing=true).
        syncTimer()
    }

    // Вызывается из UI, когда кадр успешно отрисовался (Coil loaded) или был
    // предзагружен в кэш. Такие кадры таймер показывает, а не пропускает.
    fun onFrameLoaded(uri: Uri) {
        readyUris.add(uri)
        // Кадр снова доступен — сбрасываем счётчики былых сбоев предзагрузки.
        retryAttempts.remove(uri)
        retryCooldownUntil.remove(uri)
    }

    // Проверка готовности кадра для фонового слота предзагрузки
    // (загружено и помечено готовым — можно не загружать повторно).
    fun isUriReady(uri: Uri): Boolean = uri in readyUris

    // Кадр можно пробовать предзагрузить, если он ещё не готов и у него не
    // истёк период «не трогать» после серии неудачных попыток.
    fun shouldPreload(uri: Uri): Boolean {
        if (uri in readyUris) return false
        val until = retryCooldownUntil[uri] ?: return true
        return System.currentTimeMillis() >= until
    }

    // Очередная неудача предзагрузки кадра: откладываем следующую попытку
    // с растущим backoff (10с, 20с, … максимум 60с), чтобы битый либо временно
    // недоступный uri не зацикливал prefetch (execute→null каждые 50 мс) и не
    // блокировал предзагрузку следующих кадров буфера.
    fun markFrameUnavailable(uri: Uri) {
        val n = (retryAttempts[uri] ?: 0) + 1
        retryAttempts[uri] = n
        retryCooldownUntil[uri] = System.currentTimeMillis() + (n * 10_000L).coerceAtMost(60_000L)
    }

    fun next() {
        _uiState.update { state ->
            if (state.total <= 0) return@update state
            // Ручной переход вперёд: сразу встаём на ближайший ОТРИСОВАННЫЙ кадр,
            // чтобы таймер не успел увести позицию обратно (гонка с prerender).
            val target = advanceTotal(state.total, state.position, +1)
            val jump = nextReadyPosition(state.images, state.order, target, state.total)
            state.copy(position = jump ?: state.position)
        }
        restartTimer()
    }

    fun previous() {
        _uiState.update { state ->
            if (state.total <= 0) return@update state
            // Ручной переход назад: симметрично next — перескакиваем неготовые кадры
            // НАЗАД к ближайшему отрисованному. Раньше прыжок искался только вперёд,
            // из-за чего «prev» на неготовый кадр откатывался обратно и казался мёртвым.
            val target = advanceTotal(state.total, state.position, -1)
            val jump = previousReadyPosition(state.images, state.order, target, state.total)
            state.copy(position = jump ?: state.position)
        }
        restartTimer()
    }

    private fun restartTimer() {
        viewModelScope.launch { timerMutex.withLock { syncTimerLocked() } }
    }

    // При сворачивании приложения таймер НЕ останавливаем: фоновое листание
    // продолжает предзагружать и отрисовывать фото, а юзер тем временем может
    // пользоваться другими программами. Фиксируем снимок сессии, чтобы диалог
    // «Продолжить/Заново» по-прежнему работал (позиция на момент сворачивания).
    fun onStop() {
        persistSession()
    }

    // «Продолжить с того места»: восстанавливает позицию из сохранённой сессии.
    // Вызывается один раз при входе на экран слайд-шоу, когда юзер выбрал
    // в диалоге «Продолжить». Если сессии нет (удалена, набор фото поменялся) —
    // просто стартуем с начала.
    fun resumeFromLastSession() {
        viewModelScope.launch {
            val session = settingsRepository.loadSlideshowState() ?: return@launch
            _uiState.update { state ->
                if (state.images.isEmpty()) return@update state
                val pos = positionOfFrame(
                    images = state.images,
                    order = state.order,
                    targetUri = session.currentUri,
                    fallback = session.position
                )
                state.copy(position = pos)
            }
            restartTimer()
        }
    }

    // Ищет позицию конкретного кадра В ПОРЯДКЕ ПОКАЗА (order), а не по индексу
    // в списке images: при SHUFFLE порядок не совпадает со списком, и простое
    // indexOfFirst вернуло бы «середину, но не тот кадр». Если кадр не найден
    // или URI пуст — откатываемся к сохранённой позиции.
    private fun positionOfFrame(
        images: List<Uri>,
        order: List<Int>,
        targetUri: String?,
        fallback: Int
    ): Int {
        val total = images.size
        if (total <= 0) return 0
        if (!targetUri.isNullOrEmpty()) {
            val found = order.indexOfFirst { pos -> images.getOrNull(pos)?.toString() == targetUri }
            if (found >= 0) return found
        }
        return fallback.coerceIn(0, total - 1)
    }

    // Фиксирует снимок текущего состояния слайд-шоу в хранилище. Нужно для
    // диалога «Продолжить/Заново» при повторном запуске. Вызов идемпотентен:
    // onStop, onDispose экрана и onCleared пишут одно и то же.
    fun persistSession() {
        val state = _uiState.value
        if (state.images.isEmpty()) return
        persistScope.launch {
            settingsRepository.saveSlideshowState(
                uris = state.images.map { it.toString() },
                position = state.position,
                currentUri = state.current?.toString(),
                total = state.total
            )
        }
    }

    override fun onCleared() {
        persistSession()
        persistScope.cancel()
        super.onCleared()
    }

    private fun syncTimer() {
        viewModelScope.launch { timerMutex.withLock { syncTimerLocked() } }
    }

    // Единственная точка приведения таймера в соответствие текущему состоянию.
    // Вызывается только под timerMutex, поэтому каждая мутация (пауза/старт/сброс)
    // — атомарна: сначала гасим старый job, затем, если нужно, создаём новый.
    private fun syncTimerLocked() {
        stopTimerLocked()
        if (!_uiState.value.playing || _uiState.value.images.isEmpty()) return
        playJob = viewModelScope.launch {
            // Скорость читаем из актуального состояния каждый тик: settings-коллектор
            // может поменять speedMs между кадрами без пересоздания джоба.
            while (isActive) {
                val speedMs = _uiState.value.speedMs.coerceAtLeast(250)
                val snapshot = _uiState.value
                val total = snapshot.total
                if (total <= 0) break
                val currentUri = snapshot.current
                val currentReady = currentUri != null && readyUris.contains(currentUri)

                if (currentReady) {
                    // Текущий кадр отрисован: ждём положенное время и переходим к
                    // ближайшему следующему ОТРИСОВАННОМУ кадру (нормальный случай —
                    // это ровно следующий по порядку).
                    val start = System.currentTimeMillis()
                    delay(speedMs)
                    val elapsed = System.currentTimeMillis() - start
                    val delta = if (elapsed > speedMs) (elapsed / speedMs).toInt() else 1
                    _uiState.update { state ->
                        val from = state.position + delta
                        val next = nextReadyPosition(state.images, state.order, from, state.total)
                        // Если отрисованных впереди нет — держим текущий кадр на
                        // экране (не показывая «чёрный экран») и ждём предзагрузки.
                        state.copy(position = next ?: state.position)
                    }
                } else {
                    // Текущий кадр ещё не отрисовался: вместо «чёрного экрана»
                    // перескакиваем на ближайший отрисованный кадр в порядке показа.
                    // Если отрисованных пока нет вовсе — ждём предзагрузку и повторяем.
                    val jump = nextReadyPosition(snapshot.images, snapshot.order, snapshot.position, total)
                    if (jump != null && jump != snapshot.position) {
                        _uiState.update { it.copy(position = jump) }
                        continue
                    }
                    delay(minOf(speedMs, 80))
                }
            }
        }
    }

    // Позиция ближайшего ОТРИСОВАННОГО кадра в порядке показа, начиная с from
    // (по кругу). null, если готового кадра среди total вообще нет.
    private fun nextReadyPosition(images: List<Uri>, order: List<Int>, from: Int, total: Int): Int? {
        if (total <= 0) return null
        for (step in 0 until total) {
            val pos = (from + step) % total
            val idx = order.getOrNull(pos) ?: continue
            val uri = images.getOrNull(idx) ?: continue
            if (readyUris.contains(uri)) return pos
        }
        return null
    }

    // То же, но поиск идёт НАЗАД по кругу (для ручного previous): пользователь,
    // листая назад, попадает на ближайший отрисованный кадр, не «застревая»
    // на ещё не прогруженных (иначе кнопка prev выглядит неработающей).
    private fun previousReadyPosition(images: List<Uri>, order: List<Int>, from: Int, total: Int): Int? {
        if (total <= 0) return null
        for (step in 0 until total) {
            val pos = ((from - step) % total + total) % total
            val idx = order.getOrNull(pos) ?: continue
            val uri = images.getOrNull(idx) ?: continue
            if (readyUris.contains(uri)) return pos
        }
        return null
    }

    private fun stopTimerLocked() {
        playJob?.cancel()
        playJob = null
    }

    private fun defaultOrder(images: List<Uri>): List<Int> = images.indices.toList()

    private fun buildOrder(images: List<Uri>, order: PlayOrder): List<Int> {
        val base = images.indices.toList()
        return if (order == PlayOrder.SHUFFLE) base.shuffled() else base
    }
}

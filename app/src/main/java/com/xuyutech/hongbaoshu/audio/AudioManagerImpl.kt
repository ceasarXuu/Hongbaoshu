package com.xuyutech.hongbaoshu.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xuyutech.hongbaoshu.core.AppLogger
import com.xuyutech.hongbaoshu.data.ContentLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@OptIn(UnstableApi::class)
class AudioManagerImpl(
    private val context: Context,
    private val contentLoader: ContentLoader
) : AudioManager {

    private val _state = MutableStateFlow(AudioState())
    override val state: StateFlow<AudioState> = _state

    private val audioAttributes = Media3AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()
    
    private var narrationPlayer: ExoPlayer = createNarrationPlayer()
    private var stableNarrationPlayer: Player = createStableNarrationPlayer(narrationPlayer)
    
    private fun createNarrationPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)  // 使用更强的唤醒模式
            .build().also { player ->
                player.addListener(createNarrationListener(player))
            }
    }
    private val soundPool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
            .build()
    }

    private var flipSoundId: Int = 0
    private var narrationCompletionCallback: NarrationCompletionCallback? = null
    private val playbackServiceController = PlaybackServiceController(context)
    private var narrationPlaybackState: Int = Player.STATE_IDLE
    private var autoAdvancePending: Boolean = false
    private var lastNarrationSentenceIds: List<String> = emptyList()
    private var lastNarrationStartIndex: Int = 0
    private var lastRetrySentenceId: String? = null
    private var lastRetryCount: Int = 0
    private var lastRetryAtMs: Long = 0L

    private fun createStableNarrationPlayer(player: Player): Player {
        return object : ForwardingPlayer(player) {
            override fun getPlaybackState(): Int {
                return if (autoAdvancePending) {
                    Player.STATE_READY
                } else {
                    super.getPlaybackState()
                }
            }

            override fun isPlaying(): Boolean {
                return if (autoAdvancePending) {
                    true
                } else {
                    super.isPlaying()
                }
            }
        }
    }

    private fun createNarrationListener(player: ExoPlayer): Player.Listener {
        return object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newId = mediaItem?.mediaId
                if (newId != null) {
                    if (lastRetrySentenceId != newId) {
                        lastRetrySentenceId = newId
                        lastRetryCount = 0
                    }
                    _state.update { current ->
                        current.copy(
                            narrationSentenceId = newId,
                            narrationPosition = 0L,
                            narrationError = null
                        )
                    }
                    updateForegroundState()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                narrationPlaybackState = playbackState
                if (playbackState == Player.STATE_ENDED) {
                    val completedId = player.currentMediaItem?.mediaId
                    // 只有当整个列表播放结束（没有下一首了）时，才触发完成回调
                    // ExoPlayer 自动切歌时不会触发 STATE_ENDED，只会触发 onMediaItemTransition
                    if (completedId != null) {
                        autoAdvancePending = narrationCompletionCallback != null
                        _state.update { current ->
                            current.copy(
                                narrationPosition = 0L,
                                narrationError = null
                            )
                        }
                        updateForegroundState()
                        // 这里的回调含义变成了“本页/本列表播完”，通常用于翻页
                        narrationCompletionCallback?.onSentenceCompleted(completedId)
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 仅更新状态，不再负责“防抖动”，因为列表播放天然无缝
                val activeId = player.currentMediaItem?.mediaId
                if (activeId != null) {
                     if (isPlaying) {
                        autoAdvancePending = false
                    }
                    // 仍保留对 STATE_ENDED 的特殊处理，以防万一
                    if (!isPlaying &&
                        narrationPlaybackState == Player.STATE_ENDED &&
                        narrationCompletionCallback != null
                    ) {
                        return
                    }
                    _state.update { current ->
                        current.copy(
                            narrationPosition = player.currentPosition,
                            narrationPlaying = isPlaying,
                            narrationError = null
                        )
                    }
                    updateForegroundState()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.e("AudioManager", "Player error: ${error.message}")
                val activeId = player.currentMediaItem?.mediaId
                if (activeId != null) {
                    val recovered = tryRecoverFromPlayerError(
                        activeSentenceId = activeId,
                        mediaItemIndex = player.currentMediaItemIndex,
                        positionMs = player.currentPosition
                    )
                    if (recovered) return
                    _state.update { current ->
                        current.copy(
                            narrationSentenceId = null,
                            narrationPosition = 0L,
                            narrationPlaying = false,
                            narrationError = "朗读异常，请切换前台或重新播放"
                        )
                    }
                    updateForegroundState()
                }
            }
        }
    }
    
    override fun setNarrationCompletionCallback(callback: NarrationCompletionCallback?) {
        narrationCompletionCallback = callback
    }
    
    /**
     * 确保播放器处于可用状态，必要时重新创建
     */
    private fun ensureNarrationPlayerReady() {
        try {
            // 检查播放器是否处于错误状态
            if (narrationPlayer.playerError != null) {
                AppLogger.w("AudioManager", "Recreating narration player due to error")
                resetNarrationPlayer()
            }
        } catch (e: Exception) {
            AppLogger.e("AudioManager", "Error checking player state: ${e.message}")
            resetNarrationPlayer()
        }
    }

    private fun resetNarrationPlayer() {
        narrationPlayer.release()
        narrationPlayer = createNarrationPlayer()
        stableNarrationPlayer = createStableNarrationPlayer(narrationPlayer)
    }

    private fun buildNarrationMediaItems(sentenceIds: List<String>): List<MediaItem> {
        return sentenceIds.mapNotNull { id ->
            getContentLoader().narrationUri(id)?.let { uri ->
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("朗读")
                            .setArtist("红宝书")
                            .build()
                    )
                    .build()
            }
        }
    }

    private fun tryRecoverFromPlayerError(
        activeSentenceId: String,
        mediaItemIndex: Int,
        positionMs: Long
    ): Boolean {
        if (lastNarrationSentenceIds.isEmpty()) return false

        if (lastRetrySentenceId != activeSentenceId) {
            lastRetrySentenceId = activeSentenceId
            lastRetryCount = 0
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastRetryAtMs > 10_000L) {
            lastRetryCount = 0
        }
        lastRetryAtMs = now

        return if (lastRetryCount == 0) {
            lastRetryCount = 1
            restartNarrationFrom(activeSentenceId, mediaItemIndex, positionMs)
        } else {
            skipAfterNarrationError(activeSentenceId)
        }
    }

    private fun restartNarrationFrom(
        activeSentenceId: String,
        mediaItemIndex: Int,
        positionMs: Long
    ): Boolean {
        return try {
            resetNarrationPlayer()
            val mediaItems = buildNarrationMediaItems(lastNarrationSentenceIds)
            if (mediaItems.isEmpty()) return false

            val indexById = mediaItems.indexOfFirst { it.mediaId == activeSentenceId }
            val resumeIndex = when {
                indexById >= 0 -> indexById
                mediaItemIndex in 0 until mediaItems.size -> mediaItemIndex
                else -> lastNarrationStartIndex.coerceIn(0, mediaItems.lastIndex)
            }
            val safePositionMs = positionMs.coerceAtLeast(0L)
            val resumeId = mediaItems[resumeIndex].mediaId

            narrationPlayer.setMediaItems(mediaItems, resumeIndex, safePositionMs)
            narrationPlayer.prepare()
            narrationPlayer.playWhenReady = true

            _state.update { current ->
                current.copy(
                    narrationSentenceId = resumeId,
                    narrationPosition = safePositionMs,
                    narrationPlaying = true,
                    narrationError = null
                )
            }
            updateForegroundState()
            true
        } catch (e: Exception) {
            AppLogger.e("AudioManager", "Error recovering playback: ${e.message}")
            false
        }
    }

    private fun skipAfterNarrationError(activeSentenceId: String): Boolean {
        val idx = lastNarrationSentenceIds.indexOf(activeSentenceId)
        if (idx < 0) return false
        val nextIndex = idx + 1
        if (nextIndex > lastNarrationSentenceIds.lastIndex) return false
        lastRetryCount = 0
        return playNarrationList(lastNarrationSentenceIds, nextIndex)
    }

    private fun ensureResourcesLoaded() {
        if (flipSoundId == 0) {
            getContentLoader().flipSound()?.let { uri ->
                val rawPath = uri.path ?: return@let
                // file:///android_asset/path → strip prefix for AssetManager
                val assetPath = rawPath.removePrefix("/android_asset/")
                try {
                    context.assets.openFd(assetPath).use { afd ->
                        flipSoundId = soundPool.load(afd, 1)
                    }
                } catch (_: Exception) {
                    flipSoundId = 0
                }
            }
        }
    }

    override fun playNarrationList(sentenceIds: List<String>, startIndex: Int): Boolean {
        if (sentenceIds.isEmpty()) return false
        val idx = startIndex.coerceIn(0, sentenceIds.lastIndex)
        
        // 向后寻找第一个有音频的句子
        var validIdx = -1
        for (i in idx until sentenceIds.size) {
            if (getContentLoader().narrationUri(sentenceIds[i]) != null) {
                validIdx = i
                break
            }
        }

        if (validIdx < 0) return false

        val startId = sentenceIds[validIdx]

        ensureNarrationPlayerReady()
        autoAdvancePending = false
        lastNarrationSentenceIds = sentenceIds
        lastNarrationStartIndex = validIdx
        lastRetrySentenceId = startId
        lastRetryCount = 0

        try {
            val mediaItems = buildNarrationMediaItems(sentenceIds)
            
            if (mediaItems.isEmpty()) return false

            // buildNarrationMediaItems() may filter out missing audios, so we must map by mediaId.
            val startMediaIndex = mediaItems.indexOfFirst { it.mediaId == startId }
            if (startMediaIndex < 0) return false

            narrationPlayer.setMediaItems(mediaItems, startMediaIndex, 0L)
            narrationPlayer.prepare()
            narrationPlayer.playWhenReady = true
            
            _state.update { current ->
                current.copy(
                    narrationSentenceId = startId,
                    narrationPosition = 0L,
                    narrationPlaying = true,
                    narrationError = null
                )
            }
            updateForegroundState()
            return true
        } catch (e: Exception) {
            AppLogger.e("AudioManager", "Error playing list: ${e.message}")
            return false
        }
    }

    override fun playSentence(sentenceId: String): Boolean {
        return playNarrationList(listOf(sentenceId), 0)
    }

    override fun pauseSentence() {
        narrationPlayer.pause()
        autoAdvancePending = false
        _state.update { current ->
            current.copy(
                narrationPosition = narrationPlayer.currentPosition,
                narrationPlaying = false
            )
        }
        updateForegroundState()
    }

    override fun resumeSentence() {
        if (_state.value.narrationSentenceId != null) {
            autoAdvancePending = false
            narrationPlayer.playWhenReady = true
            _state.update { current ->
                current.copy(narrationPlaying = true, narrationError = null)
            }
        }
        updateForegroundState()
    }

    override fun stopSentence() {
        narrationPlayer.stop()
        autoAdvancePending = false
        lastNarrationSentenceIds = emptyList()
        lastNarrationStartIndex = 0
        lastRetrySentenceId = null
        lastRetryCount = 0
        _state.update { current ->
            current.copy(
                narrationSentenceId = null,
                narrationPosition = 0L,
                narrationPlaying = false
            )
        }
        updateForegroundState()
    }

    override fun playFlip() {
        ensureResourcesLoaded()
        if (flipSoundId != 0) {
            soundPool.play(flipSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    override fun currentPosition(): Long = narrationPlayer.currentPosition

    override fun seekTo(positionMs: Long) {
        if (_state.value.narrationSentenceId != null && positionMs >= 0) {
            narrationPlayer.seekTo(positionMs)
            _state.update { current ->
                current.copy(narrationPosition = positionMs)
            }
        }
    }

    @Volatile
    private var contentLoaderRef: ContentLoader = contentLoader

    override fun updateContentLoader(loader: ContentLoader) {
        contentLoaderRef = loader
        AppLogger.d("AudioManagerImpl", "ContentLoader updated to: ${loader.javaClass.simpleName}")
    }

    private fun getContentLoader(): ContentLoader = contentLoaderRef

    override fun release() {
        narrationPlayer.release()
        soundPool.release()
        playbackServiceController.stop()
    }

    override fun setNarrationSpeed(speed: Float) {
        val safeSpeed = coerceNarrationSpeed(speed)
        try {
            narrationPlayer.setPlaybackSpeed(safeSpeed)
        } catch (e: Exception) {
            AppLogger.e("AudioManager", "Error setting playback speed: ${e.message}")
        }
        _state.update { current ->
            current.copy(narrationSpeed = safeSpeed)
        }
    }

    override fun activePlayer(): Player? {
        return when {
            _state.value.narrationSentenceId != null -> stableNarrationPlayer
            else -> null
        }
    }

    private fun updateForegroundState() {
        val shouldRun = _state.value.narrationSentenceId != null
        if (shouldRun) {
            playbackServiceController.start()
        } else {
            playbackServiceController.stop()
        }
    }
}

internal fun coerceNarrationSpeed(speed: Float): Float {
    return speed.coerceIn(0.75f, 1.25f)
}

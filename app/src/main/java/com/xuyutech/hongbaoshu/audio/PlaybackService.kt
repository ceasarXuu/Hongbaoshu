package com.xuyutech.hongbaoshu.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.xuyutech.hongbaoshu.MainActivity
import com.xuyutech.hongbaoshu.R
import com.xuyutech.hongbaoshu.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@UnstableApi
class PlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var fallbackPlayer: ExoPlayer? = null
    private lateinit var audioManager: AudioManager
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        audioManager = ServiceLocator.provideAudioManager(applicationContext)
        val activePlayer = audioManager.activePlayer()
        val player = activePlayer ?: ExoPlayer.Builder(this).build().also { fallbackPlayer = it }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this))
        serviceScope.launch {
            audioManager.state.collectLatest {
                syncSessionPlayer()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                syncSessionPlayer()
                startForegroundIfNeeded()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
                stopSelf()
            }
            else -> {
                syncSessionPlayer()
                if (audioManager.state.value.narrationSentenceId != null) {
                    startForegroundIfNeeded()
                }
            }
        }
        return START_STICKY
    }

    private fun syncSessionPlayer() {
        val session = mediaSession ?: return
        val activePlayer = audioManager.activePlayer()
        if (activePlayer != null && session.player !== activePlayer) {
            session.setPlayer(activePlayer)
            fallbackPlayer?.release()
            fallbackPlayer = null
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        fallbackPlayer?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundIfNeeded() {
        if (isForeground) return
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        isForeground = true
    }

    private fun buildNotification(): Notification {
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val isNarration = audioManager.state.value.narrationSentenceId != null
        val contentText = if (isNarration) "朗读中" else "播放中"
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("红宝匣")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(sessionActivity)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "播放服务",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    companion object {
        const val ACTION_START = "com.xuyutech.hongbaoshu.audio.ACTION_START"
        const val ACTION_STOP = "com.xuyutech.hongbaoshu.audio.ACTION_STOP"
        private const val NOTIFICATION_CHANNEL_ID = "playback_service"
        private const val NOTIFICATION_ID = 1001
    }
}

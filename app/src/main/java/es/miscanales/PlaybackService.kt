package es.miscanales

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/**
 * Mientras suena un vídeo en el móvil: mantiene la app viva con la pantalla apagada, y pone el
 * aviso con play, pausa, anterior y siguiente (también en la pantalla de bloqueo y en los auriculares).
 * El vídeo en sí sigue en la página del reproductor; esto solo le pasa las órdenes.
 */
class PlaybackService : Service() {
    private var session: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var title = ""
    private var channel = ""
    private var playing = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Reproducción", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        session = MediaSession(this, "MisCanales").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = PlayerActivity.control("play()")
                override fun onPause() = PlayerActivity.control("pause()")
                override fun onStop() = PlayerActivity.control("pause()")
                override fun onSkipToNext() = PlayerActivity.skip(next = true)
                override fun onSkipToPrevious() = PlayerActivity.skip(next = false)
            })
            isActive = true
        }
        // que no se duerman ni el procesador ni la wifi mientras suena
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MisCanales:reproduccion")
            .apply {
                setReferenceCounted(false)
                acquire(MAX_LOCK)
            }
        @Suppress("DEPRECATION")
        wifiLock = applicationContext.getSystemService(WifiManager::class.java)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MisCanales:reproduccion")
            ?.apply {
                setReferenceCounted(false)
                acquire()
            }
        showState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) {
            PlayerActivity.control("toggle()")
        } else if (intent?.action == ACTION_NEXT || intent?.action == ACTION_PREVIOUS) {
            PlayerActivity.skip(next = intent.action == ACTION_NEXT)
        } else {
            title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
            channel = intent?.getStringExtra(EXTRA_CHANNEL).orEmpty()
            session?.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, channel)
                    .build(),
            )
        }
        ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY
    }

    private fun showState() {
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private fun showPlaying(value: Boolean) {
        if (value == playing) return
        playing = value
        showState()
        getSystemService(NotificationManager::class.java).notify(ID, notification())
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            PlayerActivity.reopenIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow_filled),
            if (playing) "Pausa" else "Seguir",
            toggle,
        ).build()
        fun skip(code: Int, action: String, icon: Int, label: String) = Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            label,
            PendingIntent.getService(
                this,
                code,
                Intent(this, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_play_arrow_filled)
            .setContentTitle(title.ifBlank { "Mis Canales" })
            .setContentText(channel)
            .setContentIntent(open)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(skip(2, ACTION_PREVIOUS, R.drawable.ic_skip_previous, "Anterior"))
            .addAction(action)
            .addAction(skip(3, ACTION_NEXT, R.drawable.ic_skip_next, "Siguiente"))
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        session?.release()
        session = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "reproduccion"
        private const val ID = 1
        private const val ACTION_TOGGLE = "es.miscanales.TOGGLE"
        private const val ACTION_NEXT = "es.miscanales.NEXT"
        private const val ACTION_PREVIOUS = "es.miscanales.PREVIOUS"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CHANNEL = "channel"

        // como mucho 4 horas seguidas, por si algo se queda colgado
        private const val MAX_LOCK = 4L * 60 * 60 * 1000

        @SuppressLint("StaticFieldLeak")
        private var instance: PlaybackService? = null

        fun start(context: Context, title: String, channel: String) {
            context.startForegroundService(
                Intent(context, PlaybackService::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_CHANNEL, channel),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }

        /** Lo llama el reproductor cuando el vídeo se pone en marcha o se pausa. */
        fun setPlaying(playing: Boolean) {
            instance?.showPlaying(playing)
        }
    }
}

package es.miscanales

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.os.PowerManager
import android.util.Log
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Los avisos de vídeos nuevos en el móvil: cada media hora se miran los feeds de tus canales, y por
 * cada vídeo nuevo sale un aviso que al tocarlo lo abre en Mis Canales, no en la app de YouTube.
 *
 * Lo lanza una alarma que Android deja sonar también con el móvil en reposo profundo (Doze): las
 * tareas programadas normales se aplazan entonces hasta que coges el móvil, aunque la app no tenga
 * restricciones de batería. Queda también la tarea programada, por si acaso; las dos no se pisan.
 */
object NewVideos {
    private const val JOB_ID = 4201
    private const val CHANNEL = "nuevos"
    private const val GROUP = "es.miscanales.NUEVOS"
    private const val SUMMARY_ID = 2
    private const val PREFS_KNOWN = "nuevos"

    /** Se avisa de lo publicado en este tiempo: si el móvil estuvo apagado un mes, no salen 50 avisos. */
    private const val RECENT = 2 * 24 * 3600 * 1000L

    /** Como mucho, tantos avisos sueltos de una vez; si hay más, solo el resumen. */
    private const val MAX_SINGLE = 6

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val ACTION_CHECK = "es.miscanales.MIRAR_NUEVOS"
    private const val TAG = "MisCanales"
    private val every = TimeUnit.MINUTES.toMillis(BuildConfig.CHECK_MINUTES)
    private val running = Mutex()

    /** Pone en marcha (o para) la revisión periódica. Si ya estaba en marcha, la deja como está. */
    fun schedule(context: Context, on: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!on) {
            scheduler.cancel(JOB_ID)
            alarms(context)?.cancel(alarmIntent(context))
            prefs(context).edit { remove("nextAlarm") }
            return
        }
        // la alarma, si no hay una pendiente (se pierden al reiniciar el móvil o si se fuerza el cierre de la app)
        val pending = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, NewVideosReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pending == null || prefs(context).getLong("nextAlarm", 0) < System.currentTimeMillis()) arm(context)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        // si Android no la acepta, la app sigue funcionando (sin avisos)
        runCatching {
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, NewVideosJob::class.java))
                    .setPeriodic(TimeUnit.MINUTES.toMillis(30), TimeUnit.MINUTES.toMillis(10))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }
    }

    /** La siguiente alarma, dentro de [every]. */
    internal fun arm(context: Context) {
        val at = System.currentTimeMillis() + every
        runCatching {
            alarms(context)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context))
            prefs(context).edit { putLong("nextAlarm", at) }
        }
    }

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, NewVideosReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_KNOWN, Context.MODE_PRIVATE)

    /** Si la app puede usar internet con el móvil en reposo (batería "Sin restricciones"). */
    fun unrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Si Android deja que la app ponga avisos (desde Android 13 hay que pedir permiso). */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private val scope = MainScope()

    /** Un aviso de prueba con [video], para ver cómo queda y si llegan. */
    fun preview(context: Context, video: Video) {
        val app = context.applicationContext
        if (!allowed(app)) {
            Toast.makeText(app, "Falta darle permiso a la app para poner avisos", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch { show(app, listOf(video)) }
    }

    /** Quita el aviso de un vídeo (al verlo). */
    fun dismiss(context: Context, vid: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(vid, 1)
    }

    /**
     * Mira los canales y avisa de lo nuevo. La primera vez (o con un canal recién añadido) solo
     * apunta lo que hay, para no avisar de todo lo anterior.
     */
    suspend fun check(context: Context, origin: String) = running.withLock {
        Log.i(TAG, "Avisos: revisión ($origin)")
        checkNow(context)
    }

    private suspend fun checkNow(context: Context) {
        val store = Store(context)
        if (!store.notifyNew) return
        val channels = store.channels.toList()
        if (channels.isEmpty()) return
        val (videos, _) = YouTube.everything(channels, store.hideShorts)
        if (videos.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_KNOWN, Context.MODE_PRIVATE)
        val knownVideos = prefs.getString("videos", null)?.split(",")?.filter { it.isNotEmpty() }
        val knownChannels = prefs.getString("channels", "").orEmpty().split(",").filter { it.isNotEmpty() }.toSet()
        val now = System.currentTimeMillis()
        val fresh = if (knownVideos == null) {
            emptyList()
        } else {
            val known = knownVideos.toHashSet()
            videos.filter {
                it.vid !in known && it.channelId in knownChannels && it.vid !in store.watched &&
                    now - it.published < RECENT
            }
        }
        prefs.edit {
            putString("videos", (videos.map { it.vid } + knownVideos.orEmpty()).distinct().take(3000).joinToString(","))
            putString("channels", (knownChannels + videos.map { it.channelId }).joinToString(","))
        }
        Log.i(TAG, "Avisos: ${videos.size} vídeos, ${fresh.size} nuevos")
        if (fresh.isNotEmpty()) show(context, fresh.sortedBy { it.published })
    }

    private suspend fun show(context: Context, videos: List<Video>) {
        if (!allowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Vídeos nuevos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando uno de tus canales sube un vídeo"
            },
        )
        if (videos.size <= MAX_SINGLE) {
            videos.forEach { video ->
                val picture = thumbnail(video.vid)
                val notification = Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_smart_display)
                    .setContentTitle(video.channelName)
                    .setContentText(video.title)
                    .setWhen(video.published.takeIf { it > 0 } ?: System.currentTimeMillis())
                    .setShowWhen(true)
                    .setAutoCancel(true)
                    .setGroup(GROUP)
                    .setContentIntent(open(context, video))
                    .apply {
                        if (picture != null) {
                            setLargeIcon(picture)
                            setStyle(
                                Notification.BigPictureStyle()
                                    .bigPicture(picture)
                                    .bigLargeIcon(null as android.graphics.drawable.Icon?)
                                    .setSummaryText(video.title),
                            )
                        } else {
                            setStyle(Notification.BigTextStyle().bigText(video.title))
                        }
                    }
                    .build()
                manager.notify(video.vid, 1, notification)
            }
        }
        // el resumen agrupa los avisos (y, si son muchos, los sustituye)
        if (videos.size > 1) {
            val inbox = Notification.InboxStyle()
            videos.takeLast(6).forEach { inbox.addLine("${it.channelName}: ${it.title}") }
            val summary = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_smart_display)
                .setContentTitle("${videos.size} vídeos nuevos")
                .setContentText(videos.map { it.channelName }.distinct().joinToString(", "))
                .setStyle(inbox)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build()
            manager.notify(SUMMARY_ID, summary)
        }
    }

    /** Al tocar el aviso: Mis Canales con ese vídeo (lo recoge [MainActivity], como "Ver en la tele"). */
    private fun open(context: Context, video: Video): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("play", video.vid)
            .putExtra("title", video.title)
            .putExtra("channelId", video.channelId)
            .putExtra("channelName", video.channelName)
            .putExtra("published", video.published)
        return PendingIntent.getActivity(
            context, video.vid.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** La miniatura del vídeo (16:9), o null si no se puede bajar. */
    private suspend fun thumbnail(vid: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("https://i.ytimg.com/vi/$vid/mqdefault.jpg").build()).execute().use {
                if (!it.isSuccessful) null else it.body.bytes().let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) }
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** La tarea periódica de Android que lanza [NewVideos.check]. */
class NewVideosJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                NewVideos.check(applicationContext, "tarea")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sin red o YouTube caído: ya se mirará la próxima vez
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }

    override fun onDestroy() {
        scope.coroutineContext.cancelChildren()
        super.onDestroy()
    }
}

/**
 * La alarma de [NewVideos] (y, al encender el móvil o actualizar la app, volver a ponerla).
 * Android da a la app un rato con el móvil en reposo para mirar los vídeos.
 */
class NewVideosReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action != "es.miscanales.MIRAR_NUEVOS") {
            // encendido o app actualizada: las alarmas se habían borrado
            app.getSharedPreferences("nuevos", Context.MODE_PRIVATE).edit { remove("nextAlarm") }
            NewVideos.schedule(app, Store(app).notifyNew)
            return
        }
        NewVideos.arm(app)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                withTimeout(50_000) { NewVideos.check(app, "alarma") }
            } catch (e: CancellationException) {
                // se acabó el tiempo: ya se mirará la próxima vez
            } catch (e: Exception) {
                // sin red o YouTube caído: ya se mirará la próxima vez
            } finally {
                pending.finish()
            }
        }
    }
}

package es.miscanales

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/** Por dónde vas en un vídeo: el segundo, lo que dura y cuándo se apuntó (para quedarse con lo último). */
@Serializable
data class Progress(val pos: Int, val dur: Int, val at: Long) {
    /** Visto hasta el final (o casi: los últimos segundos suelen ser la pantalla final). */
    val finished: Boolean get() = dur > 0 && pos >= maxOf(dur - 30, dur * 9 / 10)

    /** Empezado y sin acabar: se puede seguir. */
    val started: Boolean get() = pos >= 10 && !finished

    val fraction: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

/** El progreso de cada vídeo, guardado en el aparato. Lo usan la app y el reproductor. */
object Progresses {
    private const val KEY = "progress"
    private const val MAX = 400
    private val json = Json { ignoreUnknownKeys = true }
    private val map = MapSerializer(String.serializer(), Progress.serializer())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): Map<String, Progress> =
        runCatching { json.decodeFromString(map, prefs(context).getString(KEY, null) ?: "{}") }.getOrDefault(emptyMap())

    fun get(context: Context, vid: String): Progress? = all(context)[vid]

    /** Guarda [entries]; de cada vídeo se queda con lo más reciente. */
    @Synchronized
    fun put(context: Context, entries: Map<String, Progress>) {
        val merged = all(context).toMutableMap()
        for ((vid, progress) in entries) {
            val old = merged[vid]
            if (old == null || progress.at >= old.at) merged[vid] = progress
        }
        val kept = merged.entries.sortedByDescending { it.value.at }.take(MAX).associate { it.key to it.value }
        prefs(context).edit().putString(KEY, json.encodeToString(map, kept)).apply()
    }

    @Synchronized
    fun remove(context: Context, vid: String) {
        val all = all(context) - vid
        prefs(context).edit().putString(KEY, json.encodeToString(map, all)).apply()
    }
}

/**
 * El progreso compartido entre aparatos: la lista de tareas "<lista de canales>_progreso" de Home
 * Assistant (todo.mis_canales_progreso). Cada vídeo es un elemento: el nombre empieza por su ID,
 * y en la descripción va el progreso. Si la lista no existe, no se sincroniza y ya está.
 */
object ProgressSync {
    private const val MAX = 400
    private val json = Json { ignoreUnknownKeys = true }

    // los envíos siguen aunque se cierre el reproductor
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun list(config: HaConfig): String = config.entity + "_progreso"

    /** Todo el progreso guardado en Home Assistant. */
    suspend fun fetch(config: HaConfig): Map<String, Progress> =
        HomeAssistant.items(config, list(config)).mapNotNull { item ->
            val vid = item.summary.take(11)
            decode(item.description)?.let { vid to it }
        }.toMap()

    /** Sube el progreso de un vídeo, sin esperar ni avisar si falla. */
    fun push(context: Context, vid: String, title: String, progress: Progress) {
        val config = readHaConfig(context) ?: return
        scope.launch {
            try {
                val items = HomeAssistant.items(config, list(config))
                val summary = "$vid · ${title.take(80)}"
                val description = json.encodeToString(Progress.serializer(), progress)
                val existing = items.firstOrNull { it.summary.take(11) == vid }
                if (existing != null) {
                    HomeAssistant.update(config, existing.uid, summary, description, list(config))
                } else {
                    HomeAssistant.add(config, summary, description, list(config))
                }
                // que la lista no crezca sin fin: fuera los más antiguos
                if (items.size >= MAX + 50) {
                    items.sortedBy { decode(it.description)?.at ?: 0L }
                        .take(items.size + 1 - MAX)
                        .forEach { HomeAssistant.remove(config, it.uid, list(config)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sin conexión o sin la lista: se queda en el aparato
            }
        }
    }

    /** Quita un vídeo (al marcarlo como no visto). */
    fun forget(context: Context, vid: String) {
        val config = readHaConfig(context) ?: return
        scope.launch {
            try {
                HomeAssistant.items(config, list(config))
                    .filter { it.summary.take(11) == vid }
                    .forEach { HomeAssistant.remove(config, it.uid, list(config)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    private fun decode(description: String): Progress? =
        runCatching { json.decodeFromString(Progress.serializer(), description.trim()) }.getOrNull()
}

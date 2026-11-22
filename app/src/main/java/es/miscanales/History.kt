package es.miscanales

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Un vídeo del historial y cuándo se abrió (en milisegundos). [pending]: abierto aquí sin haber podido
 * subirlo aún a Home Assistant.
 */
@Serializable
data class HistoryEntry(val video: Video, val at: Long, val pending: Boolean = false)

/**
 * El historial: los vídeos que se han abierto, el último primero, sin repetir (al volver a abrir uno, sube
 * arriba). Uno por perfil (Kids no tiene). Con la lista «<lista de canales>_historial» en Home Assistant
 * (todo.mis_canales_historial, todo.mis_canales_montse_historial), es el mismo en todos los aparatos; sin
 * ella, cada aparato tiene el suyo. Lo de por dónde vas en cada vídeo va aparte: [ProgressSync].
 */
class History private constructor(val profile: Profile) {
    companion object {
        private const val KEY = "history"

        /** Ya se ha sincronizado alguna vez: desde entonces, lo que no está en Home Assistant se ha borrado. */
        private const val SYNCED_ONCE = "history_synced"

        /** Los que se guardan, como mucho (aquí y en Home Assistant). */
        private const val MAX = 300

        /** La primera vez, cuántos de los de este aparato se suben (los más recientes). */
        private const val UPLOAD = 100

        /** Cada cuánto, como mucho, se mira Home Assistant. */
        private const val EVERY = 20_000L

        private val all = mutableMapOf<Profile, History>()

        // lo de Home Assistant sigue aunque se cierre la pantalla o el reproductor
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        @Synchronized
        fun of(profile: Profile): History = all.getOrPut(profile) { History(profile) }

        /** La lista de Home Assistant del historial. */
        fun list(config: HaConfig): String = config.entity + "_historial"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(HistoryEntry.serializer())
    private val mutex = Mutex()
    private var loaded = false
    private var pulledAt = 0L

    /** El último abierto, primero. */
    val entries = mutableStateListOf<HistoryEntry>()

    /** true: es el mismo en todos los aparatos; false: no está la lista en Home Assistant; null: sin saberlo. */
    var synced by mutableStateOf<Boolean?>(null)
        private set

    /** Lee el guardado en el aparato (una vez). */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = context.getSharedPreferences(profile.prefs, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching { json.decodeFromString(serializer, saved) }.getOrNull()?.let { entries.addAll(it) }
    }

    private fun save(context: Context) {
        context.getSharedPreferences(profile.prefs, Context.MODE_PRIVATE)
            .edit { putString(KEY, json.encodeToString(serializer, entries.toList())) }
    }

    /** Apunta que se ha abierto [video] (sin la descripción, que ocupa mucho) y lo sube a Home Assistant. */
    fun add(context: Context, video: Video) {
        load(context)
        val entry = HistoryEntry(video.copy(description = ""), System.currentTimeMillis(), pending = true)
        entries.removeAll { it.video.vid == video.vid }
        entries.add(0, entry)
        while (entries.size > MAX) entries.removeAt(entries.lastIndex)
        save(context)
        val app = context.applicationContext
        val config = readHaConfig(app, profile) ?: return
        scope.launch {
            mutex.withLock {
                try {
                    val items = HomeAssistant.items(config, list(config))
                    upload(config, items, entry)
                    sent(app, entry)
                    prune(config, items)
                    synced = true
                    Log.i("MisCanales", "Historial: «${entry.video.title.take(40)}» subido")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HaError) {
                    synced = false
                } catch (e: Exception) {
                    // sin conexión: se sube la próxima vez que se mire
                }
            }
        }
    }

    /** Mira Home Assistant sin esperar (al abrir Listas o el historial). */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { pull(app) }
    }

    /**
     * Junta el historial de Home Assistant con el de aquí: sube lo abierto aquí sin subir y se queda, de cada
     * vídeo, la vez más reciente. Lo que ya no está allí (borrado desde otro aparato), fuera.
     */
    suspend fun pull(context: Context, force: Boolean = false) {
        load(context)
        val config = readHaConfig(context, profile)
        if (config == null) {
            synced = null
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - pulledAt < EVERY) return
        pulledAt = now
        mutex.withLock {
            try {
                val items = HomeAssistant.items(config, list(config))
                val remote = items.mapNotNull { decode(it.description) }
                val prefs = context.getSharedPreferences(profile.prefs, Context.MODE_PRIVATE)
                // la primera vez, todo lo de este aparato es nuevo para Home Assistant
                val first = !prefs.getBoolean(SYNCED_ONCE, false)
                val local = if (first) entries.take(UPLOAD) else entries.filter { it.pending }
                val newer = remote.associateBy { it.video.vid }
                var uploaded = 0
                for (entry in local) {
                    val there = newer[entry.video.vid]
                    if (there == null || there.at < entry.at) {
                        upload(config, items, entry)
                        uploaded++
                    }
                }
                // y lo que se haya abierto mientras tanto (se sube aparte)
                val late = entries.filter { e -> e.pending && local.none { it.video.vid == e.video.vid && it.at == e.at } }
                val merged = (late + remote + local.map { it.copy(pending = false) })
                    .sortedByDescending { it.at }
                    .distinctBy { it.video.vid }
                    .take(MAX)
                entries.clear()
                entries.addAll(merged)
                save(context)
                prefs.edit { putBoolean(SYNCED_ONCE, true) }
                prune(config, items)
                synced = true
                Log.i("MisCanales", "Historial: ${remote.size} en Home Assistant, $uploaded subidos de aquí (${entries.size})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: HaError) {
                // no está la lista «Mis Canales historial»: cada aparato, el suyo
                Log.i("MisCanales", "Historial: sin sincronizar (${e.message})")
                synced = false
            } catch (e: Exception) {
                // sin conexión: se queda lo que hay
            }
        }
    }

    /** Borra todo el historial: aquí y, si está la lista, en Home Assistant (en todos los aparatos). */
    fun clear(context: Context) {
        entries.clear()
        save(context)
        val config = readHaConfig(context.applicationContext, profile) ?: return
        scope.launch {
            mutex.withLock {
                try {
                    val items = HomeAssistant.items(config, list(config))
                    HomeAssistant.removeAll(config, items.map { it.uid }, list(config))
                    Log.i("MisCanales", "Historial: borrado, también en Home Assistant (${items.size})")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // sin la lista o sin conexión: borrado aquí
                }
            }
        }
    }

    /** Sube (o pone al día) un vídeo del historial: un elemento por vídeo, «ID · título». */
    private suspend fun upload(config: HaConfig, items: List<HaItem>, entry: HistoryEntry) {
        val summary = "${entry.video.vid} · ${entry.video.title.take(80)}"
        val description = json.encodeToString(HistoryEntry.serializer(), entry.copy(pending = false))
        val same = items.filter { it.summary.take(11) == entry.video.vid }
        if (same.isEmpty()) {
            HomeAssistant.add(config, summary, description, list(config))
        } else {
            HomeAssistant.update(config, same.first().uid, summary, description, list(config))
            same.drop(1).forEach { HomeAssistant.remove(config, it.uid, list(config)) }
        }
    }

    /** Ya está en Home Assistant. */
    private fun sent(context: Context, entry: HistoryEntry) {
        val i = entries.indexOfFirst { it.video.vid == entry.video.vid && it.at == entry.at }
        if (i >= 0) {
            entries[i] = entries[i].copy(pending = false)
            save(context)
        }
    }

    /** Que la lista de Home Assistant no crezca sin fin: fuera los más antiguos. */
    private suspend fun prune(config: HaConfig, items: List<HaItem>) {
        if (items.size < MAX + 30) return
        val old = items.sortedBy { decode(it.description)?.at ?: 0L }.take(items.size - MAX)
        HomeAssistant.removeAll(config, old.map { it.uid }, list(config))
    }

    private fun decode(description: String): HistoryEntry? =
        runCatching { json.decodeFromString(HistoryEntry.serializer(), description.trim()) }.getOrNull()
}

/** El nombre del grupo del historial en que va algo abierto en [at]: "Hoy", "Ayer", "Esta semana"… */
fun historyGroup(at: Long, now: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0L -> "Hoy"
        days == 1L -> "Ayer"
        days < 7L -> "Esta semana"
        days < 31L -> "Este mes"
        else -> "Antes"
    }
}

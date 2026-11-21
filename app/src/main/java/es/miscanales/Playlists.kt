package es.miscanales

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Un vídeo dentro de una lista de reproducción. */
@Serializable
data class PlaylistEntry(
    /** El nombre de la lista. */
    val list: String,
    val video: Video,
    /** Cuándo se añadió (el orden dentro de la lista). */
    val at: Long,
    /** Guardado solo en este aparato, a falta de subirlo a Home Assistant. */
    val pending: Boolean = false,
)

/**
 * Las listas de reproducción: vídeos sueltos, de cualquier canal, guardados con un nombre (como las
 * categorías, una lista existe mientras tiene vídeos). Si en Home Assistant hay una lista de tareas
 * "Mis Canales listas" (todo.mis_canales_listas), viven ahí, un elemento por vídeo, y son las mismas
 * en el móvil y en las teles; si no, cada aparato tiene las suyas. Solo en la app de los mayores.
 * Cada perfil tiene las suyas ([of]); los demás perfiles, en su lista ("Mis Canales Montse listas").
 */
class Playlists private constructor(val profile: Profile) {
    companion object {
        /**
         * La lista fija para guardar vídeos y verlos luego: una lista más (se sincroniza igual), pero sale
         * siempre la primera y se añade con un toque desde las opciones del vídeo.
         */
        const val WATCH_LATER = "Ver más tarde"

        private const val TAG = "MisCanales"
        private const val KEY = "playlists"

        /** Cada cuánto se vuelven a traer de Home Assistant, como mucho. */
        private const val EVERY = 20_000L

        private val all = mutableMapOf<Profile, Playlists>()

        /** Las listas de [profile] (una sola vez cada una, aunque estén abiertos varios iconos). */
        @Synchronized
        fun of(profile: Profile): Playlists = all.getOrPut(profile) { Playlists(profile) }

        fun entity(config: HaConfig): String = config.entity + "_listas"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PlaylistEntry.serializer())
    private val mutex = Mutex()

    val entries = mutableStateListOf<PlaylistEntry>()

    /** Si se sincronizan: null sin probar, false si falta la lista en Home Assistant (o no hay Home Assistant). */
    var synced: Boolean? = null
        private set
    private var loaded = false
    private var pulledAt = 0L

    /** Los nombres de las listas, por orden alfabético (sin «Ver más tarde», que va aparte). */
    val names: List<String> get() = entries.map { it.list }.distinct().filter { it != WATCH_LATER }.sortedBy { it.lowercase() }

    /** Las listas para elegir al guardar un vídeo: «Ver más tarde» (aunque esté vacía) y las demás. */
    val choices: List<String> get() = listOf(WATCH_LATER) + names

    /** Si [vid] está en «Ver más tarde». */
    fun inWatchLater(vid: String): Boolean = entries.any { it.list == WATCH_LATER && it.video.vid == vid }

    /** Lo pone en «Ver más tarde», o lo quita si ya estaba. */
    suspend fun toggleWatchLater(context: Context, video: Video) {
        if (inWatchLater(video.vid)) remove(context, WATCH_LATER, video.vid) else add(context, WATCH_LATER, video)
    }

    /** Quita de una lista los vídeos ya vistos ([watched]). */
    suspend fun removeWatched(context: Context, name: String, watched: (String) -> Boolean) {
        videos(name).filter { watched(it.vid) }.forEach { remove(context, name, it.vid) }
    }

    /** Los vídeos de una lista, en el orden en que se añadieron. */
    fun videos(name: String): List<Video> = entries.filter { it.list == name }.sortedBy { it.at }.map { it.video }

    /** Las listas en las que está un vídeo. */
    fun listsOf(vid: String): Set<String> = entries.filter { it.video.vid == vid }.map { it.list }.toSet()

    /** Lee las guardadas en el aparato (una vez). */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = context.getSharedPreferences(profile.prefs, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching { json.decodeFromString(serializer, saved) }.getOrNull()?.let { entries.addAll(it) }
    }

    private fun save(context: Context) {
        context.getSharedPreferences(profile.prefs, Context.MODE_PRIVATE).edit { putString(KEY, json.encodeToString(serializer, entries.toList())) }
    }

    /** El nombre limpio; si ya hay una lista que se llama igual con otras mayúsculas, ese. */
    fun clean(name: String): String? {
        val tidy = name.trim().replace(Regex("\\s+"), " ").ifEmpty { return null }
        return choices.firstOrNull { it.equals(tidy, ignoreCase = true) } ?: tidy
    }

    /** Trae las listas de Home Assistant (y sube las que se guardaron solo aquí). Sin Home Assistant no hace nada. */
    suspend fun pull(context: Context, force: Boolean = false) {
        load(context)
        val config = readHaConfig(context, profile)
        if (config == null) {
            synced = false
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - pulledAt < EVERY) return
        pulledAt = now
        mutex.withLock {
            try {
                val list = entity(config)
                val remote = HomeAssistant.items(config, list).mapNotNull { decode(it.description) }.toMutableList()
                // lo guardado solo en este aparato (sin la lista en Home Assistant, o sin conexión), arriba
                for (entry in entries.filter { it.pending }) {
                    if (remote.none { it.list == entry.list && it.video.vid == entry.video.vid }) {
                        val clear = entry.copy(pending = false)
                        HomeAssistant.add(config, summary(clear), encode(clear), list)
                        remote += clear
                    }
                }
                entries.clear()
                entries.addAll(remote.distinctBy { it.list to it.video.vid })
                save(context)
                synced = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: HaError) {
                // no está la lista "Mis Canales listas": cada aparato, las suyas
                Log.i(TAG, "Listas: sin sincronizar (${e.message})")
                synced = false
            } catch (e: Exception) {
                // sin conexión: se queda lo que hay
            }
        }
    }

    /** Añade [video] a la lista [name] (nueva, si no existe). Sin conexión con Home Assistant, se queda aquí hasta que la haya. */
    suspend fun add(context: Context, name: String, video: Video) {
        load(context)
        val list = clean(name) ?: return
        if (entries.any { it.list == list && it.video.vid == video.vid }) return
        // (sin la descripción, que puede ser muy larga)
        var entry = PlaylistEntry(list, video.copy(description = ""), System.currentTimeMillis())
        mutex.withLock {
            val config = remote(context)
            if (config != null) {
                try {
                    HomeAssistant.add(config, summary(entry), encode(entry), entity(config))
                    synced = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HaError) {
                    // no está la lista en Home Assistant: aquí, y se subirá cuando esté
                    synced = false
                    entry = entry.copy(pending = true)
                } catch (e: Exception) {
                    // sin conexión: aquí, y se sube en cuanto la haya
                    entry = entry.copy(pending = true)
                    pulledAt = 0L
                }
            } else {
                entry = entry.copy(pending = true)
            }
            entries += entry
            save(context)
        }
        Log.i(TAG, "Listas: «${video.title.take(40)}» a «$list» (${videos(list).size})")
    }

    /**
     * Añade de una vez varios vídeos a la lista [name] (nueva, si no existe), en su orden: lo de guardar una
     * lista de YouTube como copia. [progress] dice cuántos van. Devuelve cuántos eran nuevos en la lista.
     */
    suspend fun addAll(context: Context, name: String, items: List<Video>, progress: (Int) -> Unit = {}): Int {
        load(context)
        val list = clean(name) ?: return 0
        val fresh = items.distinctBy { it.vid }.filter { video -> entries.none { it.list == list && it.video.vid == video.vid } }
        // detrás de los que ya tuviera la lista
        val base = maxOf(System.currentTimeMillis(), (entries.filter { it.list == list }.maxOfOrNull { it.at } ?: 0L) + 1)
        mutex.withLock {
            var config = remote(context)
            try {
                fresh.forEachIndexed { i, video ->
                    var entry = PlaylistEntry(list, video.copy(description = ""), base + i)
                    val haConfig = config
                    if (haConfig != null) {
                        try {
                            HomeAssistant.add(haConfig, summary(entry), encode(entry), entity(haConfig))
                            synced = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: HaError) {
                            // no está la lista en Home Assistant: aquí, y se subirán cuando esté
                            synced = false
                            config = null
                            entry = entry.copy(pending = true)
                        } catch (e: Exception) {
                            // sin conexión: aquí, y se suben en cuanto la haya
                            config = null
                            pulledAt = 0L
                            entry = entry.copy(pending = true)
                        }
                    } else {
                        entry = entry.copy(pending = true)
                    }
                    entries += entry
                    progress(i + 1)
                }
            } finally {
                save(context)
            }
        }
        Log.i(TAG, "Listas: ${fresh.size} vídeos de una lista de YouTube a «$list» (${videos(list).size})")
        return fresh.size
    }

    /** Quita un vídeo de una lista. */
    suspend fun remove(context: Context, name: String, vid: String) = change(context) { config ->
        if (config != null) {
            val list = entity(config)
            HomeAssistant.items(config, list)
                .filter { item -> decode(item.description)?.let { it.list == name && it.video.vid == vid } == true }
                .forEach { HomeAssistant.remove(config, it.uid, list) }
        }
        entries.removeAll { it.list == name && it.video.vid == vid }
    }

    /** Borra una lista entera. */
    suspend fun delete(context: Context, name: String) = change(context) { config ->
        if (config != null) {
            val list = entity(config)
            HomeAssistant.items(config, list)
                .filter { decode(it.description)?.list == name }
                .forEach { HomeAssistant.remove(config, it.uid, list) }
        }
        entries.removeAll { it.list == name }
    }

    /** Le cambia el nombre a una lista (si ya hay otra con ese nombre, se juntan). */
    suspend fun rename(context: Context, name: String, to: String) {
        val target = clean(to) ?: return
        if (target == name) return
        change(context) { config ->
            if (config != null) {
                val list = entity(config)
                for (item in HomeAssistant.items(config, list)) {
                    val entry = decode(item.description)?.takeIf { it.list == name } ?: continue
                    val moved = entry.copy(list = target)
                    HomeAssistant.update(config, item.uid, summary(moved), encode(moved), list)
                }
            }
            val moved = entries.filter { it.list == name }.map { it.copy(list = target) }
            entries.removeAll { it.list == name }
            entries.addAll(moved.filter { m -> entries.none { it.list == target && it.video.vid == m.video.vid } })
        }
    }

    /** Un cambio en Home Assistant (si se sincronizan) y aquí. Sin conexión, falla y no cambia nada. */
    private suspend fun change(context: Context, block: suspend (HaConfig?) -> Unit) {
        load(context)
        mutex.withLock {
            try {
                block(remote(context))
            } catch (e: HaError) {
                // sin la lista en Home Assistant: solo aquí
                synced = false
                block(null)
            }
            save(context)
        }
    }

    /** Home Assistant, si las listas van por él. */
    private fun remote(context: Context): HaConfig? = if (synced == false) null else readHaConfig(context, profile)

    private fun summary(entry: PlaylistEntry): String = "${entry.list} · ${entry.video.title.take(80)}"

    private fun encode(entry: PlaylistEntry): String = json.encodeToString(PlaylistEntry.serializer(), entry)

    private fun decode(description: String): PlaylistEntry? =
        runCatching { json.decodeFromString(PlaylistEntry.serializer(), description.trim()) }.getOrNull()?.copy(pending = false)
}

package es.miscanales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Añadir, quitar y sincronizar canales, igual en la tele que en el móvil.
 * Con Home Assistant configurado, su lista de tareas manda: cada carga la trae, y añadir o quitar
 * un canal se hace primero allí (si HA no responde, no se hace, para no tener listas distintas).
 */
class Library(val store: Store, val feed: Feed) {
    /** Si el progreso se sincroniza: null sin probar, false si falta la lista en Home Assistant. */
    var progressSynced by mutableStateOf<Boolean?>(null)
        private set

    /** El último error al sincronizar, o null si fue bien. */
    var syncError by mutableStateOf<String?>(null)
        private set
    var syncedAt by mutableStateOf(0L)
        private set

    private val mutex = Mutex()

    init {
        // antes de bajar los vídeos, la lista de canales al día
        feed.beforeLoad = { pull() }
    }

    /** Busca el canal de lo que se ha escrito o compartido y lo añade. Devuelve el canal y si ya estaba. */
    suspend fun add(input: String): Pair<Channel, Boolean> = add(YouTube.resolve(input))

    /** Añade un canal ya conocido (uno que ha salido al buscar). */
    suspend fun add(channel: Channel): Pair<Channel, Boolean> {
        val already = store.has(channel.id)
        val config = store.ha
        if (config != null && !already) HomeAssistant.add(config, channel.name, describe(channel))
        store.add(channel)
        feed.reload()
        return channel to already
    }

    suspend fun remove(id: String) {
        val config = store.ha
        if (config != null) {
            HomeAssistant.items(config).filter { idOf(it) == id }.forEach { HomeAssistant.remove(config, it.uid) }
        }
        store.remove(id)
        feed.drop(id)
    }

    /** Trae la lista de Home Assistant y deja los canales de la app igual. */
    suspend fun pull() {
        val config = store.ha
        if (config == null) {
            syncError = null
            return
        }
        try {
            mutex.withLock {
                val channels = mutableListOf<Channel>()
                for (item in HomeAssistant.items(config)) {
                    val id = idOf(item)
                    if (id != null) {
                        val old = store.channel(id)
                        channels += Channel(
                            id,
                            item.summary.ifBlank { old?.name ?: id },
                            avatarOf(item) ?: old?.avatar,
                            old?.addedAt ?: System.currentTimeMillis(),
                            kids = kidsOf(item),
                        )
                    } else if (item.summary.isNotBlank()) {
                        // escrito a mano en Home Assistant (@nombre o un enlace): se busca y se completa
                        val found = try {
                            YouTube.resolve(item.summary)
                        } catch (e: IOException) {
                            continue
                        }
                        HomeAssistant.update(config, item.uid, found.name, describe(found))
                        channels += store.channel(found.id)?.let { found.copy(addedAt = it.addedAt) } ?: found
                    }
                }
                store.replaceChannels(channels.distinctBy { it.id })
            }
            syncError = null
            syncedAt = System.currentTimeMillis()
            // por dónde vas en cada vídeo, de todos los aparatos
            try {
                store.mergeProgress(ProgressSync.fetch(config))
                progressSynced = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progressSynced = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            syncError = e.message ?: e.javaClass.simpleName
        }
    }

    /** Prueba la configuración, sube a Home Assistant los canales que falten allí, la guarda y sincroniza. */
    suspend fun connect(config: HaConfig): String {
        // si no responde o el token no vale, esto falla y no se guarda nada
        val inHa = HomeAssistant.items(config).mapNotNull(::idOf).toSet()
        val missing = store.channels.filter { it.id !in inHa }
        for (channel in missing) HomeAssistant.add(config, channel.name, describe(channel))
        store.saveHa(config)
        pull()
        feed.reload()
        val uploaded = if (missing.isEmpty()) "" else " He subido ${missing.size} ${if (missing.size == 1) "canal" else "canales"}."
        return "Conectado con Home Assistant.$uploaded Hay ${store.channels.size} en la lista."
    }

    /** Marca o desmarca un canal "para niños", aquí y en Home Assistant (así sale igual en la tele). */
    suspend fun setKids(id: String, value: Boolean) {
        val channel = store.channel(id) ?: return
        val updated = channel.copy(kids = value)
        val config = store.ha
        if (config != null) {
            HomeAssistant.items(config).filter { idOf(it) == id }.forEach {
                HomeAssistant.update(config, it.uid, it.summary.ifBlank { updated.name }, describe(updated))
            }
        }
        store.setKids(id, value)
    }

    /** Marca un vídeo como visto o no visto aquí y en los demás aparatos (lo de los niños, solo aquí). */
    fun setWatched(video: Video, value: Boolean) {
        store.setWatched(video.vid, value)
        if (store.kids) {
            if (value) {
                Progresses.put(store.appContextForSync(), mapOf(video.vid to Progress(1, 1, System.currentTimeMillis())), kids = true)
                store.reloadProgress()
            } else {
                store.forgetProgress(video.vid)
            }
            return
        }
        if (value) {
            val done = Progress(1, 1, System.currentTimeMillis())
            Progresses.put(store.appContextForSync(), mapOf(video.vid to done))
            store.reloadProgress()
            ProgressSync.push(store.appContextForSync(), video.vid, video.title, done)
        } else {
            store.forgetProgress(video.vid)
            ProgressSync.forget(store.appContextForSync(), video.vid)
        }
    }

    /** Deja de sincronizar. Los canales se quedan en este aparato. */
    fun disconnect() {
        store.saveHa(null)
        syncError = null
    }

    companion object {
        /** En la descripción del elemento: el ID del canal, debajo su foto y, si es para niños, "niños". */
        fun describe(channel: Channel): String =
            listOfNotNull(channel.id, channel.avatar, if (channel.kids) KIDS_MARK else null).joinToString("\n")

        private const val KIDS_MARK = "niños"

        fun kidsOf(item: HaItem): Boolean = item.description.lines().any { it.trim().equals(KIDS_MARK, ignoreCase = true) }

        fun idOf(item: HaItem): String? = Regex("UC[\\w-]{22}").find(item.description)?.value

        fun avatarOf(item: HaItem): String? = item.description.lines().map { it.trim() }.firstOrNull { it.startsWith("http") }

        /** El mensaje para cuando no se ha podido añadir un canal. */
        fun addError(e: Exception): String = when (e) {
            is ChannelNotFound -> "No encuentro ese canal: ${e.message}. Escribe su @nombre tal como sale en YouTube, o pega su enlace."
            is HaError -> "No se ha podido guardar en Home Assistant: ${e.message}."
            is IOException -> "No se ha podido conectar (${e.message}). Mira la conexión a internet."
            else -> "Algo ha fallado (${e.javaClass.simpleName}: ${e.message})."
        }
    }
}

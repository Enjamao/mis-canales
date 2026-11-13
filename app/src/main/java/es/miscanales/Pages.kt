package es.miscanales

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Una pantalla de la pila. Guarda su scroll y el elemento que tenía el foco mientras miras otra,
 * para que Atrás te deje en el mismo sitio.
 */
sealed class Page {
    /** La clave del último elemento con foco; null es el primero de la página. */
    var focus: String? = null
    val focusRequester = FocusRequester()

    /** Se pone al volver a la página, y se quita cuando el foco ya está en [focus]. */
    var needsFocus = true
}

/** Qué vídeos enseña Inicio. */
sealed interface Filter {
    data object All : Filter
    data object New : Filter
    data object Continue : Filter
    data class OneChannel(val id: String) : Filter

    /** Todos los vídeos de los canales de una categoría (null: los que no tienen). */
    data class Category(val name: String?) : Filter
}

class HomePage : Page() {
    var filter by mutableStateOf<Filter>(Filter.All)
        private set
    var grid = LazyGridState()
        private set

    /** En la tele, con categorías, Inicio va por filas: por dónde va cada una. */
    val rows = mutableMapOf<String, LazyListState>()

    fun show(filter: Filter) {
        grid = LazyGridState()
        rows.clear()
        this.filter = filter
    }
}

/** Elegir la categoría de un canal en la tele. */
class CategoryPage(val channelId: String) : Page() {
    var text by mutableStateOf("")
    var busy by mutableStateOf(false)
}

class ChannelsPage : PinPage() {
    val list = LazyListState()
}

class AddChannelPage : Page() {
    var query by mutableStateOf("")
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
}

/** Los vídeos de un canal, con el botón para quitarlo. */
class ChannelPage(val channelId: String) : Page() {
    val grid = LazyGridState()
    var confirmRemove by mutableStateOf(false)
}

/** El PIN que se está pidiendo en una pantalla de la tele (o null). */
sealed class PinPage : Page() {
    var pin by mutableStateOf<PinFlow?>(null)
}

/** El buscador (lo buscado y los resultados están en [Navigator.search]). */
class SearchPage : Page() {
    val grid = LazyGridState()
}

/** Los vídeos de un canal que ha salido al buscar (o que no es tuyo), con el botón para añadirlo. */
class FoundChannelPage(val found: FoundChannel) : Page() {
    val grid = LazyGridState()
}

/** La ficha de un vídeo: descripción y botones. */
class DetailsPage(val video: Video) : Page()

/**
 * Los últimos vídeos de todos tus canales, recién bajados de YouTube.
 * Se guardan en [dir], para enseñarlos nada más abrir la app y cuando YouTube no responde.
 */
class Feed(private val store: Store, dir: File) {
    private val cache = File(dir, "videos.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val videoList = ListSerializer(Video.serializer())

    var videos by mutableStateOf(
        runCatching { json.decodeFromString(videoList, cache.readText()) }.getOrDefault(emptyList())
            .filter { store.has(it.channelId) },
    )
        private set
    var loading by mutableStateOf(false)
        private set

    /** Los canales que no se pudieron cargar la última vez. */
    var failed by mutableStateOf<List<Channel>>(emptyList())
        private set
    var error by mutableStateOf<Exception?>(null)
        private set
    var loadedAt by mutableStateOf(0L)
        private set

    // las cargas siguen aunque cambies de pantalla
    private val scope = MainScope()

    /** Lo que se hace antes de cada carga: traer la lista de canales de Home Assistant. */
    var beforeLoad: (suspend () -> Unit)? = null

    /** Vuelve a bajar los feeds de todos los canales. */
    fun reload() {
        scope.launch { refresh() }
    }

    /** Vuelve a bajar los feeds si hace más de 15 minutos de la última vez. Devuelve si lo ha hecho. */
    fun reloadIfStale(): Boolean {
        if (System.currentTimeMillis() - loadedAt <= STALE) return false
        reload()
        return true
    }

    // cuándo se escribió por última vez lo guardado que se tiene leído
    private var cacheStamp = cache.lastModified()

    /** Vuelve a leer los vídeos guardados, si los ha bajado mientras tanto el otro icono (Kids o los mayores). */
    fun reloadCache() {
        if (loading || cache.lastModified() == cacheStamp) return
        cacheStamp = cache.lastModified()
        val saved = runCatching { json.decodeFromString(videoList, cache.readText()) }.getOrNull() ?: return
        videos = saved.filter { store.has(it.channelId) }
    }

    private var again = false

    private suspend fun refresh() {
        // si ya está cargando (por ejemplo, al añadir un canal), carga otra vez al acabar
        if (loading) {
            again = true
            return
        }
        loading = true
        try {
            do {
                again = false
                load()
            } while (again)
        } finally {
            loading = false
        }
    }

    private suspend fun load() {
        beforeLoad?.invoke()
        val channels = store.channels.toList()
        error = null
        if (channels.isEmpty()) {
            videos = emptyList()
            failed = emptyList()
            return
        }
        try {
            val (fresh, bad) = YouTube.everything(channels, store.hideShorts)
            // de los canales que han fallado, se quedan los últimos vídeos que se cargaron
            val badIds = bad.map { it.id }.toSet()
            val kept = videos.filter { it.channelId in badIds }
            videos = (fresh + kept)
                .distinctBy { it.vid }
                .sortedByDescending { it.published }
                // un canal que se ha quitado mientras cargaba ya no sale
                .filter { store.has(it.channelId) }
            failed = bad
            loadedAt = System.currentTimeMillis()
            save()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e
        }
    }

    /** Quita ya los vídeos de un canal, sin esperar a la próxima carga. */
    fun drop(channelId: String) {
        videos = videos.filter { it.channelId != channelId }
        failed = failed.filter { it.id != channelId }
        scope.launch { save() }
    }

    private suspend fun save() {
        val list = videos
        withContext(Dispatchers.IO) {
            runCatching { cache.writeText(json.encodeToString(videoList, list)) }
        }
        cacheStamp = cache.lastModified()
    }

    private companion object {
        const val STALE = 15L * 60 * 1000
    }
}

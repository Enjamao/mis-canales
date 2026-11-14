package es.miscanales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Dónde se busca. */
enum class SearchMode(val label: String) {
    Mine("Mis canales"),
    YouTube("Todo YouTube"),
}

/**
 * El buscador: lo escrito, dónde se busca y los resultados. Vive mientras la app está abierta, así
 * que al cambiar de pestaña (o de pantalla en la tele) y volver, siguen ahí.
 */
class SearchState {
    var query by mutableStateOf("")
    var mode by mutableStateOf(SearchMode.Mine)

    /** Lo buscado cuyos resultados se ven ("" si aún no se ha buscado nada). */
    var searched by mutableStateOf("")
        private set
    var searchedMode by mutableStateOf(SearchMode.Mine)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var channels by mutableStateOf<List<FoundChannel>>(emptyList())
        private set
    var videos by mutableStateOf<List<Video>>(emptyList())
        private set
    var more by mutableStateOf<String?>(null)
        private set

    /** El canal (de los resultados) cuyos vídeos se están mirando, antes de decidir si añadirlo. */
    var preview by mutableStateOf<FoundChannel?>(null)

    private val scope = MainScope()
    private var job: Job? = null

    fun run(store: Store, feed: Feed) {
        val text = query.trim()
        if (text.isEmpty()) return
        job?.cancel()
        val where = mode
        searched = text
        searchedMode = where
        preview = null
        error = null
        channels = emptyList()
        more = null
        loading = true
        // en tus canales, lo que ya está cargado sale al momento
        // (solo los canales de Inicio: en la de los mayores, sin los de niños salvo que se quiera)
        val shown = store.shownChannels
        val shownIds = shown.map { it.id }.toSet()
        val mine = feed.videos.filter { it.channelId in shownIds }
        videos = if (where == SearchMode.Mine) mine.filter { Search.matches(it, text) } else emptyList()
        job = scope.launch {
            try {
                if (where == SearchMode.YouTube) {
                    val page = Search.youtube(text, store.hideShorts)
                    channels = page.channels
                    videos = page.videos
                    more = page.more
                } else {
                    videos = Search.mine(text, shown, mine, store.hideShorts)
                }
                if (videos.isEmpty() && channels.isEmpty()) error = "No hay resultados para «$text»."
                loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se ha podido buscar (${e.message}). Mira la conexión a internet."
                loading = false
            }
        }
    }

    /** La página siguiente de resultados de todo YouTube. */
    fun loadMore(store: Store) {
        val token = more ?: return
        if (loadingMore || loading) return
        loadingMore = true
        scope.launch {
            try {
                val page = Search.youtubeMore(token, store.hideShorts)
                videos = (videos + page.videos).distinctBy { it.vid }
                channels = (channels + page.channels).distinctBy { it.id }
                more = page.more
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se han podido cargar más resultados (${e.message})."
            } finally {
                loadingMore = false
            }
        }
    }

    fun clear() {
        job?.cancel()
        query = ""
        searched = ""
        preview = null
        error = null
        channels = emptyList()
        videos = emptyList()
        more = null
        loading = false
    }
}

/**
 * Los vídeos anteriores de un canal (más allá de los últimos 15 del feed), de 30 en 30, sacados de
 * su pestaña Vídeos. Uno por canal, mientras la app está abierta.
 */
class OlderVideos(val channel: Channel) {
    var videos by mutableStateOf<List<Video>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** No quedan más (ya se ha llegado al primer vídeo del canal). */
    var done by mutableStateOf(false)
        private set
    private var next: String? = null
    private var started = false

    fun load() {
        if (loading || done) return
        loading = true
        error = null
        scope.launch {
            try {
                val page = Search.channelVideos(channel, if (started) next else null)
                started = true
                videos = (videos + page.videos).distinctBy { it.vid }
                next = page.more
                done = page.more == null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se han podido cargar más vídeos (${e.message})."
            } finally {
                loading = false
            }
        }
    }

    companion object {
        private val scope = MainScope()
        private val all = mutableMapOf<String, OlderVideos>()

        fun of(channel: Channel): OlderVideos = all.getOrPut(channel.id) { OlderVideos(channel) }
    }
}

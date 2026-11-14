package es.miscanales

import android.util.Log
import android.content.Context
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
        scope.launch { more() }
    }

    /** Carga la página siguiente y espera a tenerla. Devuelve si ha traído algo (no si ya estaba cargando, no quedan o falla). */
    suspend fun more(): Boolean {
        if (loading || done) return false
        loading = true
        error = null
        return try {
            val page = Search.channelVideos(channel, if (started) next else null)
            started = true
            videos = (videos + page.videos).distinctBy { it.vid }
            next = page.more
            done = page.more == null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "No se han podido cargar más vídeos (${e.message})."
            false
        } finally {
            loading = false
        }
    }

    companion object {
        private val scope = MainScope()
        private val all = mutableMapOf<String, OlderVideos>()

        fun of(channel: Channel): OlderVideos = all.getOrPut(channel.id) { OlderVideos(channel) }
    }
}

/** "Aleatorio": pone un vídeo al azar de una lista, con los demás mezclados detrás, seguidos. */
object Shuffle {
    /** Preparando la mezcla (cargando los vídeos anteriores del canal). */
    var busy by mutableStateOf(false)
        private set

    /** Con un canal solo, los vídeos entre los que se mezcla, si los tiene (de serie hay 15). */
    private const val WANTED = 100

    /** Las páginas de vídeos anteriores que se piden como mucho para llegar a [WANTED]. */
    private const val MAX_PAGES = 8

    /**
     * Mezcla [videos] y los pone. Con un [channel] solo, antes trae sus vídeos anteriores hasta tener
     * unos cien; si tiene menos, o YouTube no contesta, se mezcla con los que haya.
     */
    suspend fun play(context: Context, store: Store, videos: List<Video>, channel: Channel? = null) {
        if (busy) return
        busy = true
        try {
            var list = videos
            if (channel != null) {
                val older = OlderVideos.of(channel)
                var pages = 0
                while ((videos + older.videos).distinctBy { it.vid }.size < WANTED && !older.done && pages < MAX_PAGES) {
                    if (!older.more()) break
                    pages++
                }
                list = (videos + older.videos).distinctBy { it.vid }
            }
            if (list.isEmpty()) return
            val mixed = list.shuffled()
            Log.i("MisCanales", "Aleatorio: ${mixed.size} vídeos; empieza ${mixed.first().vid}")
            // siempre en el reproductor de la app y seguido, aunque la reproducción continua esté quitada
            playVideo(context, store, mixed.first(), queue = mixed.drop(1), autoNext = true, own = true)
        } finally {
            busy = false
        }
    }
}

package es.miscanales

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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
    data class OneChannel(val id: String) : Filter
}

class HomePage : Page() {
    var filter by mutableStateOf<Filter>(Filter.All)
        private set
    var grid = LazyGridState()
        private set

    fun show(filter: Filter) {
        grid = LazyGridState()
        this.filter = filter
    }
}

class ChannelsPage : Page() {
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

/** La ficha de un vídeo: descripción y botones. */
class DetailsPage(val video: Video) : Page()

/** Los últimos vídeos de todos tus canales, recién bajados de YouTube. */
class Feed(private val store: Store) {
    var videos by mutableStateOf<List<Video>>(emptyList())
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

    /** Vuelve a bajar los feeds de todos los canales. */
    fun reload() {
        scope.launch { refresh() }
    }

    /** Vuelve a bajar los feeds si hace más de 15 minutos de la última vez. */
    fun reloadIfStale() {
        if (System.currentTimeMillis() - loadedAt > STALE) reload()
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
        val channels = store.channels.toList()
        error = null
        if (channels.isEmpty()) {
            videos = emptyList()
            failed = emptyList()
            return
        }
        try {
            val (all, bad) = YouTube.everything(channels, store.hideShorts)
            // un canal que se ha quitado mientras cargaba ya no sale
            videos = all.filter { store.has(it.channelId) }
            failed = bad
            loadedAt = System.currentTimeMillis()
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
    }

    private companion object {
        const val STALE = 15L * 60 * 1000
    }
}

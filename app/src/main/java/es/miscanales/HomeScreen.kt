package es.miscanales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun HomeScreen(page: HomePage) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val nav = LocalNavigator.current
    if (store.channels.isEmpty()) {
        Welcome(page)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filter = page.filter
    // los canales de Inicio (sin los de niños, salvo que se quiera) y sus vídeos
    val shown = store.shownChannels
    val shownIds = shown.map { it.id }.toSet()
    val all = feed.videos.filter { it.channelId in shownIds }
    val categories = store.categories
    // "Otros": los canales sin categoría, cuando hay alguna
    val withOthers = categories.isNotEmpty() && shown.any { it.category == null }
    val chosen = page.category?.takeIf { it in categories || (it == OTHERS_KEY && withOthers) }
    val channels = (if (chosen == null) shown else store.channelsIn(categoryFor(chosen))).sortedBy { it.name.lowercase() }
    val inCategory = if (chosen == null) {
        all
    } else {
        val ids = channels.map { it.id }.toSet()
        all.filter { it.channelId in ids }
    }
    val newOnes = inCategory.filter { store.isNew(it) }
    // los empezados y sin acabar, el último que viste primero
    val continuing = inCategory.filter { store.progress[it.vid]?.started == true }
        .sortedByDescending { store.progress[it.vid]?.at ?: 0L }
    val videos = when (filter) {
        Filter.All -> inCategory
        Filter.New -> newOnes
        Filter.Continue -> continuing
        is Filter.OneChannel -> all.filter { it.channelId == filter.id }
    }
    // con categorías, "Todas" y "Todos" va por filas: una por categoría, y "Otros" con los canales sin ninguna
    val byRows = filter == Filter.All && chosen == null && categories.isNotEmpty()
    val rows = if (!byRows) {
        emptyList()
    } else {
        val names = categories + if (withOthers) listOf(OTHERS_KEY) else emptyList()
        names.map { it to all.inCategory(store, it) }.filter { it.second.isNotEmpty() }
    }
    VideoGrid(
        page,
        // por filas, los vídeos van dentro de las filas, arriba
        if (byRows) emptyList() else videos,
        page.grid,
        // al abrir, el foco en "Todos": arriba del todo, con el título a la vista (en la tele, la lista
        // lleva lo enfocado hacia el centro, y con el foco en el primer vídeo el título se salía)
        focusFirst = false,
        status = {
            val error = feed.error
            when {
                error != null && videos.isEmpty() -> Failed(retry = { feed.reload() })
                (feed.loading || feed.loadedAt == 0L) && videos.isEmpty() -> Status("Cargando tus canales…")
                videos.isNotEmpty() -> {}
                filter == Filter.New -> Status("No tienes vídeos nuevos sin ver. ¡Al día!")
                shown.isEmpty() -> Status("Tus canales son todos «Para niños»: salen en Mis Canales Kids. Para verlos también aquí, en Mis canales.")
                filter is Filter.OneChannel -> Status("Este canal no tiene vídeos recientes.")
                chosen != null -> Status("Los canales de esta categoría no tienen vídeos recientes.")
                feed.failed.isEmpty() -> Status("Tus canales no tienen vídeos recientes.")
                else -> {}
            }
            if (feed.failed.isNotEmpty()) {
                Status(
                    "YouTube no responde ahora para " + feed.failed.joinToString(", ") { it.name } +
                        ". Se ven los últimos vídeos que se cargaron; prueba a actualizar en un rato.",
                )
            }
        },
    ) {
        fullWidth("wordmark") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                // hay una versión nueva en GitHub: bajarla e instalarla
                if (Updater.available != null) {
                    Chip(
                        Updater.label(),
                        {
                            scope.launch {
                                Updater.install(context)
                                // lo que haya pasado (el permiso que falta, un error…)
                                Updater.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            }
                        },
                        Modifier.padding(end = 16.dp).restoreFocus(page, "update"),
                        selected = true,
                    )
                }
                val updated = if (feed.loading) "Actualizando…" else ago(feed.loadedAt)?.let { "Actualizado $it" }
                if (updated != null) Text(updated, style = Type.sub.copy(fontSize = 13.sp))
            }
        }
        if (categories.isNotEmpty()) {
            // arriba, las categorías (redondas), como en el móvil
            fullWidth("categories") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    item(key = "#all") {
                        Chip(
                            "Todas",
                            { page.showCategory(null) },
                            Modifier.restoreFocus(page, "category:#all"),
                            selected = chosen == null,
                            shape = CircleShape,
                        )
                    }
                    items(categories + if (withOthers) listOf(OTHERS_KEY) else emptyList(), key = { "cat:$it" }) { key ->
                        Chip(
                            categoryFor(key) ?: OTHERS,
                            { page.showCategory(key) },
                            Modifier.restoreFocus(page, "category:$key"),
                            selected = chosen == key,
                            shape = CircleShape,
                        )
                    }
                }
            }
        }
        fullWidth("filters") {
            // debajo, como la barra de píldoras de YouTube, pero solo con tus canales (los de la categoría)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            ) {
                item(key = "all") {
                    Chip(
                        "Todos",
                        { page.show(Filter.All) },
                        Modifier.restoreFocus(page, ALL, first = true),
                        selected = filter == Filter.All,
                    )
                }
                item(key = "new") {
                    Chip(
                        "Sin ver",
                        { page.show(Filter.New) },
                        Modifier.restoreFocus(page, "filter:new"),
                        selected = filter == Filter.New,
                        trailing = if (newOnes.isNotEmpty()) "${newOnes.size}" else null,
                    )
                }
                if (continuing.isNotEmpty() || filter == Filter.Continue) {
                    item(key = "continue") {
                        Chip(
                            "Seguir viendo",
                            { page.show(Filter.Continue) },
                            Modifier.restoreFocus(page, "filter:continue"),
                            selected = filter == Filter.Continue,
                            trailing = if (continuing.isNotEmpty()) "${continuing.size}" else null,
                        )
                    }
                }
                item(key = "refresh") {
                    Chip(
                        "Actualizar",
                        { feed.reload() },
                        Modifier.restoreFocus(page, "filter:refresh"),
                        icon = R.drawable.ic_refresh,
                    )
                }
                items(channels, key = { it.id }) { channel ->
                    Chip(
                        channel.name,
                        { page.show(Filter.OneChannel(channel.id)) },
                        Modifier.restoreFocus(page, "channel:${channel.id}"),
                        selected = filter == Filter.OneChannel(channel.id),
                        // mantener OK sobre un canal abre su página
                        onLongClick = { nav.open(ChannelPage(channel.id)) },
                    )
                }
            }
        }
        rows.forEach { (key, list) ->
            fullWidth("row:$key") {
                CategoryRow(page, key, list.take(ROW_SIZE))
            }
        }
    }
}

/** Una fila de Inicio en la tele: los últimos vídeos de una categoría y, al final, "Ver todos". */
@Composable
private fun CategoryRow(page: HomePage, key: String, videos: List<Video>) {
    val state = page.rows.getOrPut(key) { LazyListState() }
    Column {
        Text(
            categoryFor(key) ?: OTHERS,
            style = Type.pageTitle.copy(fontSize = 20.sp),
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
            itemsIndexed(videos, key = { _, video -> video.vid }) { _, video ->
                Box(Modifier.width(CARD_WIDTH)) {
                    VideoCard(video, Modifier.restoreFocus(page, video.vid), list = videos)
                }
            }
            item(key = "#all") {
                Box(Modifier.width(CARD_WIDTH).aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                    Chip(
                        "Ver todos",
                        { page.showCategory(key) },
                        Modifier.restoreFocus(page, "all:$key"),
                    )
                }
            }
        }
    }
}

/** Los vídeos de los canales de la categoría [key] ([OTHERS_KEY]: los de los canales sin ninguna). */
private fun List<Video>.inCategory(store: Store, key: String): List<Video> {
    val ids = store.channelsIn(categoryFor(key)).map { it.id }.toSet()
    return filter { it.channelId in ids }
}

private const val ALL = "filter:all"
private const val OTHERS = "Otros"

/** Los vídeos que salen en cada fila, antes de "Ver todos". */
private const val ROW_SIZE = 20

/** Lo ancho que es un vídeo en una fila: como en la cuadrícula de cuatro columnas. */
private val CARD_WIDTH = 196.dp

/** La primera vez, sin canales: un botón para añadir el primero. */
@Composable
private fun Welcome(page: HomePage) {
    val nav = LocalNavigator.current
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark()
        Spacer(Modifier.height(16.dp))
        Text(
            "Aquí solo salen los vídeos de los canales que tú elijas. Sin recomendaciones.",
            style = Type.body.copy(fontSize = 18.sp, lineHeight = 26.sp),
        )
        Spacer(Modifier.height(24.dp))
        Pill(
            "Añadir mi primer canal",
            { nav.go(Section.Add) },
            // con la misma clave que "Todos", para que el foco pase a los vídeos al añadir el primer canal
            Modifier.restoreFocus(page, ALL, first = true),
            icon = R.drawable.ic_add,
            selected = true,
        )
        Spacer(Modifier.height(12.dp))
        // o los mismos canales que en el móvil
        Pill("Traer mis canales de Home Assistant", { nav.open(HaPage()) }, Modifier.restoreFocus(page, "ha"))
    }
}

/** Cuatro columnas de vídeos, con una cabecera arriba y el estado de la carga abajo. */
@Composable
fun VideoGrid(
    page: Page,
    videos: List<Video>,
    state: LazyGridState,
    showChannel: Boolean = true,
    /** Si al abrir la página el foco va al primer vídeo (si no, lo coge alguien de la cabecera). */
    focusFirst: Boolean = true,
    status: @Composable () -> Unit,
    header: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header()
        itemsIndexed(videos, key = { _, video -> video.vid }) { i, video ->
            VideoCard(video, Modifier.restoreFocus(page, video.vid, first = focusFirst && i == 0), showChannel, list = videos)
        }
        fullWidth("status") { Column { status() } }
    }
}

fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = "#$key", span = { GridItemSpan(maxLineSpan) }) { content() }

/** Algo ha fallado al cargar, con un botón para volver a probar. */
@Composable
fun Failed(retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No se ha podido conectar con YouTube. Mira la conexión a internet de la tele.", style = Type.sub.copy(fontSize = 14.sp))
        Spacer(Modifier.height(12.dp))
        Chip("Volver a probar", retry)
    }
}

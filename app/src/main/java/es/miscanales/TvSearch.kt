package es.miscanales

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** El buscador de la tele: en tus canales (también los vídeos antiguos) o en todo YouTube. */
@Composable
fun SearchScreen(page: SearchPage) {
    val nav = LocalNavigator.current
    val store = LocalStore.current
    val feed = LocalFeed.current
    val keyboard = LocalSoftwareKeyboardController.current
    val search = nav.search

    fun go() {
        keyboard?.hide()
        search.run(store, feed)
    }

    VideoGrid(
        page,
        search.videos,
        page.grid,
        status = {
            when {
                search.loading -> Status(
                    when {
                        search.searchedMode == SearchMode.YouTube -> "Buscando en YouTube…"
                        search.videos.isEmpty() -> "Buscando en tus ${store.shownChannels.size} canales…"
                        else -> "Buscando también los vídeos antiguos de tus canales…"
                    },
                )
                search.error != null && search.videos.isEmpty() && search.channels.isEmpty() -> Status(search.error.orEmpty())
                search.searchedMode == SearchMode.YouTube && search.searched.isNotEmpty() -> MoreFooter(
                    page,
                    loading = search.loadingMore,
                    done = search.more == null,
                    label = "Más resultados",
                    doneText = "No hay más resultados.",
                    error = search.error,
                    onClick = { search.loadMore(store) },
                )
                else -> {}
            }
        },
    ) {
        fullWidth("head") {
            PageHead(
                "Buscar",
                if (search.mode == SearchMode.Mine) {
                    "Entre los vídeos de tus canales, también los antiguos."
                } else {
                    "Vídeos y canales de todo YouTube. Solo los resultados: ni recomendaciones ni vídeos relacionados."
                },
            )
        }
        fullWidth("box") {
            Row(
                Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextBox(
                    search.query,
                    { search.query = it },
                    placeholder = if (search.mode == SearchMode.Mine) "Buscar en mis canales" else "Buscar en YouTube",
                    onDone = ::go,
                    modifier = Modifier.restoreFocus(page, "field", first = search.searched.isEmpty()),
                )
                Pill("Buscar", ::go, Modifier.restoreFocus(page, "go"), icon = R.drawable.ic_search, selected = true)
                for (mode in SearchMode.entries) {
                    Pill(
                        mode.label,
                        {
                            search.mode = mode
                            if (search.query.isNotBlank()) go()
                        },
                        Modifier.restoreFocus(page, "mode-${mode.name}"),
                        selected = search.mode == mode,
                    )
                }
            }
        }
        if (search.searched.isEmpty()) {
            fullWidth("hint") {
                Text(
                    "Escribe con el teclado de la tele o con el del móvil (app Amazon Fire TV) y pulsa Buscar.",
                    style = Type.sub.copy(fontSize = 14.sp),
                )
            }
        }
        if (search.channels.isNotEmpty()) {
            fullWidth("channels") { Text("Canales", style = Type.title.copy(fontSize = 18.sp)) }
            for (found in search.channels) {
                fullWidth("c-${found.id}") {
                    TvFoundChannelRow(found, Modifier.widthIn(max = 900.dp).restoreFocus(page, "c-${found.id}"))
                }
            }
            fullWidth("videos") { Text("Vídeos", style = Type.title.copy(fontSize = 18.sp)) }
        }
    }
}

/** Un canal de los resultados: pulsarlo enseña sus vídeos y el botón de añadirlo. */
@Composable
private fun TvFoundChannelRow(found: FoundChannel, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    val store = LocalStore.current
    Surface(
        onClick = { nav.open(FoundChannelPage(found)) },
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Colors.bg2),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(Channel(found.id, found.name, found.avatar), 56.dp)
            Column(Modifier.weight(1f)) {
                Text(found.name, style = Type.title.copy(fontSize = 18.sp, lineHeight = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(found.handle, found.subscribers).filter { it.isNotEmpty() }.joinToString(" • "),
                    style = Type.sub.copy(fontSize = 13.sp),
                    maxLines = 1,
                )
            }
            Text(
                if (store.has(found.id)) "Ya es de tus canales" else "Pulsa para ver sus vídeos y añadirlo",
                style = Type.sub.copy(fontSize = 13.sp),
            )
        }
    }
}

/** Los vídeos de un canal que no es tuyo (de 30 en 30), con el botón para añadirlo. */
@Composable
fun FoundChannelScreen(page: FoundChannelPage) {
    val store = LocalStore.current
    val library = LocalLibrary.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val found = page.found
    val older = remember(found.id) { OlderVideos.of(found.toChannel()) }
    LaunchedEffect(found.id) { if (older.videos.isEmpty()) older.load() }
    var adding by remember { mutableStateOf(false) }
    val added = store.has(found.id)
    VideoGrid(
        page,
        older.videos,
        page.grid,
        showChannel = false,
        status = {
            MoreFooter(
                page,
                loading = older.loading,
                done = older.done,
                label = "Ver más",
                doneText = "Ya están todos sus vídeos.",
                error = older.error,
                onClick = { older.load() },
            )
        },
    ) {
        fullWidth("head") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(store.channel(found.id) ?: Channel(found.id, found.name, found.avatar), 80.dp)
                Column(Modifier.weight(1f)) {
                    Text(found.name, style = Type.pageTitle)
                    Text(
                        listOf(found.handle, found.subscribers).filter { it.isNotEmpty() }
                            .ifEmpty { listOf("Sus vídeos, del más nuevo al más viejo.") }.joinToString(" • "),
                        style = Type.sub.copy(fontSize = 14.sp),
                    )
                }
                Pill(
                    when {
                        added -> "Ya es de tus canales"
                        adding -> "Añadiendo…"
                        else -> "Añadir canal"
                    },
                    onClick@{
                        if (added || adding) return@onClick
                        adding = true
                        scope.launch {
                            try {
                                // sin foto (un canal sacado de un vídeo): se busca su página para tenerla
                                if (found.avatar == null) library.add(found.id) else library.add(found.toChannel())
                                Toast.makeText(context, "Añadido: ${found.name}", Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, Library.addError(e), Toast.LENGTH_LONG).show()
                            } finally {
                                adding = false
                            }
                        }
                    },
                    Modifier.restoreFocus(page, "add", first = older.videos.isEmpty()),
                    icon = if (added) R.drawable.ic_check else R.drawable.ic_add,
                    selected = !added,
                )
            }
        }
    }
}

/** El final de una lista que se puede alargar: el botón, el "cargando" o el "no hay más". */
@Composable
fun MoreFooter(page: Page, loading: Boolean, done: Boolean, label: String, doneText: String, error: String?, onClick: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        when {
            done -> Status(doneText)
            // mientras carga, el botón sigue ahí: si desapareciera, el foco saltaría arriba del todo
            else -> Pill(if (loading) "Cargando…" else label, { if (!loading) onClick() }, Modifier.restoreFocus(page, "more"))
        }
        if (error != null) Status(error)
    }
}

/** Una caja de texto con borde azul mientras escribes, como el buscador de YouTube. */
@Composable
private fun TextBox(value: String, onValue: (String) -> Unit, placeholder: String, onDone: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = Type.body.copy(fontSize = 18.sp),
        cursorBrush = SolidColor(Colors.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }, onDone = { onDone() }),
        modifier = modifier.widthIn(min = 280.dp, max = 380.dp).onFocusChanged { focused = it.isFocused },
        decorationBox = { field ->
            Box(
                Modifier.height(48.dp)
                    .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Colors.link else Colors.bg3), CircleShape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = Type.body.copy(fontSize = 18.sp, color = Colors.muted))
                field()
            }
        },
    )
}

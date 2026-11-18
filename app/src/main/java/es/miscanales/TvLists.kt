package es.miscanales

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Un cambio en las listas; si falla (sin conexión con Home Assistant), lo dice. */
internal fun CoroutineScope.playlistChange(context: android.content.Context, block: suspend () -> Unit) = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Toast.makeText(context, "No se ha podido guardar: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/** Las listas de reproducción, en la tele: una fila por lista. */
@Composable
fun ListsScreen(page: ListsPage) {
    val playlists = LocalStore.current.playlists
    val nav = LocalNavigator.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { playlists.pull(context.applicationContext) }
    val names = playlists.names
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "#head") {
            PageHead(
                "Listas",
                if (names.isEmpty()) {
                    "Aún no tienes ninguna. Para guardar un vídeo en una lista: mantén OK sobre él (o pulsa play/pausa) y «Añadir a lista»."
                } else {
                    "Tus vídeos guardados. Para añadir uno: mantén OK sobre él (o pulsa play/pausa) y «Añadir a lista»."
                },
            )
        }
        itemsIndexed(names, key = { _, name -> name }) { i, name ->
            val count = playlists.videos(name).size
            Chip(
                name,
                { nav.open(ListPage(name)) },
                Modifier.restoreFocus(page, "list:$name", first = i == 0),
                icon = R.drawable.ic_playlist,
                shape = CircleShape,
                trailing = if (count == 1) "1 vídeo" else "$count vídeos",
            )
        }
    }
}

/** Una lista: sus vídeos, en el orden en que se añadieron, y los botones de mezclar, renombrar y borrar. */
@Composable
fun ListScreen(page: ListPage) {
    val playlists = LocalStore.current.playlists
    val store = LocalStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val videos = playlists.videos(page.name)

    fun rename() {
        val to = page.text
        if (to.isBlank()) return
        keyboard?.hide()
        scope.playlistChange(context) {
            val old = page.name
            playlists.rename(context.applicationContext, old, to)
            page.name = playlists.clean(to) ?: old
            page.renaming = false
        }
    }

    VideoGrid(
        page,
        videos,
        page.grid,
        // el foco, en "Aleatorio": arriba, con el nombre a la vista
        focusFirst = false,
        status = {
            if (videos.isEmpty()) Status("Esta lista ya no tiene vídeos.")
        },
    ) {
        fullWidth("head") {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                PageHead(page.name, if (videos.size == 1) "1 vídeo" else "${videos.size} vídeos")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        if (Shuffle.busy) "Mezclando…" else "Aleatorio",
                        { scope.launch { Shuffle.play(context, store, videos) } },
                        Modifier.restoreFocus(page, "shuffle", first = true),
                        icon = R.drawable.ic_shuffle,
                    )
                    Pill(
                        "Cambiar el nombre",
                        {
                            page.text = page.name
                            page.renaming = !page.renaming
                        },
                        Modifier.restoreFocus(page, "rename"),
                    )
                    Pill(
                        if (page.confirmDelete) "¿Borrar la lista? Sí, borrar" else "Borrar la lista",
                        {
                            if (!page.confirmDelete) {
                                page.confirmDelete = true
                            } else {
                                scope.playlistChange(context) {
                                    playlists.delete(context.applicationContext, page.name)
                                    nav.back()
                                }
                            }
                        },
                        Modifier.restoreFocus(page, "delete"),
                        icon = R.drawable.ic_delete,
                    )
                }
                if (page.renaming) {
                    Row(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvTextField(
                            page.text,
                            { page.text = it },
                            "Nombre de la lista",
                            onDone = ::rename,
                            modifier = Modifier.restoreFocus(page, "name"),
                            capitalization = KeyboardCapitalization.Sentences,
                        )
                        Pill("Guardar", ::rename, Modifier.restoreFocus(page, "save"))
                    }
                }
                Text(
                    "Para quitar un vídeo de la lista: mantén OK sobre él (o pulsa play/pausa) y «Añadir a lista».",
                    style = Type.sub.copy(fontSize = 15.sp),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** Elegir en qué listas está un vídeo: las que ya hay (se marcan y desmarcan) o una nueva. */
@Composable
fun AddToListScreen(page: AddToListPage) {
    val playlists = LocalStore.current.playlists
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val video = page.video
    LaunchedEffect(Unit) { playlists.pull(context.applicationContext) }
    val names = playlists.names
    val inLists = playlists.listsOf(video.vid)

    fun create() {
        val name = page.text
        if (name.isBlank()) return
        keyboard?.hide()
        scope.playlistChange(context) {
            playlists.add(context.applicationContext, name, video)
            page.text = ""
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
        PageHead("Añadir a lista", video.title)
        if (names.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(names, key = { _, name -> name }) { i, name ->
                    val inside = name in inLists
                    Pill(
                        name,
                        {
                            scope.playlistChange(context) {
                                if (inside) {
                                    playlists.remove(context.applicationContext, name, video.vid)
                                } else {
                                    playlists.add(context.applicationContext, name, video)
                                }
                            }
                        },
                        Modifier.restoreFocus(page, "list:$name", first = i == 0),
                        icon = if (inside) R.drawable.ic_check else null,
                        selected = inside,
                    )
                }
            }
            Text(
                "Las marcadas ya lo tienen: OK lo añade o lo quita.",
                style = Type.sub.copy(fontSize = 15.sp),
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
        Text("Una lista nueva:", style = Type.sub.copy(fontSize = 16.sp), modifier = Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvTextField(
                page.text,
                { page.text = it },
                "Rock, Para ver luego…",
                onDone = ::create,
                modifier = Modifier.restoreFocus(page, "field"),
                capitalization = KeyboardCapitalization.Sentences,
            )
            // sin listas aún, el foco empieza aquí (en la caja saldría el teclado encima)
            Pill("Crear", ::create, Modifier.restoreFocus(page, "create", first = names.isEmpty()), icon = R.drawable.ic_add)
        }
    }
}

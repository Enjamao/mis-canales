package es.miscanales

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Lo que se abre como "lista" para el historial (no es una lista de verdad). */
private const val HISTORY = "#historial"

/**
 * Las listas de reproducción, en el móvil: arriba, «Ver más tarde» y el «Historial»; debajo, las tuyas. O
 * una abierta, con sus vídeos.
 */
@Composable
internal fun PhoneLists(onDetails: (Video) -> Unit, start: String? = null, onStarted: () -> Unit = {}) {
    val playlists = LocalStore.current.playlists
    val history = LocalStore.current.history
    val context = LocalContext.current
    // [start]: una lista recién guardada desde «Añadir», abierta ya
    var open by rememberSaveable { mutableStateOf(start) }
    LaunchedEffect(Unit) {
        onStarted()
        playlists.pull(context.applicationContext)
    }
    val names = playlists.names
    val name = open
    if (name == HISTORY) {
        PhoneHistory(onBack = { open = null }, onDetails = onDetails)
        return
    }
    if (name != null && (name == Playlists.WATCH_LATER || name in names)) {
        PhoneList(name, onBack = { open = null }, onRenamed = { open = it }, onDetails = onDetails)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "#head") {
            Text("Listas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
        }
        item(key = "#later") {
            val videos = playlists.videos(Playlists.WATCH_LATER)
            ListRow(Playlists.WATCH_LATER, count(videos.size), videos.firstOrNull(), R.drawable.ic_watch_later) {
                open = Playlists.WATCH_LATER
            }
        }
        item(key = "#history") {
            val videos = history.entries.map { it.video }
            ListRow("Historial", count(videos.size), videos.firstOrNull(), R.drawable.ic_history) { open = HISTORY }
        }
        item(key = "#mine") {
            Text("Tus listas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Hint(
                if (names.isEmpty()) {
                    "Aún no tienes ninguna. Para guardar un vídeo en una lista, mantenlo pulsado y toca «Añadir a lista»."
                } else {
                    "Para guardar un vídeo en una lista, mantenlo pulsado y toca «Añadir a lista»."
                },
            )
        }
        items(names, key = { it }) { list ->
            val videos = playlists.videos(list)
            ListRow(list, count(videos.size), videos.firstOrNull(), R.drawable.ic_playlist) { open = list }
        }
    }
}

private fun count(n: Int) = if (n == 1) "1 vídeo" else "$n vídeos"

/** Una fila de Listas: la miniatura del primer vídeo (o un icono, si no tiene), el nombre y cuántos. */
@Composable
private fun ListRow(title: String, subtitle: String, first: Video?, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(140.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp)).background(Colors.bg2),
            contentAlignment = Alignment.Center,
        ) {
            if (first != null) {
                Thumbnail(first)
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = Colors.muted, modifier = Modifier.size(32.dp))
            }
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Colors.muted)
        }
    }
}

/** El historial: lo que has abierto en este móvil, lo último primero, por días; y borrarlo. */
@Composable
private fun PhoneHistory(onBack: () -> Unit, onDetails: (Video) -> Unit) {
    val store = LocalStore.current
    val history = store.history
    val context = LocalContext.current
    var clearing by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val entries = history.entries.toList()
    val videos = entries.map { it.video }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "#head") {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Volver") }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Historial", style = MaterialTheme.typography.titleLarge)
                    Text(count(videos.size), style = MaterialTheme.typography.bodySmall, color = Colors.muted)
                }
                if (videos.isNotEmpty()) {
                    IconButton(onClick = { clearing = true }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Borrar el historial")
                    }
                }
            }
            Hint(
                if (videos.isEmpty()) "Aquí salen los vídeos que abras en este móvil." else "Lo que has abierto en este móvil, lo último primero.",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        var last: String? = null
        entries.forEach { entry ->
            val group = historyGroup(entry.at)
            if (group != last) {
                last = group
                item(key = "#group:$group") {
                    Text(group, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                }
            }
            item(key = entry.video.vid) {
                val video = entry.video
                PhoneVideo(video, onClick = { playVideo(context, store, video, queue = videos.after(video)) }, onLongClick = { onDetails(video) })
            }
        }
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("¿Borrar el historial?") },
            text = { Text("Solo se borra la lista de lo que has abierto en este móvil. Lo visto y por dónde ibas se queda.") },
            confirmButton = {
                TextButton(onClick = {
                    clearing = false
                    history.clear(context.applicationContext)
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancelar") } },
        )
    }
}

/** Una lista abierta: sus vídeos en el orden en que se añadieron, mezclarla, renombrarla y borrarla. */
@Composable
private fun PhoneList(name: String, onBack: () -> Unit, onRenamed: (String) -> Unit, onDetails: (Video) -> Unit) {
    val playlists = LocalStore.current.playlists
    val store = LocalStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videos = playlists.videos(name)
    val later = name == Playlists.WATCH_LATER
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "#head") {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Volver") }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (videos.size == 1) "1 vídeo" else "${videos.size} vídeos",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.muted,
                    )
                }
                if (videos.isNotEmpty()) {
                    IconButton(onClick = { deleting = true }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = if (later) "Vaciar" else "Borrar la lista")
                    }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { Shuffle.play(context, store, videos) } }) {
                    Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (Shuffle.busy) "Mezclando…" else "Aleatorio", modifier = Modifier.padding(start = 8.dp))
                }
                if (later) {
                    if (videos.any { store.isWatched(it.vid) }) {
                        OutlinedButton(onClick = {
                            scope.playlistChange(context) {
                                playlists.removeWatched(context.applicationContext, name) { store.isWatched(it) }
                            }
                        }) { Text("Quitar los vistos") }
                    }
                } else {
                    OutlinedButton(onClick = { renaming = true }) { Text("Cambiar el nombre") }
                }
            }
            Hint(
                when {
                    later && videos.isEmpty() -> "Para guardar un vídeo aquí, mantenlo pulsado y toca «Ver más tarde»."
                    later -> "Para quitar uno, mantenlo pulsado y toca «En Ver más tarde»."
                    else -> "Para quitar un vídeo de la lista, mantenlo pulsado y toca el botón de las listas."
                },
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(videos, key = { it.vid }) { video ->
            PhoneVideo(video, onClick = { playVideo(context, store, video, queue = videos.after(video)) }, onLongClick = { onDetails(video) })
        }
    }
    if (renaming) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Cambiar el nombre") },
            text = {
                OutlinedTextField(
                    text,
                    { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        renaming = false
                        scope.playlistChange(context) {
                            playlists.rename(context.applicationContext, name, text)
                            playlists.clean(text)?.let(onRenamed)
                        }
                    },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(if (later) "¿Vaciar «Ver más tarde»?" else "¿Borrar la lista «$name»?") },
            text = { Text("Los vídeos siguen en YouTube y en tus canales; solo se quitan de aquí.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.playlistChange(context) { playlists.delete(context.applicationContext, name) }
                }) { Text(if (later) "Vaciar" else "Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
        )
    }
}

/** Las listas de un vídeo: marcar y desmarcar las que ya hay, o crear una nueva con él. */
@Composable
internal fun PlaylistDialog(video: Video, onDismiss: () -> Unit) {
    val playlists = LocalStore.current.playlists
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val inLists = playlists.listsOf(video.vid)

    fun create() {
        val name = text
        if (name.isBlank()) return
        text = ""
        scope.playlistChange(context) { playlists.add(context.applicationContext, name, video) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a lista") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (name in playlists.choices) {
                    val inside = name in inLists
                    val toggle = {
                        scope.playlistChange(context) {
                            if (inside) {
                                playlists.remove(context.applicationContext, name, video.vid)
                            } else {
                                playlists.add(context.applicationContext, name, video)
                            }
                        }
                        Unit
                    }
                    Row(Modifier.fillMaxWidth().clickable(onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = inside, onCheckedChange = { toggle() })
                        Text(name)
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Lista nueva") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) create() else onDismiss() }) { Text(if (text.isNotBlank()) "Crear" else "Hecho") } },
        dismissButton = { if (text.isNotBlank()) TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

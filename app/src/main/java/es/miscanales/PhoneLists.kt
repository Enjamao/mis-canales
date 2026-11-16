package es.miscanales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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

/** Las listas de reproducción, en el móvil: todas, o una abierta con sus vídeos. */
@Composable
internal fun PhoneLists(onDetails: (Video) -> Unit) {
    val playlists = LocalStore.current.playlists
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { playlists.pull(context.applicationContext) }
    val names = playlists.names
    val name = open?.takeIf { it in names }
    if (name != null) {
        PhoneList(name, onBack = { open = null }, onRenamed = { open = it }, onDetails = onDetails)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "#head") {
            Text("Listas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
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
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = list },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(140.dp).clip(RoundedCornerShape(12.dp))) { videos.firstOrNull()?.let { Thumbnail(it) } }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(list, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (videos.size == 1) "1 vídeo" else "${videos.size} vídeos",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.muted,
                    )
                }
            }
        }
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
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
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
                IconButton(onClick = { deleting = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Borrar la lista") }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { Shuffle.play(context, store, videos) } }) {
                    Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (Shuffle.busy) "Mezclando…" else "Aleatorio", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { renaming = true }) { Text("Cambiar el nombre") }
            }
            Hint("Para quitar un vídeo de la lista, mantenlo pulsado y toca el botón de las listas.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
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
            title = { Text("¿Borrar la lista «$name»?") },
            text = { Text("Los vídeos siguen en YouTube y en tus canales; solo se borra la lista.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.playlistChange(context) { playlists.delete(context.applicationContext, name) }
                }) { Text("Borrar") }
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
                for (name in playlists.names) {
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

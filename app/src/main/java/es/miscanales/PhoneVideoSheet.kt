@file:OptIn(ExperimentalMaterial3Api::class)

package es.miscanales

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Las opciones de un vídeo en el móvil (al mantenerlo pulsado o con su botón ⋮): un panel que sube desde
 * abajo, con una línea grande y su icono por opción, y la descripción debajo.
 */
@Composable
internal fun VideoActions(video: Video, onDismiss: () -> Unit, onChannel: () -> Unit) {
    val store = LocalStore.current
    val playlists = store.playlists
    val library = LocalLibrary.current
    val context = LocalContext.current
    val watched = store.isWatched(video.vid)
    val later = playlists.inWatchLater(video.vid)
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var listing by remember { mutableStateOf(false) }
    var wholeDescription by remember { mutableStateOf(false) }
    if (listing) PlaylistDialog(video, onDismiss = { listing = false })
    // cerrar con la animación de bajar, y luego quitarlo
    fun close() {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(video.channelName, fullDate(video.published)).filter { it.isNotEmpty() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            // si lo habías empezado, el reproductor sigue por donde ibas
            val progress = store.progress[video.vid]?.takeIf { it.started }
            SheetRow(R.drawable.ic_play_arrow_filled, "Ver", progress?.let { "Sigue por donde ibas (" + clock(it.pos) + ")" }) {
                onDismiss()
                playVideo(context, store, video)
            }
            // «Ver más tarde», con un toque (y otra vez lo quita); el panel sigue abierto
            SheetRow(
                if (later) R.drawable.ic_check else R.drawable.ic_watch_later,
                if (later) "En Ver más tarde" else "Ver más tarde",
                if (later) "Toca para quitarlo" else null,
            ) {
                scope.playlistChange(context) { playlists.toggleWatchLater(context.applicationContext, video) }
            }
            // en qué otras listas de reproducción está
            val inLists = playlists.listsOf(video.vid) - Playlists.WATCH_LATER
            SheetRow(
                R.drawable.ic_playlist,
                "Añadir a lista",
                if (inLists.isEmpty()) null else "Está en: " + inLists.sortedBy { it.lowercase() }.joinToString(", "),
            ) { listing = true }
            val config = store.ha
            if (config != null) {
                SheetRow(R.drawable.ic_tv, "Ver en la tele") {
                    onDismiss()
                    store.setWatched(video.vid, true)
                    // desde donde ibas, si lo habías empezado y no acabado
                    val start = store.progress[video.vid]?.takeIf { it.started && !it.finished }?.pos ?: 0
                    Tele.sendAndTell(context, config, video.vid, start, store.profile) { tv -> store.saveHa(config.copy(tv = tv)) }
                }
            }
            SheetRow(
                if (watched) R.drawable.ic_remove_done else R.drawable.ic_task_alt,
                if (watched) "Marcar como no visto" else "Marcar como visto",
            ) {
                library.setWatched(video, !watched)
                close()
            }
            if (store.has(video.channelId)) {
                SheetRow(R.drawable.ic_account_circle, "Ir al canal", video.channelName.ifEmpty { null }) { onChannel() }
            } else if (video.channelId.isNotBlank()) {
                // un vídeo de la búsqueda, de un canal que no es tuyo
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_account_circle), contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(
                        "Su canal no está en los tuyos",
                        color = Colors.muted,
                        modifier = Modifier.padding(start = 20.dp).weight(1f),
                    )
                    AddChannelButton(FoundChannel(video.channelId, video.channelName, null, "", ""))
                }
            }
            SheetRow(R.drawable.ic_open_in_new, "Abrir en YouTube") {
                onDismiss()
                store.setWatched(video.vid, true)
                context.playOnYouTube(video.vid)
            }
            // la descripción: unas líneas, y entera al tocarla
            if (video.description.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    video.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (wholeDescription) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { wholeDescription = !wholeDescription }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
                if (!wholeDescription) {
                    Text(
                        "Toca la descripción para verla entera",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.muted,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

/** Una opción del panel: el icono, el texto y, si hace falta, una línea pequeña debajo. */
@Composable
private fun SheetRow(@DrawableRes icon: Int, text: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = if (detail == null) 16.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 20.dp).weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

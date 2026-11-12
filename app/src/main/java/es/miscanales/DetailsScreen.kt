package es.miscanales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** La ficha de un vídeo: la miniatura, la descripción y los botones de ver, visto e ir al canal. */
@Composable
fun DetailsScreen(page: DetailsPage) {
    val video = page.video
    val store = LocalStore.current
    val library = LocalLibrary.current
    val nav = LocalNavigator.current
    val watched = store.isWatched(video.vid)
    Row(
        Modifier.fillMaxSize().padding(start = 24.dp, end = 32.dp, top = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(Modifier.width(440.dp)) {
            Thumbnail(video, Modifier.clip(RoundedCornerShape(12.dp)).background(Colors.bg2))
            Row(
                Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(store.channel(video.channelId) ?: Channel(video.channelId, video.channelName), 40.dp)
                Column {
                    Text(video.channelName, style = Type.title.copy(fontSize = 16.sp))
                    Text(video.stats(), style = Type.sub)
                }
            }
            Text(fullDate(video.published), style = Type.sub, modifier = Modifier.padding(top = 8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(video.title, style = Type.pageTitle.copy(lineHeight = 32.sp), maxLines = 3)
            Row(Modifier.padding(top = 16.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill(
                    "Ver",
                    { nav.play(video) },
                    Modifier.restoreFocus(page, "play", first = true),
                    icon = R.drawable.ic_play_arrow_filled,
                    selected = true,
                )
                Pill(
                    if (watched) "Marcar como no visto" else "Marcar como visto",
                    { library.setWatched(video, !watched) },
                    Modifier.restoreFocus(page, "watched"),
                    icon = if (watched) null else R.drawable.ic_check,
                )
                Pill(
                    if (store.has(video.channelId)) "Ir al canal" else "Ver su canal",
                    {
                        // un vídeo de la búsqueda, de un canal que no es tuyo: sus vídeos y el botón de añadirlo
                        if (store.has(video.channelId)) {
                            nav.open(ChannelPage(video.channelId))
                        } else {
                            nav.open(FoundChannelPage(FoundChannel(video.channelId, video.channelName, null, "", "")))
                        }
                    },
                    Modifier.restoreFocus(page, "channel"),
                )
                Pill(
                    "Abrir en YouTube",
                    { nav.playOnYouTubeApp(video) },
                    Modifier.restoreFocus(page, "youtube"),
                )
            }
            Description(video.description)
        }
    }
}

/**
 * La descripción, en párrafos que se recorren con el mando (abajo y arriba),
 * y la lista se desplaza sola.
 */
@Composable
private fun Description(text: String) {
    val paragraphs = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    if (paragraphs.isEmpty()) {
        Text("Este vídeo no tiene descripción.", style = Type.sub.copy(fontSize = 14.sp))
        return
    }
    LazyColumn(state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(paragraphs.size) { i ->
            Surface(
                onClick = {},
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Colors.bg2),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(paragraphs[i], style = Type.body.copy(color = Colors.muted), modifier = Modifier.padding(8.dp))
            }
        }
    }
}

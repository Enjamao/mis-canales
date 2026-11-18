package es.miscanales

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/*
 * El panel «Canales» de Inicio: en vez de una pastilla por canal en la fila de los filtros (con muchos
 * canales, una tira larguísima), una sola pastilla que abre todos los canales en cuadrícula, con su foto.
 * En el móvil sale desde abajo (PhonePicker.kt); en la tele es una pantalla (aquí abajo).
 */

/** Un grupo de canales del panel: los de una categoría (con su título), o todos (sin título). */
class ChannelGroup(val title: String?, val channels: List<Channel>)

/**
 * Los canales del panel. Con una categoría elegida en Inicio, solo los suyos; si no, con categorías, uno
 * por categoría y "Otros" al final. En cada grupo, primero los que tienen vídeos sin ver, y por nombre.
 */
fun channelGroups(store: Store, category: String?, unseen: Map<String, Int>): List<ChannelGroup> {
    val categories = store.categories
    val withOthers = categories.isNotEmpty() && store.shownChannels.any { it.category == null }
    val chosen = category?.takeIf { it in categories || (it == OTHERS_KEY && withOthers) }
    fun sorted(list: List<Channel>) = list.sortedWith(compareBy({ (unseen[it.id] ?: 0) == 0 }, { it.name.lowercase() }))
    return when {
        chosen != null -> listOf(ChannelGroup(null, sorted(store.channelsIn(categoryFor(chosen)))))
        categories.isEmpty() -> listOf(ChannelGroup(null, sorted(store.shownChannels)))
        else -> (categories + if (withOthers) listOf(OTHERS_KEY) else emptyList())
            .map { ChannelGroup(categoryFor(it) ?: OTHERS_TITLE, sorted(store.channelsIn(categoryFor(it)))) }
            .filter { it.channels.isNotEmpty() }
    }
}

/** El título del panel: "Canales", o "Canales · Música" con una categoría elegida. */
fun pickerTitle(store: Store, category: String?): String {
    val categories = store.categories
    val name = when {
        category == null -> null
        category == OTHERS_KEY -> OTHERS_TITLE.takeIf { categories.isNotEmpty() }
        category in categories -> category
        else -> null
    }
    return if (name == null) "Canales" else "Canales · $name"
}

/** Cuántos vídeos sin ver tiene cada canal (de los que hay cargados). */
fun unseenByChannel(store: Store, videos: List<Video>): Map<String, Int> =
    videos.filter { store.isNew(it) }.groupingBy { it.channelId }.eachCount()

/** Lo que pone la pastilla de Inicio: "Canales", el nombre del elegido, o "3 canales". */
fun channelsLabel(store: Store, ids: Set<String>): String = when (ids.size) {
    0 -> "Canales"
    1 -> store.channel(ids.first())?.name ?: "1 canal"
    else -> "${ids.size} canales"
}

private const val OTHERS_TITLE = "Otros"

/**
 * La foto del canal para el panel: con el número de vídeos sin ver arriba a la derecha y, si está
 * elegido, una marca abajo a la derecha.
 */
@Composable
fun PickerAvatar(channel: Channel, unseen: Int, selected: Boolean, size: Dp) {
    val accent = LocalStore.current.profile.accent
    // sin textos para la accesibilidad (la inicial de la foto, el número): el nombre va debajo
    Box(Modifier.clearAndSetSemantics { }) {
        Avatar(channel, size)
        if (unseen > 0) {
            Badge(if (unseen > 99) "99+" else "$unseen", Colors.accent, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(size * .4f)
                    .background(Colors.bg, CircleShape).padding(2.dp).background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Colors.text, modifier = Modifier.size(size * .26f))
            }
        }
    }
}

/**
 * El panel en la tele: OK en un canal lo elige solo y vuelve a Inicio; mantener OK (o play/pausa, con el
 * mando de la tele) lo añade a los elegidos o lo quita, y se sigue en el panel. Atrás vuelve a Inicio.
 */
@Composable
fun ChannelPickerScreen(page: ChannelPickerPage) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val nav = LocalNavigator.current
    val home = page.home
    val ids = home.filter.channelIds()
    val unseen = unseenByChannel(store, feed.videos)
    val groups = channelGroups(store, home.category, unseen)
    // al abrir, el foco en el primer elegido o, si no hay, en el primer canal
    val listed = groups.flatMap { it.channels }
    val target = (listed.firstOrNull { it.id in ids } ?: listed.firstOrNull())?.id
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = page.grid,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth("title") {
            Column {
                Text(pickerTitle(store, home.category), style = Type.pageTitle)
                Text(
                    "OK: ver solo ese canal. Mantener OK o play/pausa: elegir varios.",
                    style = Type.sub.copy(fontSize = 16.sp),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        fullWidth("buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(
                    "Todos los canales",
                    {
                        home.show(Filter.All)
                        nav.back()
                    },
                    Modifier.restoreFocus(page, "#all", first = target == null),
                    selected = ids.isEmpty(),
                )
                if (ids.size > 1) {
                    Chip("Ver los ${ids.size} elegidos", { nav.back() }, Modifier.restoreFocus(page, "#done"), selected = true)
                }
            }
        }
        groups.forEach { group ->
            val title = group.title
            if (title != null) {
                fullWidth("group:$title") {
                    Text(title, style = Type.pageTitle.copy(fontSize = 20.sp), modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(group.channels, key = { it.id }) { channel ->
                TvChannelTile(
                    channel,
                    unseen = unseen[channel.id] ?: 0,
                    selected = channel.id in ids,
                    modifier = Modifier.restoreFocus(page, channel.id, first = channel.id == target),
                    onClick = {
                        home.show(Filter.OneChannel(channel.id))
                        nav.back()
                    },
                    onToggle = { home.show(home.filter.toggled(channel.id)) },
                )
            }
        }
    }
}

@Composable
private fun TvChannelTile(
    channel: Channel,
    unseen: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val accent = LocalStore.current.profile.accent
    Surface(
        onClick = onClick,
        onLongClick = onToggle,
        // el botón de play/pausa, como mantener OK: con el mando de la tele (por HDMI) no se puede
        // mantener pulsado el botón central
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                val media = event.nativeKeyEvent.keyCode in PLAY_KEYS
                if (media && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.repeatCount == 0) onToggle()
                media
            }
            .semantics { this.selected = selected }
            .then(modifier)
            .fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Colors.bg2, focusedContainerColor = Colors.bg3),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, accent)) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Colors.text)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PickerAvatar(channel, unseen, selected, 64.dp)
            Text(
                channel.name,
                style = Type.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

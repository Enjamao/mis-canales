package es.miscanales

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Mis Canales Kids en la tele. */
@Composable
fun KidsTvApp(store: Store, feed: Feed, library: Library) {
    CompositionLocalProvider(LocalStore provides store, LocalFeed provides feed, LocalLibrary provides library) {
        val context = LocalContext.current
        var pin by remember { mutableStateOf<PinFlow?>(null) }
        var settings by remember { mutableStateOf(false) }
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(20_000)
                tick++
            }
        }
        val left = remember(tick, store.resumed, store.kidsLimit) { KidsTime.remaining(context, store.kidsLimit) }
        Box(Modifier.fillMaxSize().background(Colors.bg)) {
            val flow = pin
            when {
                flow != null -> TvPinPad(flow, onCancel = { pin = null })
                settings -> KidsTvSettings(
                    onClose = { settings = false },
                    onChangePin = { pin = PinFlow(store, create = true) { pin = null } },
                )
                else -> KidsTvHome(
                    left,
                    onSettings = { pin = PinFlow(store) { pin = null; settings = true } },
                    onMore = { pin = PinFlow(store) { pin = null; KidsTime.grant(context); tick++ } },
                )
            }
        }
    }
}

@Composable
private fun KidsTvHome(left: Int?, onSettings: () -> Unit, onMore: () -> Unit) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val context = LocalContext.current
    var only by remember { mutableStateOf<String?>(null) }
    val channels = store.kidsChannels.sortedBy { it.name.lowercase() }
    val ids = channels.map { it.id }.toSet()
    val recent = feed.videos.filter { it.channelId in ids && (only == null || it.channelId == only) }
    // con un canal elegido (o si solo hay uno), también sus vídeos anteriores, si los piden
    val older = (channels.firstOrNull { it.id == only } ?: channels.singleOrNull())?.let { OlderVideos.of(it) }
    val recentIds = recent.map { it.vid }.toSet()
    val videos = recent + older?.videos.orEmpty().filter { it.vid !in recentIds }
    val firstFocus = remember { FocusRequester() }
    val timeUp = left != null && left <= 0
    LaunchedEffect(channels.isEmpty(), timeUp, videos.isEmpty()) { runCatching { firstFocus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_smart_display_filled), contentDescription = null, tint = KidsAmber, modifier = Modifier.size(36.dp))
            Text("Mis Canales Kids", style = Type.pageTitle, modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (left != null && !timeUp) Text("Quedan ${KidsTime.text(left)}", style = Type.sub.copy(fontSize = 16.sp), modifier = Modifier.padding(end = 16.dp))
            Pill(
                "Mayores",
                onSettings,
                if (channels.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                icon = R.drawable.ic_lock,
            )
        }
        when {
            channels.isEmpty() -> KidsTvMessage(
                "Aún no hay canales para niños.",
                "Los mayores los eligen en Mis Canales → Mis canales → un canal → «Para niños».",
            )
            timeUp -> KidsTvMessage(
                "¡Se acabó el tiempo de hoy!",
                "Mañana más. Un mayor puede dar media hora más con el PIN.",
                button = "Media hora más (PIN)",
                onButton = onMore,
                buttonModifier = Modifier.focusRequester(firstFocus),
            )
            else -> {
                if (channels.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Pill("Todos", { only = null }, selected = only == null) }
                        items(channels, key = { it.id }) { channel ->
                            Pill(channel.name, { only = if (only == channel.id) null else channel.id }, selected = only == channel.id)
                        }
                    }
                } else {
                    Spacer(Modifier.size(16.dp))
                }
                if (videos.isEmpty()) {
                    Text(if (feed.loading) "Cargando…" else "No hay vídeos todavía.", style = Type.sub)
                }
                val small = store.kidsSmall
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (small) 3 else 4),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (small) 32.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (small) 32.dp else 28.dp),
                ) {
                    itemsIndexed(videos, key = { _, video -> video.vid }) { i, video ->
                        Column {
                            Surface(
                                onClick = { playVideo(context, store, video, queue = videos.after(video)) },
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(if (small) 20.dp else 12.dp)),
                                colors = ClickableSurfaceDefaults.colors(containerColor = Colors.bg2, focusedContainerColor = Colors.bg2),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = if (small) 1.08f else 1.05f),
                                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, KidsAmber))),
                            ) {
                                Thumbnail(video)
                            }
                            if (!small) {
                                Text(
                                    video.title,
                                    style = Type.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                    if (older != null && !older.done) {
                        item(key = "#older", span = { GridItemSpan(maxLineSpan) }) {
                            Row {
                                if (older.loading) {
                                    Text("Cargando…", style = Type.sub)
                                } else {
                                    Pill(if (older.videos.isEmpty()) "Ver vídeos anteriores" else "Ver más", { older.load() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsTvMessage(
    title: String,
    text: String,
    button: String? = null,
    onButton: () -> Unit = {},
    buttonModifier: Modifier = Modifier,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = Type.pageTitle, textAlign = TextAlign.Center)
        Text(text, style = Type.sub.copy(fontSize = 16.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        if (button != null) Pill(button, onButton, buttonModifier.padding(top = 24.dp), icon = R.drawable.ic_lock, selected = true)
    }
}

/** Los ajustes de Kids en la tele (tras el PIN). */
@Composable
private fun KidsTvSettings(onClose: () -> Unit, onChangePin: () -> Unit) {
    val store = LocalStore.current
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ajustes de Kids", style = Type.pageTitle)
        Text("Tiempo al día", style = Type.title.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidsLimits.forEachIndexed { i, (minutes, label) ->
                Pill(
                    label,
                    { store.changeKidsLimit(minutes) },
                    if (i == 0) Modifier.focusRequester(first) else Modifier,
                    selected = store.kidsLimit == minutes,
                )
            }
        }
        Text(
            "Hoy llevan ${KidsTime.text(KidsTime.used(context))} en esta tele. Al acabarse, el vídeo se para; con el PIN, media hora más.",
            style = Type.sub.copy(fontSize = 14.sp),
        )
        Text("Modo peque", style = Type.title.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 12.dp))
        Pill(
            if (store.kidsSmall) "Miniaturas grandes y sin textos: sí" else "Miniaturas grandes y sin textos: no",
            { store.toggleKidsSmall() },
            selected = store.kidsSmall,
        )
        Text("Reproducción continua", style = Type.title.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 12.dp))
        Pill(
            if (store.autoNext) "Al acabar un vídeo, el siguiente: sí" else "Al acabar un vídeo, el siguiente: no",
            { store.toggleAutoNext() },
            selected = store.autoNext,
        )
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Cambiar el PIN", onChangePin, icon = R.drawable.ic_lock)
            Pill("Listo", onClose, icon = R.drawable.ic_check, selected = true)
        }
        Text(
            "Los canales para niños se eligen en Mis Canales (la app de los mayores): Mis canales → un canal → «Para niños».",
            style = Type.sub.copy(fontSize = 14.sp),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** El PIN en la tele: un teclado numérico que se maneja con las flechas del mando (o con números, si los tiene). */
@Composable
fun TvPinPad(flow: PinFlow, onCancel: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(flow) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onCancel)
    Column(
        Modifier.fillMaxSize().background(Colors.bg).onPreviewKeyEvent { event ->
            val code = event.nativeKeyEvent.keyCode
            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                flow.type('0' + (code - KeyEvent.KEYCODE_0))
                true
            } else {
                false
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(40.dp))
        Text(flow.prompt, style = Type.pageTitle, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(Pin.LENGTH) { i ->
                Box(
                    Modifier.size(20.dp).background(if (i < flow.digits.length) Colors.text else Colors.bg3, CircleShape),
                )
            }
        }
        Text(flow.error ?: " ", style = Type.sub.copy(fontSize = 16.sp, color = Colors.accent))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Borrar", "0", "Cancelar")
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in keys.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (key in row) {
                        Pill(
                            key,
                            {
                                when (key) {
                                    "Borrar" -> flow.erase()
                                    "Cancelar" -> onCancel()
                                    else -> flow.type(key[0])
                                }
                            },
                            Modifier.width(120.dp).let { if (key == "1") it.focusRequester(first) else it },
                        )
                    }
                }
            }
        }
    }
}

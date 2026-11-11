package es.miscanales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Tus canales: abrir uno, añadir otro y si se ven o no los Shorts. */
@Composable
fun ChannelsScreen(page: ChannelsPage) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val nav = LocalNavigator.current
    val channels = store.channels.sortedBy { it.name.lowercase() }
    LazyColumn(
        state = page.list,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "#head") {
            PageHead(
                "Mis canales",
                when (channels.size) {
                    0 -> "Aún no tienes ninguno."
                    1 -> "1 canal. Púlsalo para ver sus vídeos o quitarlo."
                    else -> "${channels.size} canales. Pulsa uno para ver sus vídeos o quitarlo."
                },
            )
        }
        item(key = "#buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                Pill(
                    "Añadir canal",
                    { nav.go(Section.Add) },
                    Modifier.restoreFocus(page, "add", first = channels.isEmpty()),
                    icon = R.drawable.ic_add,
                )
                Pill(
                    if (store.hideShorts) "Shorts: ocultos" else "Shorts: se ven",
                    {
                        store.toggleShorts()
                        feed.reload()
                    },
                    Modifier.restoreFocus(page, "shorts"),
                )
            }
        }
        itemsIndexed(channels, key = { _, channel -> channel.id }) { i, channel ->
            ChannelRow(channel, Modifier.widthIn(max = 900.dp).restoreFocus(page, channel.id, first = i == 0))
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    val feed = LocalFeed.current
    val videos = feed.videos.filter { it.channelId == channel.id }
    val failed = feed.failed.any { it.id == channel.id }
    Surface(
        onClick = { nav.open(ChannelPage(channel.id)) },
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
            Avatar(channel, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = Type.title.copy(fontSize = 18.sp, lineHeight = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val latest = videos.firstOrNull()?.let { ago(it.published) }
                Text(
                    when {
                        failed -> "No se ha podido cargar"
                        latest != null -> "Último vídeo $latest"
                        feed.loading -> "Cargando…"
                        else -> "Sin vídeos recientes"
                    },
                    style = Type.sub.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

/** Los vídeos de un canal, con su foto arriba y el botón para quitarlo. */
@Composable
fun ChannelScreen(page: ChannelPage) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val nav = LocalNavigator.current
    val channel = store.channel(page.channelId)
    val videos = feed.videos.filter { it.channelId == page.channelId }
    VideoGrid(
        page,
        videos,
        page.grid,
        showChannel = false,
        status = {
            when {
                feed.loading && videos.isEmpty() -> Status("Cargando…")
                videos.isEmpty() -> Status("No hay vídeos recientes de este canal.")
                else -> {}
            }
        },
    ) {
        fullWidth("head") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(channel, 80.dp)
                Column(Modifier.weight(1f)) {
                    Text(channel?.name ?: "Canal", style = Type.pageTitle)
                    Text(
                        "Sus últimos vídeos. Al quitarlo, deja de salir en Inicio.",
                        style = Type.sub.copy(fontSize = 14.sp),
                    )
                }
                Pill(
                    if (page.confirmRemove) "¿Seguro? Pulsa otra vez" else "Quitar canal",
                    {
                        if (page.confirmRemove) {
                            store.remove(page.channelId)
                            feed.drop(page.channelId)
                            nav.back()
                        } else {
                            page.confirmRemove = true
                        }
                    },
                    Modifier.restoreFocus(page, "remove", first = videos.isEmpty()).onFocusChanged {
                        // si te vas del botón, hay que volver a confirmar
                        if (!it.isFocused) page.confirmRemove = false
                    },
                    selected = page.confirmRemove,
                )
            }
        }
    }
}

/** Escribir el @nombre o el enlace de un canal y añadirlo. */
@Composable
fun AddChannelScreen(page: AddChannelPage) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun add() {
        val text = page.query.trim()
        if (text.isEmpty() || page.busy) return
        keyboard?.hide()
        page.busy = true
        page.message = "Buscando el canal…"
        page.failed = false
        scope.launch {
            try {
                val channel = YouTube.resolve(text)
                val again = store.has(channel.id)
                store.add(channel)
                feed.reload()
                page.query = ""
                page.message = if (again) "${channel.name} ya estaba en tus canales." else "Añadido: ${channel.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChannelNotFound) {
                page.failed = true
                page.message = "No encuentro ese canal. Escribe su @nombre tal como sale en YouTube, o pega su enlace."
            } catch (e: IOException) {
                page.failed = true
                page.message = "No se ha podido conectar con YouTube. Mira la conexión a internet de la tele."
            } catch (e: Exception) {
                page.failed = true
                page.message = "Algo ha fallado al buscar el canal (${e.javaClass.simpleName}). Prueba con su enlace."
            } finally {
                page.busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
        PageHead(
            "Añadir canal",
            "Escribe el @nombre del canal tal como sale en YouTube, o pega el enlace del canal.",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelField(page, onDone = ::add, modifier = Modifier.restoreFocus(page, "field", first = true))
            Pill("Añadir", ::add, Modifier.restoreFocus(page, "button"), icon = R.drawable.ic_add, selected = true)
        }
        val message = page.message
        if (message != null) {
            Text(
                message,
                style = Type.body.copy(fontSize = 16.sp, color = if (page.failed) Colors.accent else Colors.text),
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("Vale cualquiera de estas formas:", style = Type.sub.copy(fontSize = 14.sp))
        for (example in listOf("@NombreDelCanal", "youtube.com/@NombreDelCanal", "youtube.com/channel/UC…")) {
            Text("•  $example", style = Type.sub.copy(fontSize = 14.sp, lineHeight = 22.sp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tienes ${store.channels.size} ${if (store.channels.size == 1) "canal" else "canales"}.",
            style = Type.sub.copy(fontSize = 14.sp),
        )
    }
}

/** La caja de texto, con borde azul mientras escribes, como el buscador de YouTube. */
@Composable
private fun ChannelField(page: AddChannelPage, onDone: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = page.query,
        onValueChange = { page.query = it },
        singleLine = true,
        textStyle = Type.body.copy(fontSize = 18.sp),
        cursorBrush = SolidColor(Colors.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.widthIn(min = 360.dp, max = 560.dp).onFocusChanged { focused = it.isFocused },
        decorationBox = { field ->
            Box(
                Modifier.height(48.dp)
                    .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Colors.link else Colors.bg3), CircleShape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (page.query.isEmpty()) Text("@NombreDelCanal", style = Type.body.copy(fontSize = 18.sp, color = Colors.muted))
                field()
            }
        },
    )
}

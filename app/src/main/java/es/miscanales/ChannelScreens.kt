package es.miscanales

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // pidiendo el PIN (para "Pedir PIN al abrir" o para cambiarlo): solo el teclado
    page.pin?.let { flow ->
        TvPinPad(flow, onCancel = { page.pin = null })
        return
    }
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
        val ha = store.ha
        if (ha != null) {
            item(key = "#sync") {
                val library = LocalLibrary.current
                val error = library.syncError
                Text(
                    if (error != null) {
                        "Home Assistant no responde: $error. Se ven los canales de la última vez."
                    } else {
                        "Sincronizado con Home Assistant (${ha.entity})" + (ago(library.syncedAt)?.let { ", $it" } ?: "") + "." +
                            if (library.progressSynced == false) " Falta la lista ${ProgressSync.list(ha)} para compartir por dónde vas." else ""
                    },
                    style = Type.sub.copy(fontSize = 14.sp, color = if (error != null) Colors.accent else Colors.muted),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        item(key = "#buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                Pill(
                    "Añadir canal",
                    { nav.go(Section.Add) },
                    Modifier.restoreFocus(page, "add", first = channels.isEmpty()),
                    icon = R.drawable.ic_add,
                )
                // los mismos canales que en el móvil
                Pill(
                    if (store.ha != null) "Home Assistant: conectado" else "Conectar con Home Assistant",
                    { nav.open(HaPage()) },
                    Modifier.restoreFocus(page, "ha"),
                    selected = store.ha == null && channels.isEmpty(),
                )
                Pill(
                    if (store.hideShorts) "Shorts: ocultos" else "Shorts: se ven",
                    {
                        store.toggleShorts()
                        feed.reload()
                    },
                    Modifier.restoreFocus(page, "shorts"),
                )
                Pill(
                    if (store.useYouTubeApp) "Reproductor: app de YouTube" else "Reproductor: Mis Canales",
                    { store.togglePlayer() },
                    Modifier.restoreFocus(page, "player"),
                )
                Pill(
                    if (store.subtitles) "Subtítulos: sí" else "Subtítulos: no",
                    { store.toggleSubtitles() },
                    Modifier.restoreFocus(page, "subtitles"),
                )
                Pill(
                    if (store.sponsorBlock) "Patrocinios: se saltan" else "Patrocinios: se ven",
                    { store.toggleSponsorBlock() },
                    Modifier.restoreFocus(page, "sponsorblock"),
                )
            }
        }
        item(key = "#autonext") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Pill(
                    if (store.autoNext) "Reproducción continua: sí" else "Reproducción continua: no",
                    { store.toggleAutoNext() },
                    Modifier.restoreFocus(page, "autonext"),
                )
                // las versiones nuevas, de GitHub
                Pill(
                    if (Updater.available != null) Updater.label() else "Versión ${BuildConfig.VERSION_NAME}: buscar otra",
                    {
                        scope.launch {
                            if (Updater.available != null) Updater.install(context) else Updater.check(context, byHand = true)
                            Updater.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        }
                    },
                    Modifier.restoreFocus(page, "update"),
                    selected = Updater.available != null,
                )
            }
        }
        item(key = "#kids") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Text("Kids:", style = Type.sub.copy(fontSize = 14.sp))
                // si no, los canales «Para niños» salen solo en Kids (y aquí, para cambiarlos)
                Pill(
                    if (store.kidsInAdult) "Sus canales también en Inicio: sí" else "Sus canales también en Inicio: no",
                    { store.toggleKidsInAdult() },
                    Modifier.restoreFocus(page, "kidsinadult"),
                )
                Pill(
                    if (store.askPin) "Pedir PIN al abrir: sí" else "Pedir PIN al abrir: no",
                    {
                        if (store.askPin) {
                            store.toggleAskPin()
                        } else {
                            page.pin = PinFlow(store) {
                                page.pin = null
                                store.toggleAskPin()
                                AppLock.unlock()
                            }
                        }
                    },
                    Modifier.restoreFocus(page, "askpin"),
                    icon = R.drawable.ic_lock,
                )
                if (store.hasPin) {
                    Pill(
                        "Cambiar el PIN",
                        { page.pin = PinFlow(store) { page.pin = PinFlow(store, create = true) { page.pin = null } } },
                        Modifier.restoreFocus(page, "changepin"),
                    )
                }
            }
        }
        item(key = "#hint") {
            Text(
                "La categoría y «Para niños» se ponen dentro de cada canal.",
                style = Type.sub.copy(fontSize = 14.sp),
                modifier = Modifier.padding(bottom = 12.dp),
            )
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
    val library = LocalLibrary.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val channel = store.channel(page.channelId)
    val videos = feed.videos.filter { it.channelId == page.channelId }
    // y sus vídeos anteriores, más allá de los últimos 15, si los pides
    val older = channel?.let { OlderVideos.of(it) }
    val shown = videos.map { it.vid }.toSet()
    val all = videos + older?.videos.orEmpty().filter { it.vid !in shown }
    VideoGrid(
        page,
        all,
        page.grid,
        showChannel = false,
        status = {
            when {
                feed.loading && all.isEmpty() -> Status("Cargando…")
                all.isEmpty() -> Status("No hay vídeos recientes de este canal.")
                else -> {}
            }
            if (older != null) {
                MoreFooter(
                    page,
                    loading = older.loading,
                    done = older.done,
                    label = if (older.videos.isEmpty()) "Ver vídeos anteriores" else "Ver más",
                    doneText = "Ya están todos los vídeos de ${older.channel.name}.",
                    error = older.error,
                    onClick = { older.load() },
                )
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
                if (channel != null) {
                    // su categoría: en Inicio, cada una es una fila
                    Pill(
                        channel.category?.let { "Categoría: $it" } ?: "Poner categoría",
                        { nav.open(CategoryPage(channel.id)) },
                        Modifier.restoreFocus(page, "category"),
                        selected = channel.category != null,
                    )
                    // para Mis Canales Kids (se guarda también en Home Assistant)
                    Pill(
                        if (channel.kids) "Para niños: sí" else "Para niños: no",
                        {
                            scope.launch {
                                try {
                                    library.setKids(channel.id, !channel.kids)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No se ha podido guardar: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        Modifier.restoreFocus(page, "kids"),
                        icon = if (channel.kids) R.drawable.ic_check else null,
                        selected = channel.kids,
                    )
                }
                Pill(
                    if (page.confirmRemove) "¿Seguro? Pulsa otra vez" else "Quitar canal",
                    {
                        if (page.confirmRemove) {
                            scope.launch {
                                try {
                                    library.remove(page.channelId)
                                    nav.back()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    page.confirmRemove = false
                                    Toast.makeText(context, "No se ha podido quitar: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
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
    val library = LocalLibrary.current
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
                val (channel, again) = library.add(text)
                page.query = ""
                page.message = if (again) "${channel.name} ya estaba en tus canales." else "Añadido: ${channel.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                page.failed = true
                page.message = Library.addError(e)
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
            TvTextField(
                page.query,
                { page.query = it },
                "@NombreDelCanal",
                onDone = ::add,
                modifier = Modifier.restoreFocus(page, "field", first = true),
            )
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

/** Elegir la categoría de un canal: una de las que ya hay, ninguna, o escribir una nueva. */
@Composable
fun CategoryScreen(page: CategoryPage) {
    val store = LocalStore.current
    val library = LocalLibrary.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val channel = store.channel(page.channelId)

    fun choose(category: String?) {
        if (channel == null || page.busy) return
        keyboard?.hide()
        page.busy = true
        scope.launch {
            try {
                library.setCategory(channel.id, category)
                nav.back()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                page.busy = false
                Toast.makeText(context, "No se ha podido guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val options = store.allCategories + listOf<String?>(null)
    val current = options.indexOf(channel?.category).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
        PageHead(
            "Categoría de ${channel?.name ?: "este canal"}",
            "En Inicio, cada categoría es una fila. Se guarda también en Home Assistant, para el móvil.",
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        ) {
            itemsIndexed(options, key = { _, name -> name ?: "#none" }) { i, name ->
                Pill(
                    name ?: "Sin categoría",
                    { choose(name) },
                    Modifier.restoreFocus(page, "option:${name ?: ""}", first = i == current),
                    icon = if (channel?.category == name) R.drawable.ic_check else null,
                    selected = channel?.category == name,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("O una nueva:", style = Type.sub.copy(fontSize = 14.sp), modifier = Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvTextField(
                page.text,
                { page.text = it },
                "Música, Noticias…",
                onDone = { if (page.text.isNotBlank()) choose(page.text) },
                modifier = Modifier.restoreFocus(page, "field"),
                capitalization = KeyboardCapitalization.Sentences,
            )
            Pill("Crear", { if (page.text.isNotBlank()) choose(page.text) }, Modifier.restoreFocus(page, "create"), icon = R.drawable.ic_add)
        }
    }
}

/** Una caja de texto, con borde azul mientras escribes, como el buscador de YouTube. */
@Composable
internal fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Type.body.copy(fontSize = 18.sp),
        cursorBrush = SolidColor(Colors.text),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.widthIn(min = 360.dp, max = 560.dp).onFocusChanged { focused = it.isFocused },
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

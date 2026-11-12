@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package es.miscanales

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// La versión para el móvil: lo mismo que en la tele, con una interfaz táctil (Material 3).

private enum class Tab(val label: String, @DrawableRes val icon: Int, @DrawableRes val currentIcon: Int) {
    Home("Inicio", R.drawable.ic_home, R.drawable.ic_home_filled),
    Channels("Canales", R.drawable.ic_smart_display, R.drawable.ic_smart_display_filled),
    Add("Añadir", R.drawable.ic_add, R.drawable.ic_add),
}

@Composable
fun PhoneApp(store: Store, feed: Feed, library: Library, shared: MutableState<String?>) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Colors.accent,
            onPrimary = Color.White,
            background = Colors.bg,
            onBackground = Colors.text,
            surface = Colors.bg,
            onSurface = Colors.text,
            surfaceVariant = Colors.bg2,
            onSurfaceVariant = Colors.muted,
            surfaceContainer = Colors.bg2,
            secondaryContainer = Colors.bg3,
            onSecondaryContainer = Colors.text,
        ),
    ) {
        CompositionLocalProvider(LocalStore provides store, LocalFeed provides feed, LocalLibrary provides library) {
            var tab by rememberSaveable { mutableStateOf(Tab.Home) }
            var filter by remember { mutableStateOf<Filter>(Filter.All) }
            var details by remember { mutableStateOf<Video?>(null) }
            // al compartir un vídeo o un canal con la app, a "Añadir"
            val sharedText = shared.value
            LaunchedEffect(sharedText) {
                if (sharedText != null) tab = Tab.Add
            }
            Scaffold(
                containerColor = Colors.bg,
                bottomBar = {
                    NavigationBar(containerColor = Colors.bg2) {
                        for (t in Tab.entries) {
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(painterResource(if (tab == t) t.currentIcon else t.icon), contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (tab) {
                        Tab.Home -> PhoneHome(filter, onFilter = { filter = it }, onDetails = { details = it })
                        Tab.Channels -> PhoneChannels(onOpen = { id ->
                            filter = Filter.OneChannel(id)
                            tab = Tab.Home
                        })
                        Tab.Add -> PhoneAdd(shared)
                    }
                }
            }
            val video = details
            if (video != null) {
                VideoActions(
                    video,
                    onDismiss = { details = null },
                    onChannel = {
                        filter = Filter.OneChannel(video.channelId)
                        details = null
                    },
                )
            }
        }
    }
}

@Composable
private fun PhoneHome(filter: Filter, onFilter: (Filter) -> Unit, onDetails: (Video) -> Unit) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val context = LocalContext.current
    val newOnes = feed.videos.filter { store.isNew(it) }
    val continuing = feed.videos.filter { store.progress[it.vid]?.started == true }
        .sortedByDescending { store.progress[it.vid]?.at ?: 0L }
    val videos = when (filter) {
        Filter.All -> feed.videos
        Filter.New -> newOnes
        Filter.Continue -> continuing
        is Filter.OneChannel -> feed.videos.filter { it.channelId == filter.id }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "#head") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                if (feed.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                IconButton(onClick = { feed.reload() }) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Actualizar")
                }
            }
        }
        item(key = "#filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    FilterChip(selected = filter == Filter.All, onClick = { onFilter(Filter.All) }, label = { Text("Todos") })
                }
                item(key = "new") {
                    FilterChip(
                        selected = filter == Filter.New,
                        onClick = { onFilter(Filter.New) },
                        label = { Text(if (newOnes.isEmpty()) "Sin ver" else "Sin ver · ${newOnes.size}") },
                    )
                }
                if (continuing.isNotEmpty() || filter == Filter.Continue) {
                    item(key = "continue") {
                        FilterChip(
                            selected = filter == Filter.Continue,
                            onClick = { onFilter(Filter.Continue) },
                            label = { Text(if (continuing.isEmpty()) "Seguir viendo" else "Seguir viendo · ${continuing.size}") },
                        )
                    }
                }
                items(store.channels.sortedBy { it.name.lowercase() }, key = { it.id }) { channel ->
                    FilterChip(
                        selected = filter == Filter.OneChannel(channel.id),
                        onClick = { onFilter(Filter.OneChannel(channel.id)) },
                        label = { Text(channel.name, maxLines = 1) },
                    )
                }
            }
        }
        val message = when {
            store.channels.isEmpty() -> "Aún no tienes canales. Añade uno en \"Añadir\", o comparte un vídeo desde la app de YouTube con Mis Canales."
            feed.error != null && videos.isEmpty() -> "No se ha podido conectar con YouTube. Mira la conexión a internet."
            (feed.loading || feed.loadedAt == 0L) && videos.isEmpty() -> "Cargando tus canales…"
            videos.isEmpty() && filter == Filter.New -> "No tienes vídeos nuevos sin ver. ¡Al día!"
            videos.isEmpty() -> "No hay vídeos recientes."
            else -> null
        }
        if (message != null) {
            item(key = "#message") { Hint(message, Modifier.padding(24.dp)) }
        }
        if (feed.failed.isNotEmpty()) {
            item(key = "#failed") {
                Hint(
                    "YouTube no responde ahora para " + feed.failed.joinToString(", ") { it.name } + ". Se ven los últimos vídeos que se cargaron.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(videos, key = { it.vid }) { video ->
            PhoneVideo(video, onClick = { playVideo(context, store, video) }, onLongClick = { onDetails(video) })
        }
    }
}

/** Un vídeo a todo lo ancho, como en YouTube: tocar lo pone, mantener abre sus opciones. */
@Composable
private fun PhoneVideo(video: Video, onClick: () -> Unit, onLongClick: () -> Unit) {
    val store = LocalStore.current
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        Thumbnail(video)
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            Avatar(store.channel(video.channelId), 36.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(video.channelName, video.stats()).filter { it.isNotEmpty() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Mantener pulsado un vídeo: su descripción y qué hacer con él. */
@Composable
private fun VideoActions(video: Video, onDismiss: () -> Unit, onChannel: () -> Unit) {
    val store = LocalStore.current
    val library = LocalLibrary.current
    val context = LocalContext.current
    val watched = store.isWatched(video.vid)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(video.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(listOf(video.channelName, fullDate(video.published)).filter { it.isNotEmpty() }.joinToString(" • "), color = Colors.muted)
                val config = store.ha
                if (config != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            store.setWatched(video.vid, true)
                            // desde donde ibas, si lo habías empezado y no acabado
                            val start = store.progress[video.vid]?.takeIf { it.started && !it.finished }?.pos ?: 0
                            Tele.sendAndTell(context, config, video.vid, start) { tv -> store.saveHa(config.copy(tv = tv)) }
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("📺 Ver en la tele") }
                }
                if (video.description.isNotBlank()) {
                    Text(video.description, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                playVideo(context, store, video)
            }) { Text("Ver") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    library.setWatched(video, !watched)
                    onDismiss()
                }) { Text(if (watched) "No visto" else "Visto") }
                TextButton(onClick = onChannel) { Text("Canal") }
                TextButton(onClick = {
                    onDismiss()
                    store.setWatched(video.vid, true)
                    context.playOnYouTube(video.vid)
                }) { Text("YouTube") }
            }
        },
    )
}

@Composable
private fun PhoneChannels(onOpen: (String) -> Unit) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val library = LocalLibrary.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<Channel?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "#head") {
            Text("Mis canales", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (store.channels.isEmpty()) {
            item(key = "#empty") { Hint("Aún no tienes ninguno.") }
        }
        items(store.channels.sortedBy { it.name.lowercase() }, key = { it.id }) { channel ->
            val latest = feed.videos.firstOrNull { it.channelId == channel.id }?.let { ago(it.published) }
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(channel.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(channel, 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        latest?.let { "Último vídeo $it" } ?: "Sin vídeos recientes",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.muted,
                    )
                }
                IconButton(onClick = { removing = channel }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = "Quitar ${channel.name}")
                }
            }
        }
        item(key = "#settings") {
            Column(Modifier.padding(top = 24.dp)) {
                Text("Ajustes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                SettingSwitch("Ocultar Shorts", store.hideShorts) {
                    store.toggleShorts()
                    feed.reload()
                }
                SettingSwitch("Ver en la app de YouTube", store.useYouTubeApp) { store.togglePlayer() }
                SettingSwitch("Subtítulos", store.subtitles) { store.toggleSubtitles() }
                SettingSwitch("Seguir sonando con la pantalla apagada", store.backgroundPlay) { store.toggleBackgroundPlay() }
                SettingSwitch("Saltar patrocinios (SponsorBlock)", store.sponsorBlock) { store.toggleSponsorBlock() }
                SettingSwitch("Avisar de vídeos nuevos", store.notifyNew) {
                    store.toggleNotifyNew()
                    NewVideos.schedule(context, store.notifyNew)
                    if (store.notifyNew) (context as? MainActivity)?.askNotifications()
                }
                if (store.notifyNew) {
                    Hint(
                        "Cada media hora, más o menos, el móvil mira tus canales. Al tocar el aviso, el vídeo se " +
                            "abre aquí. Si también te llegan los avisos de Telegram, puedes apagar la automatización " +
                            "\"Mis Canales: aviso de vídeos nuevos\" en Home Assistant.",
                    )
                    val latest = feed.videos.firstOrNull()
                    if (latest != null) {
                        TextButton(onClick = {
                            (context as? MainActivity)?.askNotifications()
                            NewVideos.preview(context, latest)
                        }) { Text("Probar con el último vídeo") }
                    }
                }
            }
        }
        item(key = "#ha") { HaSettings() }
    }
    val channel = removing
    if (channel != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("¿Quitar ${channel.name}?") },
            text = {
                Text(
                    if (store.ha != null) "Se quita también de la lista de Home Assistant, y deja de salir en la tele." else "Deja de salir en Inicio.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        try {
                            library.remove(channel.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se ha podido quitar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onChange() })
    }
}

/** La conexión con Home Assistant: las direcciones, el token y la lista. */
@Composable
private fun HaSettings() {
    val store = LocalStore.current
    val library = LocalLibrary.current
    val scope = rememberCoroutineScope()
    val config = store.ha
    var url by rememberSaveable { mutableStateOf(config?.url.orEmpty()) }
    var localUrl by rememberSaveable { mutableStateOf(config?.localUrl.orEmpty()) }
    var token by rememberSaveable { mutableStateOf(config?.token.orEmpty()) }
    var entity by rememberSaveable { mutableStateOf(config?.entity ?: HaConfig.DEFAULT_ENTITY) }
    var tv by rememberSaveable { mutableStateOf(config?.tv.orEmpty()) }
    var tvs by remember { mutableStateOf<List<HaTv>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sincronizar con Home Assistant", style = MaterialTheme.typography.titleMedium)
        val error = library.syncError
        Hint(
            when {
                config == null -> "Los canales se guardan en una lista de tareas de tu Home Assistant y se comparten entre el móvil y la tele."
                error != null -> "No responde: $error"
                else -> "Conectado" + (ago(library.syncedAt)?.let { ", sincronizado $it" } ?: "") + "." +
                    if (library.progressSynced == false) {
                        " Para compartir también por dónde vas en cada vídeo, crea en HA otra lista de tareas " +
                            "llamada \"Mis Canales progreso\" (${config?.let { ProgressSync.list(it) }.orEmpty()})."
                    } else {
                        ""
                    }
            },
        )
        OutlinedTextField(
            url,
            { url = it },
            label = { Text("Dirección de fuera (DuckDNS)") },
            placeholder = { Text("https://micasa.duckdns.org:8123") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            localUrl,
            { localUrl = it },
            label = { Text("Dirección de casa (opcional)") },
            placeholder = { Text("http://192.168.1.10:8123") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            token,
            { token = it },
            label = { Text("Token de acceso de larga duración") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            entity,
            { entity = it },
            label = { Text("Lista de tareas") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Ver en la tele", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Hint(
            "Mantén pulsado un vídeo (o, mientras lo ves, toca \"Ver en la tele\") y Home Assistant lo abre en el " +
                "Fire TV desde donde ibas. Hace falta el Fire TV en HA con la integración Android Debug Bridge " +
                "(la IP del Fire TV y el puerto 5555; la primera vez, en la tele, acepta \"Permitir siempre\"). " +
                "Si HA tiene una sola tele, déjalo vacío y se busca sola.",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                tv,
                { tv = it },
                label = { Text("Tele") },
                placeholder = { Text("media_player.fire_tv") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                onClick = {
                    busy = true
                    message = "Buscando teles…"
                    scope.launch {
                        message = try {
                            val found = HomeAssistant.tvs(HaConfig(url.trim(), token.trim(), localUrl.trim()))
                            tvs = found
                            if (found.size == 1) tv = found[0].entity
                            when (found.size) {
                                0 -> "Home Assistant no tiene ninguna tele con Android Debug Bridge."
                                1 -> "Encontrada: ${found[0].name}. Toca Guardar."
                                else -> "Elige cuál:"
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "No se ha podido buscar: ${e.message}"
                        }
                        busy = false
                    }
                },
            ) { Text("Buscar") }
        }
        if (tvs.size > 1) {
            tvs.forEach { found ->
                TextButton(onClick = {
                    tv = found.entity
                    tvs = emptyList()
                    message = "Elegida: ${found.name}. Toca Guardar."
                }) { Text("${found.name}  (${found.entity})") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                onClick = {
                    busy = true
                    message = "Probando…"
                    scope.launch {
                        message = try {
                            library.connect(
                                HaConfig(url.trim(), token.trim(), localUrl.trim(), entity.trim().ifBlank { HaConfig.DEFAULT_ENTITY }, tv.trim()),
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "No se ha podido conectar: ${e.message}"
                        }
                        busy = false
                    }
                },
            ) { Text(if (config == null) "Conectar" else "Guardar") }
            if (config != null) {
                TextButton(onClick = {
                    library.disconnect()
                    message = "Desconectado. Los canales se quedan en el móvil."
                }) { Text("Desconectar") }
            }
        }
        message?.let { Text(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PhoneAdd(shared: MutableState<String?>) {
    val store = LocalStore.current
    val library = LocalLibrary.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun add(input: String) {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        keyboard?.hide()
        busy = true
        failed = false
        message = "Buscando el canal…"
        scope.launch {
            try {
                val (channel, again) = library.add(text)
                query = ""
                message = if (again) "${channel.name} ya estaba en tus canales." else "Añadido: ${channel.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                message = Library.addError(e)
            } finally {
                busy = false
            }
        }
    }

    // lo que llega al compartir desde YouTube se añade directamente
    val sharedText = shared.value
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            shared.value = null
            query = sharedText
            add(sharedText)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Añadir canal", style = MaterialTheme.typography.headlineSmall)
        Hint("Escribe el @nombre del canal tal como sale en YouTube, o pega su enlace.")
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("@NombreDelCanal o enlace") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(query) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { add(query) }, enabled = !busy && query.isNotBlank()) { Text("Añadir") }
        message?.let { Text(it, color = if (failed) Colors.accent else Colors.text) }
        Hint(
            "Más fácil: en la app de YouTube, en cualquier vídeo, Compartir → Mis Canales, y se añade su canal." +
                if (store.ha != null) " Se guarda también en Home Assistant y aparece en la tele." else "",
        )
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Colors.muted, modifier = modifier)
}

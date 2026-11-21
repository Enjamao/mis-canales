@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package es.miscanales

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.net.nsd.NsdServiceInfo
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.material3.LinearProgressIndicator
import kotlin.coroutines.cancellation.CancellationException

// La versión para el móvil: lo mismo que en la tele, con una interfaz táctil (Material 3).

private enum class Tab(val label: String, @DrawableRes val icon: Int, @DrawableRes val currentIcon: Int) {
    Home("Inicio", R.drawable.ic_home, R.drawable.ic_home_filled),
    Search("Buscar", R.drawable.ic_search, R.drawable.ic_search),
    Lists("Listas", R.drawable.ic_playlist, R.drawable.ic_playlist),
    Channels("Canales", R.drawable.ic_smart_display, R.drawable.ic_smart_display_filled),
    Add("Añadir", R.drawable.ic_add, R.drawable.ic_add),
}

/** Los colores de la app del móvil (también los de Kids, con [accent] en ámbar). */
internal fun phoneColors(accent: Color = Colors.accent) = darkColorScheme(
    primary = accent,
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
)

@Composable
fun PhoneApp(store: Store, feed: Feed, library: Library, shared: MutableState<String?>) {
    MaterialTheme(colorScheme = phoneColors(store.profile.accent)) {
        CompositionLocalProvider(LocalStore provides store, LocalFeed provides feed, LocalLibrary provides library) {
            // si se ha silenciado un canal desde su aviso con la app abierta, se vuelven a leer los canales
            val changed = ChannelsChanged.version
            LaunchedEffect(changed) { if (changed > 0) store.reloadChannels() }
            var tab by rememberSaveable { mutableStateOf(Tab.Home) }
            var filter by remember { mutableStateOf<Filter>(Filter.All) }
            // la categoría elegida arriba en Inicio (null: todas)
            var category by rememberSaveable { mutableStateOf<String?>(null) }
            var details by remember { mutableStateOf<Video?>(null) }
            // los ajustes, en su pantalla (desde la rueda de Inicio o de Canales)
            var settings by rememberSaveable { mutableStateOf(false) }
            val search = remember { SearchState() }
            // una lista recién guardada desde «Añadir», para abrirla al ir a Listas
            var openList by remember { mutableStateOf<String?>(null) }
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
                                selected = tab == t && !settings,
                                onClick = {
                                    tab = t
                                    settings = false
                                },
                                icon = { Icon(painterResource(if (tab == t) t.currentIcon else t.icon), contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    if (settings) {
                        PhoneSettings(onBack = { settings = false })
                    } else when (tab) {
                        Tab.Home -> PhoneHome(
                            filter,
                            category,
                            onFilter = {
                                filter = it
                                if (BuildConfig.DEBUG) Log.i("MisCanales", "Filtro: $it")
                            },
                            onCategory = {
                                category = it
                                // un canal suelto de otra categoría ya no se vería en la fila
                                if (filter.channelIds().isNotEmpty()) filter = Filter.All
                            },
                            onDetails = { details = it },
                            onSettings = { settings = true },
                        )
                        Tab.Search -> PhoneSearch(search, onDetails = { details = it })
                        Tab.Lists -> PhoneLists(onDetails = { details = it }, start = openList, onStarted = { openList = null })
                        Tab.Channels -> PhoneChannels(
                            onOpen = { id ->
                                filter = Filter.OneChannel(id)
                                category = null
                                tab = Tab.Home
                            },
                            onSettings = { settings = true },
                        )
                        Tab.Add -> PhoneAdd(shared, onList = { name ->
                            openList = name
                            tab = Tab.Lists
                        })
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
                        category = null
                        details = null
                    },
                )
            }
        }
    }
}

@Composable
private fun PhoneHome(
    filter: Filter,
    category: String?,
    onFilter: (Filter) -> Unit,
    onCategory: (String?) -> Unit,
    onDetails: (Video) -> Unit,
    onSettings: () -> Unit,
) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // los canales de Inicio (sin los de niños, salvo que se quiera) y sus vídeos
    val shown = store.shownChannels
    val shownIds = shown.map { it.id }.toSet()
    val all = feed.videos.filter { it.channelId in shownIds }
    val categories = store.categories
    // "Otros": los canales sin categoría, cuando hay alguna
    val withOthers = categories.isNotEmpty() && shown.any { it.category == null }
    // la categoría elegida (si ya no la tiene ningún canal, como si no hubiera ninguna)
    val chosen = category?.takeIf { it in categories || (it == OTHERS_KEY && withOthers) }
    val channels = (if (chosen == null) shown else store.channelsIn(categoryFor(chosen))).sortedBy { it.name.lowercase() }
    val inCategory = if (chosen == null) {
        all
    } else {
        val ids = channels.map { it.id }.toSet()
        all.filter { it.channelId in ids }
    }
    val newOnes = inCategory.filter { store.isNew(it) }
    val continuing = inCategory.filter { store.progress[it.vid]?.started == true }
        .sortedByDescending { store.progress[it.vid]?.at ?: 0L }
    val videos = when (filter) {
        Filter.All -> inCategory
        Filter.New -> newOnes
        Filter.Continue -> continuing
        is Filter.OneChannel -> all.filter { it.channelId == filter.id }
        is Filter.Several -> all.filter { it.channelId in filter.ids }
    }
    // el panel con los canales, abierto desde la pastilla «Canales»
    var picking by remember { mutableStateOf(false) }
    if (picking) PhoneChannelsSheet(filter, chosen, onFilter, onDismiss = { picking = false })
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
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Ajustes")
                }
            }
        }
        if (Updater.available != null) {
            item(key = "#update") { UpdateBanner() }
        }
        if (categories.isNotEmpty()) {
            // las categorías, en píldoras redondas, encima de la fila de siempre
            item(key = "#categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "#all") {
                        FilterChip(
                            selected = chosen == null,
                            onClick = { onCategory(null) },
                            label = { Text("Todas") },
                            shape = CircleShape,
                        )
                    }
                    items(categories + if (withOthers) listOf(OTHERS_KEY) else emptyList(), key = { "cat:$it" }) { key ->
                        FilterChip(
                            selected = chosen == key,
                            onClick = { onCategory(if (chosen == key) null else key) },
                            label = { Text(categoryFor(key) ?: "Otros", maxLines = 1) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }
        item(key = "#filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = filter == Filter.All,
                        onClick = { onFilter(Filter.All) },
                        label = { Text(if (chosen == null) "Todos" else "Todos · ${channels.size}") },
                    )
                }
                // los canales: una pastilla que abre el panel con todos; con canales elegidos, su nombre (o
                // "3 canales") y una ✕ para quitarlos
                item(key = "channels") {
                    val ids = filter.channelIds()
                    val one = ids.singleOrNull()?.let { store.channel(it) }
                    FilterChip(
                        selected = ids.isNotEmpty(),
                        onClick = { picking = true },
                        label = { Text(channelsLabel(store, ids), maxLines = 1) },
                        leadingIcon = if (one != null) {
                            { Avatar(one, 18.dp) }
                        } else {
                            null
                        },
                        trailingIcon = {
                            if (ids.isEmpty()) {
                                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = "Quitar",
                                    modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onFilter(Filter.All) },
                                )
                            }
                        },
                    )
                }
                item(key = "new") {
                    FilterChip(
                        selected = filter == Filter.New,
                        onClick = { onFilter(Filter.New) },
                        label = { Text(if (newOnes.isEmpty()) "Sin ver" else "Sin ver · ${newOnes.size}") },
                    )
                }
                // los empezados van en su fila, arriba; la pastilla solo sale al verlos todos («Ver todos»)
                if (filter == Filter.Continue) {
                    item(key = "continue") {
                        FilterChip(
                            selected = filter == Filter.Continue,
                            onClick = { onFilter(Filter.Continue) },
                            label = { Text(if (continuing.isEmpty()) "Seguir viendo" else "Seguir viendo · ${continuing.size}") },
                        )
                    }
                }
                // lo que se ve, mezclado y seguido (con canales elegidos, entre unos cien vídeos suyos)
                if (videos.isNotEmpty()) {
                    item(key = "shuffle") {
                        FilterChip(
                            selected = false,
                            onClick = {
                                val chosen = filter.channelIds().mapNotNull { store.channel(it) }
                                scope.launch { Shuffle.play(context, store, videos, chosen) }
                            },
                            label = { Text(if (Shuffle.busy) "Mezclando…" else "Aleatorio") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }
        // arriba del todo, los empezados y sin acabar (el último que viste primero), de lado
        if (filter == Filter.All && continuing.isNotEmpty()) {
            item(key = "#continue") {
                ContinueRow(continuing, onAll = { onFilter(Filter.Continue) }, onDetails = onDetails)
            }
        }
        val message = when {
            store.channels.isEmpty() -> "Aún no tienes canales. Añade uno en \"Añadir\", o comparte un vídeo desde la app de YouTube con Mis Canales."
            feed.error != null && videos.isEmpty() -> "No se ha podido conectar con YouTube. Mira la conexión a internet."
            (feed.loading || feed.loadedAt == 0L) && videos.isEmpty() -> "Cargando tus canales…"
            videos.isEmpty() && filter == Filter.New -> "No tienes vídeos nuevos sin ver. ¡Al día!"
            shown.isEmpty() -> "Tus canales son todos «Para niños»: salen en Mis Canales Kids. Para verlos también aquí, en Ajustes."
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
            PhoneVideo(video, onClick = { playVideo(context, store, video, queue = videos.after(video)) }, onLongClick = { onDetails(video) })
        }
        // en un canal: sus vídeos anteriores, más allá de los últimos 15
        val channel = (filter as? Filter.OneChannel)?.let { store.channel(it.id) }
        if (channel != null && store.channels.isNotEmpty()) {
            val older = OlderVideos.of(channel)
            val shown = videos.map { it.vid }.toSet()
            val olderList = older.videos.filter { it.vid !in shown }
            items(olderList, key = { "older-" + it.vid }) { video ->
                PhoneVideo(video, onClick = { playVideo(context, store, video, queue = olderList.after(video)) }, onLongClick = { onDetails(video) })
            }
            item(key = "#older") {
                MoreButton(
                    loading = older.loading,
                    done = older.done,
                    label = if (older.videos.isEmpty()) "Ver vídeos anteriores" else "Ver más",
                    doneText = "Ya están todos los vídeos de ${channel.name}.",
                    error = older.error,
                    onClick = { older.load() },
                )
            }
        }
    }
}

/** El botón del final de una lista para cargar más, con su "cargando" y su "no hay más". */
/** En Inicio, los vídeos empezados y sin acabar, de lado, con «Ver todos». */
@Composable
private fun ContinueRow(videos: List<Video>, onAll: () -> Unit, onDetails: (Video) -> Unit) {
    val context = LocalContext.current
    val store = LocalStore.current
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Seguir viendo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (videos.size > 1) TextButton(onClick = onAll) { Text("Ver todos") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(videos.take(20), key = { it.vid }) { video ->
                Column(
                    Modifier.width(220.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(
                        onClick = { playVideo(context, store, video, queue = videos.after(video)) },
                        onLongClick = { onDetails(video) },
                    ),
                ) {
                    Thumbnail(video, Modifier.clip(RoundedCornerShape(10.dp)))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(
                                video.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                            )
                            Text(
                                video.channelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                        MoreOptions { onDetails(video) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MoreButton(loading: Boolean, done: Boolean, label: String, doneText: String, error: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            done -> Hint(doneText)
            else -> OutlinedButton(onClick = onClick) { Text(label) }
        }
        if (error != null) Hint(error, Modifier.padding(top = 8.dp))
    }
}

/** El buscador: en tus canales (también lo antiguo) o en todo YouTube. */
@Composable
private fun PhoneSearch(search: SearchState, onDetails: (Video) -> Unit) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun go() {
        keyboard?.hide()
        search.run(store, feed)
    }

    val preview = search.preview
    if (preview != null) {
        BackHandler { search.preview = null }
        ChannelPreview(preview, onBack = { search.preview = null }, onDetails = onDetails)
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "#box") {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
                OutlinedTextField(
                    search.query,
                    { search.query = it },
                    placeholder = { Text(if (search.mode == SearchMode.Mine) "Buscar en mis canales" else "Buscar en YouTube") },
                    singleLine = true,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = {
                        if (search.query.isNotEmpty()) {
                            IconButton(onClick = { search.clear() }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Borrar")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mode in SearchMode.entries) {
                        FilterChip(
                            selected = search.mode == mode,
                            onClick = {
                                search.mode = mode
                                if (search.query.isNotBlank()) go()
                            },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }
        val message = when {
            search.loading && search.videos.isEmpty() -> null
            search.error != null && search.videos.isEmpty() && search.channels.isEmpty() -> search.error
            search.searched.isEmpty() -> if (search.mode == SearchMode.Mine) {
                "Busca entre los vídeos de tus canales, también los antiguos. Escribe y pulsa la lupa del teclado."
            } else {
                "Busca vídeos y canales en todo YouTube. Solo salen los resultados: ni recomendaciones ni vídeos relacionados. " +
                    "Puedes ver un vídeo sin añadir su canal, o añadir el canal."
            }
            else -> null
        }
        if (message != null) item(key = "#message") { Hint(message, Modifier.padding(16.dp)) }
        if (search.loading && search.videos.isEmpty()) {
            item(key = "#loading") {
                Searching(
                    if (search.searchedMode == SearchMode.Mine) "Buscando en tus ${store.shownChannels.size} canales…" else "Buscando en YouTube…",
                )
            }
        }
        if (search.channels.isNotEmpty()) {
            item(key = "#channels") { SectionTitle("Canales") }
            items(search.channels, key = { "c-" + it.id }) { found ->
                FoundChannelRow(found, onOpen = { search.preview = found })
            }
            item(key = "#videos") { SectionTitle("Vídeos") }
        }
        items(search.videos, key = { it.vid }) { video ->
            PhoneVideo(video, onClick = { playVideo(context, store, video, queue = search.videos.after(video)) }, onLongClick = { onDetails(video) })
        }
        if (search.loading && search.videos.isNotEmpty()) {
            item(key = "#loading-more") { Searching("Buscando también los vídeos antiguos de tus canales…") }
        }
        if (search.searchedMode == SearchMode.YouTube && search.searched.isNotEmpty() && !search.loading) {
            item(key = "#more") {
                MoreButton(
                    loading = search.loadingMore,
                    done = search.more == null && search.videos.isNotEmpty(),
                    label = "Más resultados",
                    doneText = "No hay más resultados.",
                    error = search.error.takeIf { search.videos.isNotEmpty() },
                    onClick = { search.loadMore(store) },
                )
            }
        }
    }
}

@Composable
private fun Searching(text: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Hint(text, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Un canal de los resultados: tocarlo enseña sus vídeos; el botón lo añade a tus canales. */
@Composable
private fun FoundChannelRow(found: FoundChannel, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(Channel(found.id, found.name, found.avatar), 48.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(found.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(found.handle, found.subscribers).filter { it.isNotEmpty() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = Colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AddChannelButton(found)
    }
}

/** "Añadir" (o "Añadido" si ya es tuyo). */
@Composable
internal fun AddChannelButton(found: FoundChannel) {
    val store = LocalStore.current
    val library = LocalLibrary.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    if (store.has(found.id)) {
        Text("Añadido", color = Colors.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp))
    } else {
        Button(
            enabled = !adding,
            onClick = {
                adding = true
                scope.launch {
                    try {
                        // sin foto (un canal sacado de un vídeo): se busca su página para tenerla
                        if (found.avatar == null) library.add(found.id) else library.add(found.toChannel())
                        Toast.makeText(context, "Añadido: ${found.name}", Toast.LENGTH_SHORT).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, Library.addError(e), Toast.LENGTH_LONG).show()
                    } finally {
                        adding = false
                    }
                }
            },
        ) { Text(if (adding) "Añadiendo…" else "Añadir") }
    }
}

/** Los vídeos de un canal de los resultados, para ver qué sube antes de añadirlo. */
@Composable
private fun ChannelPreview(found: FoundChannel, onBack: () -> Unit, onDetails: (Video) -> Unit) {
    val store = LocalStore.current
    val context = LocalContext.current
    val older = remember(found.id) { OlderVideos.of(found.toChannel()) }
    LaunchedEffect(found.id) { if (older.videos.isEmpty()) older.load() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "#head") {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Volver") }
                Avatar(Channel(found.id, found.name, found.avatar), 40.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(found.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(found.subscribers, style = MaterialTheme.typography.bodySmall, color = Colors.muted, maxLines = 1)
                }
                AddChannelButton(found)
            }
        }
        items(older.videos, key = { it.vid }) { video ->
            PhoneVideo(video, onClick = { playVideo(context, store, video, queue = older.videos.after(video)) }, onLongClick = { onDetails(video) })
        }
        item(key = "#older") {
            MoreButton(
                loading = older.loading,
                done = older.done,
                label = "Ver más",
                doneText = "Ya están todos sus vídeos.",
                error = older.error,
                onClick = { older.load() },
            )
        }
    }
}

/** Un vídeo a todo lo ancho, como en YouTube: tocar lo pone, mantener abre sus opciones. */
/** El botón ⋮ de un vídeo: abre sus opciones, lo mismo que mantenerlo pulsado. */
@Composable
private fun MoreOptions(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            painterResource(R.drawable.ic_more_vert),
            contentDescription = "Más opciones",
            tint = Colors.muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun PhoneVideo(video: Video, onClick: () -> Unit, onLongClick: () -> Unit) {
    val store = LocalStore.current
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        Thumbnail(video)
        Row(Modifier.padding(start = 12.dp, end = 0.dp, top = 10.dp)) {
            Avatar(store.channel(video.channelId) ?: Channel(video.channelId, video.channelName), 36.dp)
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
            MoreOptions(onLongClick)
        }
    }
}

@Composable
private fun PhoneChannels(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val library = LocalLibrary.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<Channel?>(null) }
    var categorizing by remember { mutableStateOf<Channel?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "#head") {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mis canales", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Ajustes")
                }
            }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // su categoría (se guarda también en Home Assistant)
                        FilterChip(
                            selected = channel.category != null,
                            onClick = { categorizing = channel },
                            label = { Text(channel.category ?: "Categoría", fontSize = 12.sp, maxLines = 1) },
                            shape = CircleShape,
                        )
                        // para Mis Canales Kids (se guarda también en Home Assistant); Kids sale de los canales del principal
                        if (store.profile == Profile.Main) FilterChip(
                            selected = channel.kids,
                            onClick = {
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
                            label = { Text("Para niños", fontSize = 12.sp) },
                        )
                    }
                }
                // a la derecha, una encima de otra (para que quepan los botones de en medio): sus avisos (la
                // campana tachada, silenciado; sus vídeos siguen saliendo en Inicio) y quitarlo
                Column {
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                library.setMuted(channel.id, !channel.muted)
                                val text = if (channel.muted) "Avisaré otra vez de ${channel.name}" else "Ya no avisaré de ${channel.name}"
                                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se ha podido guardar: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Icon(
                            painterResource(if (channel.muted) R.drawable.ic_notifications_off else R.drawable.ic_notifications),
                            contentDescription = if (channel.muted) "Avisos de ${channel.name}: silenciados" else "Avisos de ${channel.name}: sí",
                            tint = if (channel.muted) Colors.muted else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { removing = channel }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Quitar ${channel.name}")
                    }
                }
            }
        }
    }
    val toCategorize = categorizing
    if (toCategorize != null) {
        CategoryDialog(
            toCategorize,
            onDismiss = { categorizing = null },
            onChoose = { category ->
                categorizing = null
                scope.launch {
                    try {
                        library.setCategory(toCategorize.id, category)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, "No se ha podido guardar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
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

/** En Inicio, cuando hay una versión nueva en GitHub: un botón para bajarla e instalarla. */
/**
 * Los ajustes del móvil, en su pantalla (desde la rueda de Inicio o de Canales): cómo se ven los
 * vídeos, avisos, Kids, iconos, versión y Home Assistant.
 */
@Composable
private fun PhoneSettings(onBack: () -> Unit) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item(key = "#head") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Volver") }
                Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item(key = "#settings") {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Vídeos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                SettingSwitch("Ocultar Shorts", store.hideShorts) {
                    store.toggleShorts()
                    feed.reload()
                }
                SettingSwitch("Ver en la app de YouTube", store.useYouTubeApp) { store.togglePlayer() }
                SettingSwitch("Subtítulos", store.subtitles) { store.toggleSubtitles() }
                SettingSwitch("Seguir sonando con la pantalla apagada", store.backgroundPlay) { store.toggleBackgroundPlay() }
                SettingSwitch("Saltar patrocinios (SponsorBlock)", store.sponsorBlock) { store.toggleSponsorBlock() }
                SettingSwitch("Audio en español si lo hay", store.spanishAudio) { store.toggleSpanishAudio() }
                Hint("En los vídeos doblados (MrBeast, por ejemplo). En el reproductor, el botón 🔊 cambia entre el español y el original.")
                SettingSwitch("Reproducción continua", store.autoNext) { store.toggleAutoNext() }
                Hint("Al acabar un vídeo, el siguiente de la lista desde la que lo abriste. En el reproductor, Atrás lo deja en una ventana pequeña para seguir por la app.")
                Text("Avisos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                SettingSwitch("Avisar de vídeos nuevos", store.notifyNew) {
                    store.toggleNotifyNew()
                    NewVideos.schedule(context, NewVideos.wanted(context))
                    if (store.notifyNew) (context as? MainActivity)?.askNotifications()
                }
                if (store.notifyNew) {
                    Hint(
                        "Cada media hora, más o menos, el móvil mira tus canales. Al tocar el aviso, el vídeo se " +
                            "abre aquí. Si también te llegan los avisos de Telegram, puedes apagar la automatización " +
                            "\"Mis Canales: aviso de vídeos nuevos\" en Home Assistant.",
                    )
                    if (!NewVideos.unrestricted(context)) {
                        Hint(
                            "Para que avise también con el móvil en reposo, la batería de la app tiene que estar " +
                                "\"Sin restricciones\".",
                        )
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        }) { Text("Quitar la restricción de batería") }
                    }
                    val latest = feed.videos.firstOrNull()
                    if (latest != null) {
                        TextButton(onClick = {
                            (context as? MainActivity)?.askNotifications()
                            NewVideos.preview(context, latest, store.profile)
                        }) { Text("Probar con el último vídeo") }
                    }
                }
                if (store.profile == Profile.Main) KidsAdultSettings()
                IconsSetting()
                UpdateSetting()
            }
        }
        item(key = "#ha") { HaSettings() }
    }
}

@Composable
private fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Button(
            onClick = { scope.launch { Updater.install(context) } },
            enabled = !Updater.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(Updater.label()) }
        Updater.message?.let { Hint(it) }
    }
}

/** En Ajustes: la versión que tienes y mirar si hay otra. */
@Composable
private fun UpdateSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
        Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
        Hint("Las versiones nuevas se bajan de GitHub. La app mira sola al abrirse y avisa en Inicio.")
        OutlinedButton(
            onClick = {
                scope.launch { if (Updater.available != null) Updater.install(context) else Updater.check(context, byHand = true) }
            },
            enabled = !Updater.busy,
        ) { Text(Updater.label()) }
        Updater.message?.let { Hint(it) }
    }
}

/** Qué iconos de la app salen en este móvil (cada uno, el suyo). El que se está usando no se quita desde dentro. */
@Composable
private fun IconsSetting() {
    val context = LocalContext.current
    val store = LocalStore.current
    var shown by remember { mutableStateOf(Launcher.icons.associate { it.second to Launcher.shown(context, it.second) }) }
    Column(Modifier.padding(top = 20.dp)) {
        Text("Iconos en este móvil", style = MaterialTheme.typography.titleMedium)
        Hint("En el móvil de cada uno, solo el suyo; en las teles, todos. Quitar un icono no borra nada.")
        for ((label, activity) in Launcher.icons) {
            val mine = activity == store.profile.activity
            SettingSwitch(if (mine) "$label (el que usas ahora)" else label, shown[activity] == true) {
                if (mine) {
                    Toast.makeText(context, "Es el que estás usando: se quita desde otro icono", Toast.LENGTH_LONG).show()
                } else {
                    runCatching { Launcher.set(context, activity, shown[activity] != true) }
                    shown = shown + (activity to Launcher.shown(context, activity))
                    // los avisos, solo de los perfiles con icono
                    NewVideos.schedule(context, NewVideos.wanted(context))
                }
            }
        }
    }
}

/** Elegir la categoría de un canal: una de las que ya hay, ninguna, o una nueva. */
@Composable
private fun CategoryDialog(channel: Channel, onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    val store = LocalStore.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categoría de ${channel.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (name in store.allCategories + listOf<String?>(null)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onChoose(name) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = channel.category == name, onClick = { onChoose(name) })
                        Text(name ?: "Sin categoría")
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Nueva categoría") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onChoose(text) }),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(text) }, enabled = text.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun SettingSwitch(label: String, checked: Boolean, onChange: () -> Unit) {
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
    var passing by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sincronizar con Home Assistant", style = MaterialTheme.typography.titleMedium)
        val error = library.syncError
        Hint(
            when {
                config == null -> "Los canales se guardan en una lista de tareas de tu Home Assistant y se comparten entre el móvil y la tele."
                // en otro perfil, lo normal es que aún no estén sus listas
                error != null && store.profile != Profile.Main ->
                    "Home Assistant: $error. Si aún no está, crea en Home Assistant (Local To-do) la lista " +
                        "\"${store.profile.haListName}\" (${config.entity}) para guardar ahí tus canales, y para lo demás " +
                        "\"${store.profile.haListName} progreso\" y \"${store.profile.haListName} listas\"."
                error != null -> "No responde: $error"
                else -> "Conectado" + (ago(library.syncedAt)?.let { ", sincronizado $it" } ?: "") + "." +
                    if (library.progressSynced == false) {
                        " Para compartir también por dónde vas en cada vídeo, crea en HA otra lista de tareas " +
                            "llamada \"${store.profile.haListName} progreso\" (${config?.let { ProgressSync.list(it) }.orEmpty()})."
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
        if (config != null) {
            // la tele no puede escribir el token: se lo pasa el móvil por la wifi de casa
            OutlinedButton(onClick = { passing = true }) { Text("Pasar a la tele o a otro móvil") }
            Hint(
                "Para conectar un Fire TV con Mis Canales: en la tele, Mis canales → Home Assistant, y aquí el código " +
                    "que sale. A otro móvil: en el otro, ⚙ Ajustes → \"Recibir de otro móvil\".",
            )
        } else {
            // o desde otro móvil que ya lo tenga (así no hay que copiar el token)
            OutlinedButton(onClick = { receiving = true }) { Text("Recibir de otro móvil") }
            Hint("Si otro móvil de casa ya tiene Mis Canales conectado: allí, ⚙ Ajustes → \"Pasar a la tele o a otro móvil\".")
        }
        Spacer(Modifier.height(24.dp))
    }
    if (passing && config != null) PassToTvDialog(config, onDismiss = { passing = false })
    if (receiving) ReceiveDialog(onDismiss = { receiving = false })
}

/** Recibe la conexión con Home Assistant de otro móvil (como la tele): enseña un código y espera. */
@Composable
private fun ReceiveDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val library = LocalLibrary.current
    val receiver = remember { Pairing.Receiver(context.applicationContext) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(receiver) {
        receiver.run { config ->
            message = "Probando Home Assistant…"
            val result = try {
                library.connect(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se ha podido conectar con Home Assistant: ${e.message}"
            }
            message = result
            result
        }
    }
    DisposableEffect(receiver) { onDispose { receiver.stop() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recibir de otro móvil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "En el otro móvil: ⚙ Ajustes → \"Pasar a la tele o a otro móvil\". Elige \"${Pairing.deviceName(context)}\" " +
                        "y escribe este código:",
                )
                Text(receiver.code, style = MaterialTheme.typography.displaySmall, letterSpacing = 8.sp)
                Text("Los dos móviles, en la misma wifi.", color = Colors.muted)
                message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Busca teles con la pantalla de Home Assistant abierta y le manda a una la conexión, con su código. */
@Composable
private fun PassToTvDialog(config: HaConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tvs by remember { mutableStateOf<List<NsdServiceInfo>>(emptyList()) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val stop = Pairing.discover(context) { found ->
            tvs = found
            if (chosen == null && found.size == 1) chosen = found[0].serviceName
            if (found.none { it.serviceName == chosen }) chosen = found.singleOrNull()?.serviceName
        }
        onDispose { stop() }
    }
    val tv = tvs.firstOrNull { it.serviceName == chosen }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pasar a la tele o a otro móvil") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("En la tele: Mis canales → Home Assistant. Sale un código. El móvil y la tele, en la misma wifi.")
                if (tvs.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Buscando teles…")
                    }
                }
                for (found in tvs) {
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = found.serviceName },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen == found.serviceName, onClick = { chosen = found.serviceName })
                        Text(Pairing.name(found))
                    }
                }
                OutlinedTextField(
                    code,
                    { code = it.filter(Char::isDigit).take(4) },
                    label = { Text("Código de la tele") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                result?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && tv != null && code.length == 4,
                onClick = {
                    val target = tv ?: return@TextButton
                    busy = true
                    result = "Enviando… (la tele prueba la conexión)"
                    scope.launch {
                        result = try {
                            "La tele dice: " + Pairing.send(context, target, code, config)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "No se ha podido: ${e.message}"
                        }
                        busy = false
                    }
                },
            ) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun PhoneAdd(shared: MutableState<String?>, onList: (String) -> Unit) {
    val store = LocalStore.current
    val library = LocalLibrary.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    // una lista de YouTube (al compartirla o pegar su enlace): se guarda como copia en Listas
    var playlist by remember { mutableStateOf<FoundPlaylist?>(null) }
    var listName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }

    fun findPlaylist(text: String) {
        busy = true
        failed = false
        saved = null
        playlist = null
        message = "Buscando la lista…"
        scope.launch {
            try {
                val found = Search.playlist(text)
                Log.i("MisCanales", "Lista de YouTube: «${found.title}», ${found.videos.size} vídeos" + if (found.mix) " (mix)" else "")
                playlist = found
                listName = found.title
                query = ""
                message = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i("MisCanales", "Lista de YouTube: no se puede leer (${e.message})")
                failed = true
                message = e.message?.takeIf { it.endsWith(".") } ?: "No se ha podido leer la lista. Prueba otra vez."
            } finally {
                busy = false
            }
        }
    }

    fun savePlaylist(found: FoundPlaylist) {
        val name = store.playlists.clean(listName) ?: return
        keyboard?.hide()
        saving = 0
        scope.launch {
            // aunque se cambie de pestaña, se acaba de guardar
            val added = withContext(NonCancellable) {
                store.playlists.addAll(context.applicationContext, name, found.videos) { saving = it }
            }
            saving = null
            playlist = null
            failed = false
            saved = name
            val total = store.playlists.videos(name).size
            message = when {
                added == found.videos.size -> "Guardada «$name»: $added vídeos."
                added == 0 -> "Esos vídeos ya estaban en «$name»."
                else -> "Añadidos $added vídeos a «$name» (ya tiene $total)."
            } + if (store.playlists.synced == true) " También sale en la tele." else ""
        }
    }

    fun add(input: String) {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        keyboard?.hide()
        // el enlace de una lista (…?list=…): a guardarla en Listas
        if (Search.playlistIdIn(text) != null) {
            findPlaylist(text)
            return
        }
        saved = null
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
        Text("Añadir canal o lista", style = MaterialTheme.typography.headlineSmall)
        Hint("Escribe el @nombre del canal tal como sale en YouTube, o pega su enlace o el de una lista.")
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
        saved?.let { name -> OutlinedButton(onClick = { onList(name) }) { Text("Ver la lista") } }
        playlist?.let { found ->
            PlaylistImport(
                found,
                listName,
                onName = { listName = it },
                saving = saving,
                exists = store.playlists.clean(listName)?.let { it in store.playlists.names } == true,
                onSave = { savePlaylist(found) },
                onCancel = { playlist = null },
            )
        }
        Hint(
            "Más fácil: en la app de YouTube, en cualquier vídeo, Compartir → Mis Canales, y se añade su canal." +
                if (store.ha != null) " Se guarda también en Home Assistant y aparece en la tele." else "",
        )
        Hint("Con una lista de YouTube (la de alguien o un mix), igual: Compartir → Mis Canales, y se guarda una copia en Listas.")
    }
}

/** Una lista de YouTube a punto de guardarse: su nombre (se puede cambiar), cuántos vídeos trae y el botón. */
@Composable
private fun PlaylistImport(
    found: FoundPlaylist,
    name: String,
    onName: (String) -> Unit,
    saving: Int?,
    exists: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colors.bg2).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (found.mix) "Mix de YouTube" else "Lista de YouTube", style = MaterialTheme.typography.labelMedium, color = Colors.muted)
        Text(found.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${found.videos.size} vídeos" + when {
                found.mix -> ". YouTube cambia los mixes cada vez: se guardan los de ahora."
                found.videos.size >= Search.MAX_PLAYLIST -> " (los primeros; no se guardan más)."
                else -> "."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Colors.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            found.videos.take(3).forEach { video ->
                Thumbnail(video, Modifier.weight(1f).clip(RoundedCornerShape(8.dp)))
            }
        }
        OutlinedTextField(
            name,
            onName,
            label = { Text("Nombre de la lista") },
            singleLine = true,
            enabled = saving == null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (saving != null) {
            LinearProgressIndicator(progress = { saving / found.videos.size.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("Guardando $saving de ${found.videos.size}…", style = MaterialTheme.typography.bodySmall, color = Colors.muted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave, enabled = name.isNotBlank()) {
                    Text(if (exists) "Añadir a esa lista" else "Guardar en Listas")
                }
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
            if (exists) Hint("Ya tienes una lista con ese nombre: se añaden los vídeos que no tenga.")
        }
    }
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Colors.muted, modifier = modifier)
}

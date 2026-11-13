package es.miscanales

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** El color de Mis Canales Kids. */
internal val KidsAmber = Color(0xFFFFB300)

/** Los límites que se pueden elegir, en minutos (0: sin límite). */
internal val KidsLimits = listOf(0 to "Sin límite", 30 to "30 min", 60 to "1 h", 90 to "1 h 30", 120 to "2 h")

/** Mis Canales Kids en el móvil. */
@Composable
fun KidsPhoneApp(store: Store, feed: Feed, library: Library) {
    MaterialTheme(colorScheme = phoneColors(KidsAmber)) {
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
            Scaffold(
                containerColor = Colors.bg,
                topBar = {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_smart_display_filled),
                            contentDescription = null,
                            tint = KidsAmber,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            "Mis Canales Kids",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        if (left != null && !settings) {
                            Text("Quedan ${KidsTime.text(left)}", color = Colors.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!settings) {
                            IconButton(onClick = { pin = PinFlow(store) { pin = null; settings = true } }) {
                                Icon(painterResource(R.drawable.ic_lock), contentDescription = "Ajustes de los mayores")
                            }
                        }
                    }
                },
            ) { padding ->
                Column(Modifier.padding(padding)) {
                    when {
                        settings -> KidsPhoneSettings(
                            onClose = { settings = false },
                            onChangePin = { pin = PinFlow(store, create = true) { pin = null } },
                        )
                        store.kidsChannels.isEmpty() -> KidsMessage(
                            "Aún no hay canales para niños.",
                            "Los mayores los eligen en Mis Canales → Canales, tocando «Para niños» en cada canal.",
                        )
                        left != null && left <= 0 -> KidsMessage(
                            "¡Se acabó el tiempo de hoy!",
                            "Mañana más. Un mayor puede dar media hora más con el PIN.",
                            button = "Media hora más (PIN)",
                            onButton = { pin = PinFlow(store) { pin = null; KidsTime.grant(context); tick++ } },
                        )
                        else -> KidsPhoneVideos()
                    }
                }
            }
            pin?.let { PhonePinDialog(it, onDismiss = { pin = null }) }
        }
    }
}

/** Los vídeos de los canales para niños: en lista o, en modo peque, solo las miniaturas grandes. */
@Composable
private fun KidsPhoneVideos() {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val context = LocalContext.current
    var only by remember { mutableStateOf<String?>(null) }
    val channels = store.kidsChannels.sortedBy { it.name.lowercase() }
    val ids = channels.map { it.id }.toSet()
    val videos = feed.videos.filter { it.channelId in ids && (only == null || it.channelId == only) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (store.kidsSmall) 12.dp else 0.dp),
    ) {
        if (channels.size > 1) {
            item(key = "#chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip(selected = only == null, onClick = { only = null }, label = { Text("Todos") }) }
                    items(channels, key = { it.id }) { channel ->
                        FilterChip(
                            selected = only == channel.id,
                            onClick = { only = if (only == channel.id) null else channel.id },
                            label = { Text(channel.name) },
                            leadingIcon = { Avatar(channel, 24.dp) },
                        )
                    }
                }
            }
        }
        if (videos.isEmpty()) {
            item(key = "#empty") {
                Hint(if (feed.loading) "Cargando…" else "No hay vídeos todavía.", Modifier.padding(16.dp))
            }
        }
        items(videos, key = { it.vid }) { video ->
            if (store.kidsSmall) {
                Thumbnail(
                    video,
                    Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp))
                        .clickable { playVideo(context, store, video) },
                )
            } else {
                PhoneVideo(video, onClick = { playVideo(context, store, video) }, onLongClick = {})
            }
        }
        // con un canal elegido, sus vídeos anteriores
        val channel = only?.let { store.channel(it) }
        if (channel != null && !store.kidsSmall) {
            val older = OlderVideos.of(channel)
            val shown = videos.map { it.vid }.toSet()
            items(older.videos.filter { it.vid !in shown }, key = { "older-" + it.vid }) { video ->
                PhoneVideo(video, onClick = { playVideo(context, store, video) }, onLongClick = {})
            }
            item(key = "#older") {
                MoreButton(
                    loading = older.loading,
                    done = older.done,
                    label = if (older.videos.isEmpty()) "Ver vídeos anteriores" else "Ver más",
                    doneText = "Ya están todos.",
                    error = older.error,
                    onClick = { older.load() },
                )
            }
        }
    }
}

@Composable
private fun KidsMessage(title: String, text: String, button: String? = null, onButton: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(text, color = Colors.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        if (button != null) Button(onClick = onButton, modifier = Modifier.padding(top = 24.dp)) { Text(button) }
    }
}

/** Los ajustes de Kids (tras el PIN): tiempo al día, modo peque y el PIN. */
@Composable
private fun KidsPhoneSettings(onClose: () -> Unit, onChangePin: () -> Unit) {
    val store = LocalStore.current
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Ajustes de Kids", style = MaterialTheme.typography.headlineSmall)
            Text("Tiempo al día", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            Row(
                Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for ((minutes, label) in KidsLimits) {
                    FilterChip(
                        selected = store.kidsLimit == minutes,
                        onClick = { store.changeKidsLimit(minutes) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }
            Hint(
                "Hoy llevan ${KidsTime.text(KidsTime.used(context))} viendo vídeos en este aparato. " +
                    "Al acabarse el tiempo, el vídeo se para; con el PIN se da media hora más.",
                Modifier.padding(top = 4.dp),
            )
            Text("Modo peque", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            SettingSwitch("Miniaturas grandes y sin textos", store.kidsSmall) { store.toggleKidsSmall() }
            Hint("Para quien aún no sabe leer.")
            Text("PIN", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            OutlinedButton(onClick = onChangePin, modifier = Modifier.padding(top = 4.dp)) { Text("Cambiar el PIN") }
            Hint(
                "Los canales para niños se eligen en Mis Canales (la app de los mayores) → Canales, " +
                    "tocando «Para niños» en cada canal.",
                Modifier.padding(top = 20.dp),
            )
            Button(onClick = onClose, modifier = Modifier.padding(top = 24.dp)) { Text("Listo") }
        }
    }
}

/** El PIN en el móvil: un diálogo con el teclado numérico. */
@Composable
internal fun PhonePinDialog(flow: PinFlow, onDismiss: () -> Unit) {
    var text by remember(flow.prompt) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(flow.prompt) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
        title = { Text(flow.prompt, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(Pin.LENGTH)
                        text = digits
                        if (digits.length == Pin.LENGTH) {
                            text = ""
                            flow.enter(digits)
                        }
                    },
                    singleLine = true,
                    placeholder = { Text("····") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.focusRequester(focus),
                )
                flow.error?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** En la app de los mayores: la parte de Kids de los ajustes. */
@Composable
internal fun KidsAdultSettings() {
    val store = LocalStore.current
    var pin by remember { mutableStateOf<PinFlow?>(null) }
    Column(Modifier.padding(top = 20.dp)) {
        Text("Mis Canales Kids", style = MaterialTheme.typography.titleMedium)
        Hint(
            "Kids es otro icono de la app, para los niños: solo los canales marcados «Para niños» " +
                "(arriba, en cada canal), sin buscar en YouTube ni añadir canales, y con tiempo al día y " +
                "modo peque en sus ajustes (con el PIN).",
        )
        SettingSwitch("Pedir PIN al abrir Mis Canales", store.askPin) {
            if (store.askPin) {
                store.toggleAskPin()
            } else {
                // para activarlo hace falta un PIN (y que lo sepas)
                pin = PinFlow(store) {
                    pin = null
                    store.toggleAskPin()
                    AppLock.unlock()
                }
            }
        }
        Hint("Para un aparato que usan también los niños: así no entran en la app de los mayores.")
        if (store.hasPin) {
            TextButton(onClick = { pin = PinFlow(store) { pin = PinFlow(store, create = true) { pin = null } } }) {
                Text("Cambiar el PIN")
            }
        }
    }
    pin?.let { PhonePinDialog(it, onDismiss = { pin = null }) }
}

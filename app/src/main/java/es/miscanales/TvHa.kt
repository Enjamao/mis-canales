package es.miscanales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Home Assistant en la tele. Lo más fácil, desde el móvil: mientras esta pantalla está abierta, la tele
 * se anuncia en la wifi de casa y el móvil le manda la conexión con el código que sale aquí. Si no, a
 * mano (con el teclado de la app Amazon Fire TV del móvil se puede pegar el token).
 * En dos columnas, sin desplazarse, para que el código se vea siempre.
 */
@Composable
fun HaScreen(page: HaPage) {
    val store = LocalStore.current
    val library = LocalLibrary.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val receiver = remember { Pairing.Receiver(context.applicationContext) }

    suspend fun connect(config: HaConfig): String {
        page.busy = true
        page.message = "Probando Home Assistant…"
        val message = try {
            library.connect(config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "No se ha podido conectar con Home Assistant: ${e.message}"
        }
        page.message = message
        page.busy = false
        return message
    }

    // mientras la pantalla está abierta, el móvil puede mandar la conexión
    LaunchedEffect(receiver) { receiver.run { config -> connect(config) } }
    DisposableEffect(receiver) { onDispose { receiver.stop() } }

    val config = store.ha
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
        PageHead(
            "Home Assistant",
            if (config != null) {
                "Conectado a ${config.url}: aquí salen los mismos canales que en el móvil."
            } else {
                "Para tener aquí los mismos canales que en el móvil (y lo que marques en uno, en los dos)."
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Desde el móvil (lo más fácil)", style = Type.pageTitle.copy(fontSize = 20.sp))
                Text(
                    "En Mis Canales del móvil: Canales → Sincronizar con Home Assistant → «Pasar a la tele». " +
                        "Elige «${Pairing.deviceName(context)}» y escribe este código:",
                    style = Type.body.copy(fontSize = 16.sp, lineHeight = 23.sp),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    receiver.code,
                    style = Type.pageTitle.copy(fontSize = 52.sp, letterSpacing = 10.sp),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    "El móvil y la tele tienen que estar en la misma wifi.",
                    style = Type.sub.copy(fontSize = 14.sp),
                )
                page.message?.let {
                    Text(it, style = Type.body.copy(fontSize = 16.sp, lineHeight = 23.sp), modifier = Modifier.padding(top = 16.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("O a mano", style = Type.pageTitle.copy(fontSize = 20.sp))
                Text(
                    "Con el teclado de la app Amazon Fire TV del móvil se puede pegar el token.",
                    style = Type.sub.copy(fontSize = 14.sp),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                TvTextField(page.url, { page.url = it }, "Dirección de fuera (https://…duckdns.org:8123)", onDone = {}, modifier = Modifier.restoreFocus(page, "url"))
                Spacer(Modifier.height(10.dp))
                TvTextField(page.token, { page.token = it }, "Token de acceso de larga duración", onDone = {}, modifier = Modifier.restoreFocus(page, "token"))
                Spacer(Modifier.height(10.dp))
                TvTextField(page.localUrl, { page.localUrl = it }, "Dirección de casa (opcional)", onDone = {}, modifier = Modifier.restoreFocus(page, "local"))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill(
                        if (page.busy) "Probando…" else "Conectar",
                        {
                            if (!page.busy && page.url.isNotBlank() && page.token.isNotBlank()) {
                                keyboard?.hide()
                                scope.launch { connect(HaConfig(page.url.trim(), page.token.trim(), page.localUrl.trim())) }
                            } else if (page.url.isBlank() || page.token.isBlank()) {
                                page.message = "Para conectar a mano hacen falta la dirección y el token."
                            }
                        },
                        // el foco empieza aquí (en una caja de texto saldría el teclado y taparía el código)
                        Modifier.restoreFocus(page, "connect", first = true),
                    )
                    if (config != null) {
                        Pill(
                            "Desconectar",
                            {
                                library.disconnect()
                                page.message = "Desconectado. Los canales se quedan en la tele."
                            },
                            Modifier.restoreFocus(page, "disconnect"),
                        )
                    }
                }
            }
        }
    }
}

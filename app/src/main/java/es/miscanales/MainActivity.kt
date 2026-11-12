package es.miscanales

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var feed: Feed
    private lateinit var library: Library
    private val scope = MainScope()

    /** Lo que se ha compartido con la app (un enlace de YouTube), para añadir su canal. */
    private val shared = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        feed = Feed(store, filesDir)
        library = Library(store, feed)
        handle(intent)
        val tv = isTv()
        setContent {
            if (tv) {
                MisCanalesTheme { App(store, feed, library) }
            } else {
                PhoneApp(store, feed, library, shared)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // al abrir la app, y al volver de YouTube o de otra app, si hace rato de la última carga
        feed.reloadIfStale()
        // los subtítulos se pueden cambiar desde el reproductor
        store.reloadSettings()
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) shared.value = intent.getStringExtra(Intent.EXTRA_TEXT)
        // Home Assistant en la tele, por ADB (escribir el token con el mando es imposible):
        // adb shell am start -n es.miscanales/.MainActivity --es ha_url URL --es ha_token TOKEN [--es ha_local_url URL]
        // Hacen falta la dirección y el token juntos, para que nadie pueda mandar tu token a otra dirección.
        val url = intent.getStringExtra("ha_url")
        val token = intent.getStringExtra("ha_token")
        if (!url.isNullOrBlank() && !token.isNullOrBlank()) {
            val config = HaConfig(
                url.trim(),
                token.trim(),
                intent.getStringExtra("ha_local_url").orEmpty().trim(),
                intent.getStringExtra("ha_entity")?.trim()?.ifBlank { null } ?: HaConfig.DEFAULT_ENTITY,
            )
            scope.launch {
                val message = try {
                    library.connect(config)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "No se ha podido conectar con Home Assistant: ${e.message}"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

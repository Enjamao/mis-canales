package es.miscanales

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Mis Canales Kids: el mismo programa con su propio icono, siempre en modo niños (ver [MainActivity]). */
class KidsActivity : MainActivity() {
    override val kidsMode = true
}

open class MainActivity : ComponentActivity() {
    /** Si esta es la entrada de los niños (el icono de Mis Canales Kids). */
    protected open val kidsMode = false

    /** "Pedir PIN al abrir": la app de los mayores, tapada por el PIN hasta que se escribe. */
    private val locked = mutableStateOf(false)

    private lateinit var store: Store
    private lateinit var feed: Feed
    private lateinit var library: Library
    private val scope = MainScope()

    /** Lo que se ha compartido con la app (un enlace de YouTube), para añadir su canal. */
    private val shared = mutableStateOf<String?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(this, "Sin permiso para avisar: no saldrán los avisos de vídeos nuevos", Toast.LENGTH_LONG).show()
        }
    }

    /** Pide permiso para poner avisos (desde Android 13), si no lo tiene. */
    fun askNotifications() {
        if (!NewVideos.allowed(this) && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** De los mayores a Kids: se cierra esta, para que Atrás en Kids no vuelva a los mayores. */
    fun openKids() {
        KidsMode.set(this, true)
        startActivity(Intent(this, KidsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    /** De Kids a los mayores (después del PIN): ya no hace falta volver a escribirlo. */
    fun openAdults() {
        KidsMode.set(this, false)
        AppLock.unlock()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(FROM_KIDS, true))
        finish()
    }

    private companion object {
        /** Viene de Kids, donde ya se ha escrito el PIN. */
        const val FROM_KIDS = "fromKids"
    }

    /** Abrir la app (el icono) con el último modo en Kids: a Kids. */
    private fun shouldGoToKids(intent: Intent?): Boolean =
        !kidsMode && KidsMode.last(this) && (intent == null || intent.action == Intent.ACTION_MAIN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (shouldGoToKids(intent)) {
            openKids()
            return
        }
        if (kidsMode) KidsMode.set(this, true)
        store = Store(this, kids = kidsMode)
        feed = Feed(store, filesDir)
        library = Library(store, feed)
        if (kidsMode) {
            setContent {
                if (isTv()) MisCanalesTheme { KidsTvApp(store, feed, library) } else KidsPhoneApp(store, feed, library)
            }
            return
        }
        // al abrirla de nuevo (no al girar), la app de los mayores pide el PIN si así está puesto
        if (savedInstanceState == null && !intent.getBooleanExtra(FROM_KIDS, false)) AppLock.leaving()
        locked.value = store.askPin && AppLock.locked
        // al volver a crearse (al girar, por ejemplo) no se vuelve a poner el vídeo que llegó de fuera
        handle(intent, fresh = savedInstanceState == null)
        val tv = isTv()
        if (!tv) {
            // los avisos de vídeos nuevos (la primera vez que se abre con ellos, se pide el permiso)
            NewVideos.schedule(this, store.notifyNew)
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            if (store.notifyNew && savedInstanceState == null && !prefs.getBoolean("askedNotifications", false)) {
                prefs.edit().putBoolean("askedNotifications", true).apply()
                askNotifications()
            }
        }
        setContent {
            if (locked.value) {
                LockScreen(tv)
            } else if (tv) {
                MisCanalesTheme { App(store, feed, library) }
            } else {
                PhoneApp(store, feed, library, shared)
            }
        }
    }

    /** La app de los mayores, cerrada con el PIN: Cancelar (o Atrás) sale de la app. */
    @androidx.compose.runtime.Composable
    private fun LockScreen(tv: Boolean) {
        val flow = androidx.compose.runtime.remember {
            PinFlow(store, create = false) {
                AppLock.unlock()
                locked.value = false
            }
        }
        if (tv) {
            MisCanalesTheme { TvPinPad(flow, onCancel = { finish() }) }
        } else {
            androidx.compose.material3.MaterialTheme(colorScheme = phoneColors()) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(Colors.bg),
                ) { PhonePinDialog(flow, onDismiss = { finish() }) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::store.isInitialized) return
        // la de los mayores, al volver a ella con el último modo en Kids (abierto con su icono): a Kids
        if (!kidsMode && KidsMode.last(this)) {
            openKids()
            return
        }
        if (!kidsMode && store.askPin && AppLock.locked) locked.value = true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // al salir con Home o a otra app: al volver, PIN
        if (!kidsMode) AppLock.leaving()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!kidsMode) handle(intent, fresh = true)
    }

    override fun onResume() {
        super.onResume()
        if (!::store.isInitialized || isFinishing) return
        // al abrir la app, y al volver de YouTube o de otra app, si hace rato de la última carga
        feed.reloadIfStale()
        // los subtítulos se pueden cambiar desde el reproductor
        store.reloadSettings()
    }

    private fun handle(intent: Intent?, fresh: Boolean) {
        if (intent == null) return
        // "Ver en la tele" desde el móvil (ver Tele): Home Assistant, por ADB, abre la app con
        // --es play ID_DEL_VIDEO --ei start SEGUNDO
        val play = intent.getStringExtra("play")
        // y los avisos de vídeos nuevos (ver NewVideos), que traen además el título y el canal
        if (fresh && play != null && Regex("[\\w-]{11}").matches(play)) {
            val start = intent.getIntExtra("start", -1)
            val sent = intent.getStringExtra("title")?.let {
                Video(
                    play, it, intent.getStringExtra("channelId").orEmpty(),
                    intent.getStringExtra("channelName").orEmpty(), intent.getLongExtra("published", 0),
                )
            }
            scope.launch {
                val video = feed.videos.firstOrNull { it.vid == play } ?: sent ?: YouTube.info(play) ?: Video(play, "", "", "", 0)
                playVideo(this@MainActivity, store, video, start)
            }
        }
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

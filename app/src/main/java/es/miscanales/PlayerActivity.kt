package es.miscanales

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * El reproductor propio: el reproductor oficial de YouTube (el de los vídeos insertados en webs)
 * dentro de la app. Atrás vuelve siempre a Mis Canales, y al acabar el vídeo también, sin recomendaciones.
 */
class PlayerActivity : ComponentActivity() {
    private var web: WebView? = null
    private val state = PlayerState()
    private var vid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vid = intent.getStringExtra(EXTRA_VID) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MisCanalesTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(factory = ::createWebView, modifier = Modifier.fillMaxSize())
                    PlayerOverlay(state, title, channel)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        // las teclas del mando las lleva la actividad, no la página
        isFocusable = false
        isFocusableInTouchMode = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        addJavascriptInterface(PlayerBridge(this@PlayerActivity), "Android")
        // YouTube solo reproduce vídeos insertados si sabe quién los inserta (si no, da el error 153).
        // La página se presenta como https://es.miscanales, el ID de la app.
        loadDataWithBaseURL("$ORIGIN/", playerHtml(vid), "text/html", "utf-8", null)
        web = this
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val script = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> "toggle()"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "play()"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "pause()"
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> "seek(-10)"
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "seek(10)"
            // arriba y abajo solo enseñan el panel
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> ""
            // Atrás, Home, volumen…: lo normal
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            state.touchedAt = System.currentTimeMillis()
            if (script.isNotEmpty()) web?.evaluateJavascript(script, null)
        }
        return true
    }

    fun onPlayerState(code: Int) {
        when (code) {
            ENDED -> finish()
            PLAYING -> {
                state.playing = true
                state.started = true
            }
            PAUSED -> state.playing = false
        }
    }

    fun onProgress(time: Double, duration: Double) {
        state.time = time
        state.duration = duration
    }

    /** El vídeo no se puede ver insertado (lo ha prohibido su autor, por ejemplo): a la app de YouTube. */
    fun onPlayerError(code: Int) {
        if (isFinishing) return
        Toast.makeText(this, "Este vídeo no se puede ver aquí (error $code). Lo abro en YouTube.", Toast.LENGTH_LONG).show()
        playOnYouTube(vid)
        finish()
    }

    override fun onPause() {
        // con Home, que no siga sonando de fondo
        web?.evaluateJavascript("pause()", null)
        web?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_VID = "vid"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CHANNEL = "channel"
        private const val ORIGIN = "https://es.miscanales"

        // los estados del reproductor de YouTube
        private const val ENDED = 0
        private const val PLAYING = 1
        private const val PAUSED = 2

        fun start(context: Context, video: Video) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_VID, video.vid)
                    .putExtra(EXTRA_TITLE, video.title)
                    .putExtra(EXTRA_CHANNEL, video.channelName),
            )
        }

        private fun playerHtml(vid: String) = """
            <!DOCTYPE html>
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html, body, #p { margin: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }</style>
            </head><body>
            <div id="p"></div>
            <script>
            var player = null;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('p', {
                width: '100%', height: '100%', videoId: '$vid',
                playerVars: { autoplay: 1, controls: 0, rel: 0, playsinline: 1, fs: 0, disablekb: 1,
                              iv_load_policy: 3, cc_load_policy: 0, hl: 'es', origin: '$ORIGIN' },
                events: {
                  onReady: function (e) { e.target.playVideo(); },
                  onStateChange: function (e) { Android.state(e.data); },
                  onError: function (e) { Android.error(e.data); }
                }
              });
              setInterval(function () {
                if (player && player.getCurrentTime) Android.progress(player.getCurrentTime() || 0, player.getDuration() || 0);
              }, 500);
            }
            function ready() { return player && player.getPlayerState; }
            function play() { if (ready()) player.playVideo(); }
            function pause() { if (ready()) player.pauseVideo(); }
            function toggle() { if (!ready()) return; if (player.getPlayerState() == 1) player.pauseVideo(); else player.playVideo(); }
            function seek(s) { if (ready()) player.seekTo(Math.max(0, player.getCurrentTime() + s), true); }
            </script>
            <script src="https://www.youtube.com/iframe_api"></script>
            </body></html>
        """.trimIndent()
    }
}

/** Lo que llama el JavaScript del reproductor. Llega en otro hilo, así que se pasa al principal. */
class PlayerBridge(private val activity: PlayerActivity) {
    @JavascriptInterface
    fun state(code: Int) = activity.runOnUiThread { activity.onPlayerState(code) }

    @JavascriptInterface
    fun progress(time: Double, duration: Double) = activity.runOnUiThread { activity.onProgress(time, duration) }

    @JavascriptInterface
    fun error(code: Int) = activity.runOnUiThread { activity.onPlayerError(code) }
}

class PlayerState {
    var started by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var time by mutableStateOf(0.0)
    var duration by mutableStateOf(0.0)

    /** La última vez que se tocó el mando: el panel se ve unos segundos después. */
    var touchedAt by mutableStateOf(System.currentTimeMillis())
}

/** El título arriba y la barra de progreso abajo. Se ve al empezar, en pausa y al tocar el mando. */
@Composable
private fun PlayerOverlay(state: PlayerState, title: String, channel: String) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    val visible = !state.started || !state.playing || now - state.touchedAt < 4000
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .85f), Color.Transparent)))
                    .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 48.dp),
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(channel, fontSize = 15.sp, color = Colors.muted, modifier = Modifier.padding(top = 4.dp))
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                    .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 32.dp),
            ) {
                val fraction = if (state.duration > 0) (state.time / state.duration).toFloat().coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .3f))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Colors.accent))
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val status = when {
                        !state.started -> "Cargando…"
                        !state.playing -> "En pausa"
                        else -> null
                    }
                    Text(
                        if (state.duration > 0) "${clock(state.time)} / ${clock(state.duration)}" else "",
                        fontSize = 15.sp,
                        color = Color.White,
                    )
                    if (status != null) Text("   ·   $status", fontSize = 15.sp, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text("OK: pausa   ◀ ▶: 10 s   Atrás: salir", fontSize = 13.sp, color = Colors.muted)
                }
            }
        }
    }
}

/** 75 → "1:15"; 3725 → "1:02:05". */
private fun clock(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

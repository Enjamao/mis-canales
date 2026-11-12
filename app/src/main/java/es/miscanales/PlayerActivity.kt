package es.miscanales

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.serialization.json.JsonPrimitive

/**
 * El reproductor propio: el reproductor oficial de YouTube (el de los vídeos insertados en webs)
 * dentro de la app. Atrás vuelve siempre a Mis Canales, y al acabar el vídeo también, sin recomendaciones.
 *
 * La pantalla es solo la página web, sin Compose ni nada más detrás o encima: en algunas teles
 * (el Fire TV, por ejemplo) el vídeo se pinta en una capa aparte por detrás de la ventana, y
 * cualquier fondo que haya entre medias lo tapa: se oye, pero se ve negro. El título y la barra
 * de progreso están dentro de la propia página.
 */
class PlayerActivity : ComponentActivity() {
    private var web: WebView? = null
    private var vid = ""
    private var tv = false

    /** En el móvil: seguir sonando con la pantalla apagada o desde la ventana flotante. */
    private var background = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vid = intent.getStringExtra(EXTRA_VID) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        val subtitles = intent.getBooleanExtra(EXTRA_SUBTITLES, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        current = this
        tv = isTv()
        background = !tv && intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        if (!tv) {
            // en el móvil, a pantalla completa (las barras salen deslizando)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            // al salir de la app (Home), el vídeo sigue en una ventana flotante
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setPictureInPictureParams(pipParams())
        }
        if (background) {
            // el aviso con los controles necesita permiso a partir de Android 13
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            // mantiene la app viva con la pantalla apagada, con play y pausa en la pantalla de bloqueo
            PlaybackService.start(this, title, channel)
        }
        setContentView(createWebView(title, channel, subtitles, tv))
    }

    private fun pipParams(): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true) }
        .build()

    // antes de Android 12 la ventana flotante no se abre sola
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!tv && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            runCatching { enterPictureInPictureMode(pipParams()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // cerrar la ventana flotante (deslizándola fuera) para el vídeo
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) finish()
    }

    // al tocar el aviso: si es el mismo vídeo, se vuelve a él; si es otro, se carga
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newVid = intent.getStringExtra(EXTRA_VID)
        if (newVid != null && newVid != vid) {
            setIntent(intent)
            recreate()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(title: String, channel: String, subtitles: Boolean, tv: Boolean): WebView = WebView(this).apply {
        // las teclas del mando las lleva la actividad, no la página
        isFocusable = false
        isFocusableInTouchMode = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        addJavascriptInterface(PlayerBridge(this@PlayerActivity), "Android")
        // El reproductor de YouTube se para solo cuando la página deja de verse (pantalla apagada).
        // Para seguir sonando, a la página de YouTube se le dice que siempre está a la vista.
        if (background && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, KEEP_VISIBLE, setOf("https://www.youtube.com"))
        }
        // YouTube solo reproduce vídeos insertados si sabe quién los inserta (si no, da el error 153).
        // La página se presenta como https://es.miscanales, el ID de la app.
        loadDataWithBaseURL("$ORIGIN/", playerHtml(vid, title, channel, subtitles, tv), "text/html", "utf-8", null)
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
            // abajo (o el botón de subtítulos, si el mando lo tiene) los pone o los quita
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CAPTIONS -> "toggleSubs()"
            // arriba solo enseña el panel
            KeyEvent.KEYCODE_DPAD_UP -> ""
            // Atrás, Home, volumen…: lo normal
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) web?.evaluateJavascript("touched();$script", null)
        return true
    }

    fun onEnded() {
        if (!isFinishing) finish()
    }

    /** El estado del reproductor de YouTube: 1 en marcha, 2 en pausa, 3 cargando. */
    fun onPlayerState(code: Int) {
        when (code) {
            1, 3 -> PlaybackService.setPlaying(true)
            2 -> PlaybackService.setPlaying(false)
        }
    }

    /** Se han puesto o quitado los subtítulos con el mando: se queda así para los siguientes vídeos. */
    fun onCaptions(on: Boolean) {
        saveSubtitles(this, on)
    }

    /** El vídeo no se puede ver insertado (lo ha prohibido su autor, por ejemplo): a la app de YouTube. */
    fun onPlayerError(code: Int) {
        if (isFinishing) return
        Toast.makeText(this, "Este vídeo no se puede ver aquí (error $code). Lo abro en YouTube.", Toast.LENGTH_LONG).show()
        playOnYouTube(vid)
        finish()
    }

    override fun onStop() {
        // En la tele (o en el móvil sin el ajuste) Home o apagar la pantalla lo paran.
        // En el móvil con el ajuste sigue sonando; y en la ventana flotante la actividad no llega a pararse.
        if (!background) {
            web?.evaluateJavascript("pause()", null)
            web?.onPause()
        }
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        web?.onResume()
    }

    override fun onDestroy() {
        if (background) PlaybackService.stop(this)
        if (current === this) current = null
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_VID = "vid"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_SUBTITLES = "subtitles"
        private const val EXTRA_BACKGROUND = "background"
        private const val ORIGIN = "https://es.miscanales"

        // Se ejecuta al empezar a cargar la página de YouTube (dentro del reproductor insertado):
        // la página siempre "se ve", y no se entera de que se ha apagado la pantalla.
        private const val KEEP_VISIBLE = """
            (function () {
              try {
                Object.defineProperty(document, 'visibilityState', { configurable: true, get: function () { return 'visible'; } });
                Object.defineProperty(document, 'hidden', { configurable: true, get: function () { return false; } });
                Object.defineProperty(document, 'webkitVisibilityState', { configurable: true, get: function () { return 'visible'; } });
                Object.defineProperty(document, 'webkitHidden', { configurable: true, get: function () { return false; } });
              } catch (e) {}
              var stop = function (e) { e.stopImmediatePropagation(); };
              ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'freeze'].forEach(function (type) {
                window.addEventListener(type, stop, true);
                document.addEventListener(type, stop, true);
              });
            })();
        """

        /** El reproductor que está abierto, para los controles del aviso y de la pantalla de bloqueo. */
        @SuppressLint("StaticFieldLeak")
        private var current: PlayerActivity? = null

        fun start(context: Context, video: Video, subtitles: Boolean, background: Boolean) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_VID, video.vid)
                    .putExtra(EXTRA_TITLE, video.title)
                    .putExtra(EXTRA_CHANNEL, video.channelName)
                    .putExtra(EXTRA_SUBTITLES, subtitles)
                    .putExtra(EXTRA_BACKGROUND, background),
            )
        }

        /** Ejecuta [script] ("play()", "pause()", "toggle()") en el reproductor abierto. */
        fun control(script: String) {
            current?.web?.evaluateJavascript(script, null)
        }

        /** Para volver al reproductor abierto desde el aviso, sin abrir otro. */
        fun reopenIntent(context: Context): Intent =
            (current?.intent?.let { Intent(it) } ?: Intent(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Un texto como cadena de JavaScript, sin que pueda cerrar la etiqueta <script>. */
        private fun js(text: String) = JsonPrimitive(text).toString().replace("</", "<\\/")

        private fun playerHtml(vid: String, title: String, channel: String, subtitles: Boolean, tv: Boolean) = """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html, body { margin: 0; width: 100%; height: 100%; background: #000; overflow: hidden;
                           font-family: sans-serif; color: #fff; }
              #wrap { position: absolute; top: 0; left: 0; width: 100%; height: 100%; transform-origin: 0 0; }
              .panel { position: absolute; left: 0; right: 0; padding: 32px 48px; z-index: 2;
                       pointer-events: none; transition: opacity .4s; }
              .hidden { opacity: 0; }
              #top { top: 0; padding-bottom: 56px; background: linear-gradient(rgba(0,0,0,.85), rgba(0,0,0,0)); }
              #bottom { bottom: 0; padding-top: 56px; background: linear-gradient(rgba(0,0,0,0), rgba(0,0,0,.85)); }
              #title { font-size: 22px; font-weight: bold; line-height: 1.3; }
              #channel { font-size: 15px; color: #aaa; margin-top: 4px; }
              #bar { height: 5px; border-radius: 3px; background: rgba(255,255,255,.3); overflow: hidden; }
              #fill { height: 100%; width: 0; background: #ff0033; }
              #row { display: flex; margin-top: 10px; font-size: 15px; }
              #hint { margin-left: auto; font-size: 13px; color: #aaa; }
            </style>
            </head><body>
            <div id="wrap"><div id="p"></div></div>
            <div id="top" class="panel"><div id="title"></div><div id="channel"></div></div>
            <div id="bottom" class="panel">
              <div id="bar"><div id="fill"></div></div>
              <div id="row"><span id="time"></span><span id="status"></span>
                <span id="hint">OK: pausa &nbsp; ◀ ▶: 10 s &nbsp; ▼: subtítulos &nbsp; Atrás: salir</span></div>
            </div>
            <script>
            document.getElementById('title').textContent = ${js(title)};
            document.getElementById('channel').textContent = ${js(channel)};
            var player = null, started = false, lastKey = Date.now();
            var subs = $subtitles, subsDone = false, note = '', noteUntil = 0;
            // en el móvil: los controles táctiles de YouTube, sin el panel del mando
            var tv = $tv;
            if (!tv) { document.getElementById('top').style.display = 'none'; document.getElementById('bottom').style.display = 'none'; }
            // La tele dice tener menos píxeles de los que tiene (la mitad, en un Fire TV), y YouTube
            // elige la calidad por el tamaño del reproductor: se hace del tamaño real y se reduce para que quepa.
            (function () {
              var k = window.devicePixelRatio || 1;
              // en el móvil no: los controles táctiles se quedarían diminutos
              if (tv && k > 1) {
                var w = document.getElementById('wrap');
                w.style.width = (100 * k) + '%';
                w.style.height = (100 * k) + '%';
                w.style.transform = 'scale(' + (1 / k) + ')';
              }
            })();
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('p', {
                width: '100%', height: '100%', videoId: '$vid',
                playerVars: { autoplay: 1, controls: tv ? 0 : 1, rel: 0, playsinline: 1, fs: 0, disablekb: 1,
                              iv_load_policy: 3, cc_load_policy: subs ? 1 : 0, cc_lang_pref: 'es', hl: 'es',
                              vq: 'hd1080', origin: '$ORIGIN' },
                events: {
                  onReady: function (e) { applySubs(); e.target.playVideo(); },
                  onStateChange: function (e) {
                    Android.state(e.data);
                    if (e.data == 0) Android.ended();
                    // al empezar, otra vez: YouTube a veces los pone por su cuenta
                    if (e.data == 1 && !subsDone) { subsDone = true; applySubs(); }
                    update();
                  },
                  onError: function (e) { Android.error(e.data); }
                }
              });
            }
            function ready() { return player && player.getPlayerState; }
            function play() { if (ready()) player.playVideo(); }
            function pause() { if (ready()) player.pauseVideo(); }
            function toggle() { if (!ready()) return; if (player.getPlayerState() == 1) player.pauseVideo(); else player.playVideo(); }
            function seek(s) { if (ready()) player.seekTo(Math.max(0, player.getCurrentTime() + s), true); }
            function applySubs() {
              if (!ready()) return;
              try {
                if (subs) {
                  player.loadModule('captions');
                  // en español, si el vídeo los tiene
                  var list = player.getOption('captions', 'tracklist') || [];
                  for (var i = 0; i < list.length; i++) {
                    if (list[i].languageCode == 'es') { player.setOption('captions', 'track', list[i]); break; }
                  }
                } else {
                  player.setOption('captions', 'track', {});
                  player.unloadModule('captions');
                  player.unloadModule('cc');
                }
              } catch (err) {}
            }
            function toggleSubs() {
              subs = !subs;
              applySubs();
              Android.captions(subs);
              note = subs ? 'Subtítulos: sí' : 'Subtítulos: no';
              noteUntil = Date.now() + 2500;
            }
            function touched() { lastKey = Date.now(); update(); }
            function two(n) { return (n < 10 ? '0' : '') + n; }
            function clock(s) {
              s = Math.max(0, Math.floor(s));
              var h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
              return (h > 0 ? h + ':' + two(m) : m) + ':' + two(x);
            }
            function update() {
              var state = ready() ? player.getPlayerState() : -1;
              var t = ready() ? player.getCurrentTime() || 0 : 0;
              var d = ready() ? player.getDuration() || 0 : 0;
              if (state == 1) started = true;
              document.getElementById('fill').style.width = (d > 0 ? Math.min(100, t / d * 100) : 0) + '%';
              document.getElementById('time').textContent = d > 0 ? clock(t) + ' / ' + clock(d) : '';
              var msg = Date.now() < noteUntil ? note : (!started ? 'Cargando…' : (state == 2 ? 'En pausa' : ''));
              document.getElementById('status').textContent = msg ? (d > 0 ? '   ·   ' : '') + msg : '';
              var show = !started || state != 1 || Date.now() - lastKey < 4000;
              document.getElementById('top').className = show ? 'panel' : 'panel hidden';
              document.getElementById('bottom').className = show ? 'panel' : 'panel hidden';
            }
            setInterval(update, 250);
            </script>
            <script src="https://www.youtube.com/iframe_api"></script>
            </body></html>
        """.trimIndent()
    }
}

/** Lo que llama el JavaScript del reproductor. Llega en otro hilo, así que se pasa al principal. */
class PlayerBridge(private val activity: PlayerActivity) {
    @JavascriptInterface
    fun ended() = activity.runOnUiThread { activity.onEnded() }

    @JavascriptInterface
    fun state(code: Int) = activity.runOnUiThread { activity.onPlayerState(code) }

    @JavascriptInterface
    fun error(code: Int) = activity.runOnUiThread { activity.onPlayerError(code) }

    @JavascriptInterface
    fun captions(on: Boolean) = activity.runOnUiThread { activity.onCaptions(on) }
}

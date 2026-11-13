package es.miscanales

import androidx.activity.OnBackPressedCallback
import android.os.Looper
import android.os.Handler
import android.util.Log
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
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

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

    /**
     * Mis Canales Kids: su propio progreso (sin sincronizar), sin salidas a YouTube, sin ventana
     * flotante ni sonido con la pantalla apagada, y con el tiempo del día contado ([kidsLimit] min).
     */
    private var kids = false
    private var kidsLimit = 0
    private var playingSince = 0L

    /** Reproducción continua: los siguientes vídeos de la lista desde la que se abrió este. */
    private val queue = ArrayDeque<QueueItem>()
    private var autoNext = false
    private var sponsorBlock = true
    private var nextPending = false
    private val handler = Handler(Looper.getMainLooper())
    private val playNextLater = Runnable { playNext() }

    // Solo en la variante de prueba (depurable), para el emulador, donde YouTube no deja reproducir:
    // hacer como si el vídeo acabase a los test_end_after segundos y no hacer caso de los errores.
    private val testPrefs get() = if (BuildConfig.DEBUG) getSharedPreferences(PREFS, MODE_PRIVATE) else null
    private val simulateEnd = Runnable { onEnded() }

    private fun simulateEndLater() {
        val seconds = testPrefs?.getInt("test_end_after", 0) ?: 0
        if (seconds > 0) handler.postDelayed(simulateEnd, seconds * 1000L)
    }

    /** En el móvil: seguir sonando con la pantalla apagada o desde la ventana flotante. */
    private var keepPlaying = false

    // por dónde va el vídeo, para seguir otro día (y en otro aparato) donde lo dejaste
    private var videoTitle = ""
    private var lastPos = 0
    private var lastDur = 0
    private var lastSaved = 0L
    private var ended = false
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vid = intent.getStringExtra(EXTRA_VID) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        videoTitle = title
        sponsorBlock = intent.getBooleanExtra(EXTRA_SPONSORBLOCK, true)
        kids = intent.getBooleanExtra(EXTRA_KIDS, false)
        autoNext = intent.getBooleanExtra(EXTRA_AUTONEXT, false)
        queue.clear()
        intent.getStringExtra(EXTRA_QUEUE)?.let { saved ->
            runCatching { queueJson.decodeFromString(ListSerializer(QueueItem.serializer()), saved) }.getOrNull()?.let(queue::addAll)
        }
        kidsLimit = intent.getIntExtra(EXTRA_KIDS_LIMIT, 0)
        // desde un momento concreto (mandado desde el móvil) o donde lo dejaste (si no lo acabaste)
        val forced = intent.getIntExtra(EXTRA_START, -1)
        val saved = Progresses.get(this, vid, kids)
        val start = when {
            forced >= 0 -> forced
            saved != null && saved.started -> saved.pos
            else -> 0
        }
        lastPos = start
        if (start > 0) {
            val text = if (forced >= 0) "Desde el móvil (${clock(start)})" else "Sigues por donde ibas (${clock(start)})"
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
        val channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        val subtitles = intent.getBooleanExtra(EXTRA_SUBTITLES, false)
        val stats = intent.getStringExtra(EXTRA_STATS).orEmpty()
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        current = this
        tv = isTv()
        keepPlaying = !tv && !kids && intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        if (!tv && !kids) {
            showBars(resources.configuration.orientation)
            // al salir de la app (Home), el vídeo sigue en una ventana flotante
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setPictureInPictureParams(pipParams())
        }
        if (keepPlaying) {
            // el aviso con los controles necesita permiso a partir de Android 13
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            // mantiene la app viva con la pantalla apagada, con play y pausa en la pantalla de bloqueo
            PlaybackService.start(this, title, channel)
        }
        val webView = createWebView(title, channel, subtitles, tv, stats, description, start, cast = !tv && !kids && readHaConfig(this) != null)
        if (tv) {
            // en la tele, la página sola en la ventana (con algo detrás, en Fire TV el vídeo se ve negro)
            setContentView(webView)
        } else {
            // en el móvil, dentro de un marco que deja sitio a las barras y a la cámara
            val root = FrameLayout(this)
            root.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsetsCompat.CONSUMED
            }
            setContentView(root)
        }
        loadSegments(vid)
        simulateEndLater()
        // en el móvil (no en Kids), Atrás deja el vídeo en la ventana flotante y se sigue por la app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!nextPending && !tv && !kids && minimize()) return
                finish()
            }
        })
        // si lo has seguido viendo en otro aparato, salta a donde lo dejaste allí
        val config = readHaConfig(this).takeIf { !kids }
        if (config != null && forced < 0) {
            scope.launch {
                val remote = runCatching { ProgressSync.fetch(config)[vid] }.getOrNull() ?: return@launch
                val local = Progresses.get(this@PlayerActivity, vid)
                if (remote.started && (local == null || remote.at > local.at) && abs(remote.pos - lastPos) > 10) {
                    web?.evaluateJavascript("jumpTo(${remote.pos})", null)
                    Toast.makeText(this@PlayerActivity, "Sigues por donde ibas en otro aparato (${clock(remote.pos)})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Los patrocinios de [video] para saltarlos (si está puesto SponsorBlock). */
    private fun loadSegments(video: String) {
        if (!sponsorBlock) return
        scope.launch {
            val segments = SponsorBlock.segments(video)
            if (segments.isNotEmpty() && video == vid) web?.evaluateJavascript("setSegments(${segments.toJson()})", null)
        }
    }

    /** A la ventana flotante (en el móvil). Devuelve si se ha podido. */
    fun minimize(): Boolean {
        if (tv || kids || isInPictureInPictureMode) return false
        return runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)
    }

    /** Al acabar un vídeo con más en la lista: "Siguiente: …" unos segundos, y el siguiente. */
    private fun announceNext() {
        val next = queue.firstOrNull() ?: return
        nextPending = true
        web?.evaluateJavascript("showNext(${js(next.title)}, $NEXT_SECONDS, $tv)", null)
        handler.postDelayed(playNextLater, NEXT_SECONDS * 1000L)
    }

    /** "Cancelar" en el aviso del siguiente: se acaba aquí, como sin reproducción continua. */
    fun cancelNext() {
        handler.removeCallbacks(playNextLater)
        nextPending = false
        finish()
    }

    /** El siguiente de la lista, en el mismo reproductor (sin salir; también en la ventana flotante o con la pantalla apagada). */
    fun playNext() {
        handler.removeCallbacks(playNextLater)
        nextPending = false
        val next = queue.removeFirstOrNull() ?: return finish()
        if (kids) countKidsTime(stillPlaying = false)
        vid = next.vid
        videoTitle = next.title
        ended = false
        lastDur = 0
        lastSaved = 0L
        val saved = Progresses.get(this, vid, kids)
        lastPos = if (saved != null && saved.started) saved.pos else 0
        markWatched(this, vid, kids)
        NewVideos.dismiss(this, vid)
        if (keepPlaying) PlaybackService.start(this, next.title, next.channelName)
        // para que girar la pantalla o volver desde el aviso sigan con este vídeo y con lo que queda
        setIntent(
            Intent(intent)
                .putExtra(EXTRA_VID, next.vid)
                .putExtra(EXTRA_TITLE, next.title)
                .putExtra(EXTRA_CHANNEL, next.channelName)
                .putExtra(EXTRA_STATS, next.stats)
                .putExtra(EXTRA_DESCRIPTION, "")
                .putExtra(EXTRA_START, -1)
                .putExtra(EXTRA_QUEUE, queueJson.encodeToString(ListSerializer(QueueItem.serializer()), queue.toList())),
        )
        web?.evaluateJavascript(
            "loadNext(${js(next.vid)}, ${js(next.title)}, ${js(next.channelName)}, ${js(next.stats)}, $lastPos)",
            null,
        )
        loadSegments(vid)
        Log.i("MisCanales", "Siguiente: $vid (quedan ${queue.size})")
        simulateEndLater()
    }

    private fun List<List<Double>>.toJson(): String =
        joinToString(",", "[", "]") { (from, to) -> "[$from,$to]" }

    /** Guarda por dónde vas; con [sync], también en Home Assistant. */
    private fun saveProgress(sync: Boolean) {
        if (lastDur <= 0) return
        val progress = Progress(lastPos, lastDur, System.currentTimeMillis())
        Progresses.put(this, mapOf(vid to progress), kids)
        lastSaved = progress.at
        if (sync && !kids) ProgressSync.push(this, vid, videoTitle, progress)
    }

    /** Cada pocos segundos, mientras suena. */
    fun onProgress(time: Double, duration: Double) {
        lastPos = time.toInt()
        lastDur = duration.toInt()
        if (System.currentTimeMillis() - lastSaved > 15_000) saveProgress(sync = false)
        if (kids) countKidsTime(stillPlaying = true)
    }

    /**
     * Suma al día de los niños lo que ha sonado desde la última vez y, si se ha acabado su tiempo,
     * para el vídeo y vuelve a la lista (allí sale el aviso y el botón de pedir más con el PIN).
     */
    private fun countKidsTime(stillPlaying: Boolean) {
        val now = System.currentTimeMillis()
        if (playingSince > 0) KidsTime.add(this, ((now - playingSince) / 1000).toInt())
        playingSince = if (stillPlaying) now else 0L
        val left = KidsTime.remaining(this, kidsLimit) ?: return
        if (left <= 0 && !isFinishing) {
            web?.evaluateJavascript("pause()", null)
            Toast.makeText(this, "Se acabó el tiempo de hoy", Toast.LENGTH_LONG).show()
            saveProgress(sync = false)
            finish()
        }
    }

    /** En vertical, con las barras, como en YouTube; en horizontal, a pantalla completa. */
    private fun showBars(orientation: Int) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!tv && !isInPictureInPictureMode) showBars(newConfig.orientation)
    }

    private fun pipParams(): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true) }
        .build()

    // antes de Android 12 la ventana flotante no se abre sola
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // al salir con Home de la app de los mayores, al volver pide el PIN (si así está puesto)
        if (!kids) AppLock.leaving()
        if (!tv && !kids && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
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
        } else if (newVid == vid && intent.getIntExtra(EXTRA_START, -1) >= 0) {
            // el mismo vídeo, mandado otra vez desde el móvil: al momento en que va allí
            web?.evaluateJavascript("jumpTo(${intent.getIntExtra(EXTRA_START, 0)})", null)
        }
    }

    /** "Ver en la tele": se para aquí y se manda a la tele por Home Assistant, desde este momento. */
    fun sendToTv(start: Int) {
        val config = readHaConfig(this) ?: return
        lastPos = start
        saveProgress(sync = true)
        Tele.sendAndTell(this, config, vid, start) { tv -> saveHaConfig(this, config.copy(tv = tv)) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(
        title: String,
        channel: String,
        subtitles: Boolean,
        tv: Boolean,
        stats: String,
        description: String,
        start: Int,
        cast: Boolean,
        minimizable: Boolean = !tv && !kids,
    ): WebView = (if (keepPlaying) AlwaysVisibleWebView(this) else WebView(this)).apply {
        // las teclas del mando las lleva la actividad, no la página
        isFocusable = false
        isFocusableInTouchMode = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // En la tele, como el Chrome de un PC: con el de una app Android, YouTube da su reproductor
        // de móvil, que no pasa de poca calidad aunque la pantalla sea grande.
        if (tv) settings.userAgentString = DESKTOP_UA
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        addJavascriptInterface(PlayerBridge(this@PlayerActivity), "Android")
        // El reproductor de YouTube se para solo cuando la página deja de verse (pantalla apagada).
        // Para seguir sonando, a la página de YouTube se le dice que siempre está a la vista.
        if (this@PlayerActivity.keepPlaying && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, KEEP_VISIBLE, setOf("https://www.youtube.com"))
        }
        // En la tele, para que YouTube ofrezca más de 720p, a su reproductor se le cuenta que está en
        // el Chrome de un PC (también en las pistas que da el navegador, no solo en el nombre), que
        // puede con 1080p o más con soltura (la WebView del Fire TV dice que no aunque pueda) y el
        // tamaño real de la pantalla (dice ser de 960×540 con escala ×4). La WebView del Fire TV no
        // sabe meter scripts al empezar una página, así que la app pide ella la del reproductor.
        if (tv || kids) webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (tv && EmbedPatch.isEmbed(request)) {
                    EmbedPatch.fetch(request, DESKTOP_UA, "$ORIGIN/", QUALITY_CAP + HIDE_BRIDGE + DESKTOP_HINTS + SMOOTH_DECODING + REAL_SCREEN + HFR_FIX)
                } else {
                    null
                }

            // en Kids, nada de salir a YouTube: los enlaces del reproductor (el logo, el título) no hacen nada
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = kids
        }
        // YouTube solo reproduce vídeos insertados si sabe quién los inserta (si no, da el error 153).
        // La página se presenta como https://es.miscanales, el ID de la app.
        loadDataWithBaseURL("$ORIGIN/", playerHtml(vid, title, channel, subtitles, tv, stats, description, start, cast, minimizable), "text/html", "utf-8", null)
        web = this
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (nextPending && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (event.action == KeyEvent.ACTION_UP) playNext()
            return true
        }
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
        if (ended || nextPending) return
        // visto entero: la próxima vez empieza desde el principio, y la barra sale llena
        if (lastDur > 0) lastPos = lastDur
        saveProgress(sync = true)
        ended = true
        if (isFinishing) return
        if (autoNext && queue.isNotEmpty()) announceNext() else finish()
    }

    /** El estado del reproductor de YouTube: 1 en marcha, 2 en pausa, 3 cargando. */
    fun onPlayerState(code: Int) {
        if (kids) countKidsTime(stillPlaying = code == 1)
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
        if (testPrefs?.getBoolean("test_ignore_errors", false) == true) return
        if (kids) {
            // en Kids no se sale a la app de YouTube: se avisa y se vuelve a la lista
            Toast.makeText(this, "Este vídeo no se puede ver aquí", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Este vídeo no se puede ver aquí (error $code). Lo abro en YouTube.", Toast.LENGTH_LONG).show()
            playOnYouTube(vid)
        }
        finish()
    }

    override fun onStop() {
        if (!ended) saveProgress(sync = true)
        if (kids) countKidsTime(stillPlaying = false)
        // En la tele (o en el móvil sin el ajuste) Home o apagar la pantalla lo paran.
        // En el móvil con el ajuste sigue sonando; y en la ventana flotante la actividad no llega a pararse.
        if (!keepPlaying) {
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
        handler.removeCallbacks(playNextLater)
        handler.removeCallbacks(simulateEnd)
        scope.cancel()
        if (keepPlaying) PlaybackService.stop(this)
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
        private const val EXTRA_STATS = "stats"
        private const val EXTRA_SPONSORBLOCK = "sponsorblock"
        private const val EXTRA_DESCRIPTION = "description"
        private const val EXTRA_START = "start"
        private const val EXTRA_KIDS = "kids"
        private const val EXTRA_QUEUE = "queue"
        private const val EXTRA_AUTONEXT = "autoNext"
        private const val NEXT_SECONDS = 6
        private const val MAX_QUEUE = 60
        private val queueJson = Json { ignoreUnknownKeys = true }
        private const val EXTRA_KIDS_LIMIT = "kidsLimit"
        private const val ORIGIN = "https://es.miscanales"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

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

        private const val SMOOTH_DECODING = """
            (function () {
              var mc = navigator.mediaCapabilities;
              if (!mc || !mc.decodingInfo) return;
              var original = mc.decodingInfo.bind(mc);
              var yes = function (result, config) {
                return { supported: true, smooth: true, powerEfficient: true,
                         keySystemAccess: result.keySystemAccess || null, configuration: result.configuration || config };
              };
              mc.decodingInfo = function (config) {
                var cv = config && config.video, max = window.__mcMax || 100000;
                if (cv && cv.width && cv.height && Math.min(cv.width, cv.height) > max) {
                  return Promise.resolve({ supported: false, smooth: false, powerEfficient: false, configuration: config });
                }
                return original(config).then(function (result) {
                  if (result && result.supported) return yes(result, config);
                  var v = config && config.video;
                  if (!v || !(v.framerate > 30)) return result;
                  var slower = JSON.parse(JSON.stringify(config));
                  slower.video.framerate = 30;
                  return original(slower).then(function (r30) {
                    if (!r30 || !r30.supported) return result;
                    return yes(r30, config);
                  });
                });
              };
            })();
        """

        // La WebView del Fire TV dice que no puede con los formatos de 60 imágenes por segundo cuando
        // YouTube pregunta con detalles (resolución, fps…), aunque sí puede con el formato en sí, y
        // YouTube los quita todos (y en muchos vídeos, de 1080p para arriba solo hay a 60).
        // Se contesta como el Chrome de un PC: mirando solo el formato. Se apunta qué se ha corregido.
        private const val HFR_FIX = """
            (function () {
              function base(t) {
                var parts = String(t).split(';'), out = [parts[0].trim()];
                for (var i = 1; i < parts.length; i++) {
                  var p = parts[i].trim();
                  if (/^codecs\s*=/i.test(p)) out.push(p);
                }
                return out.join('; ');
              }
              function tooBig(t) {
                var w = /width=(\d+)/.exec(t), h = /height=(\d+)/.exec(t);
                return !!(w && h && Math.min(+w[1], +h[1]) > (window.__mcMax || 100000));
              }
              ['MediaSource', 'ManagedMediaSource', 'WebKitMediaSource'].forEach(function (name) {
                var MS = window[name];
                if (!MS || !MS.isTypeSupported) return;
                var original = MS.isTypeSupported.bind(MS);
                MS.isTypeSupported = function (t) {
                  if (tooBig(String(t))) return false;
                  if (original(t)) return true;
                  var b = base(t);
                  return b != String(t) && original(b);
                };
              });
              var canPlay = HTMLMediaElement.prototype.canPlayType;
              HTMLMediaElement.prototype.canPlayType = function (t) {
                if (tooBig(String(t))) return '';
                var r = canPlay.call(this, t);
                if (r) return r;
                var b = base(t);
                if (b != String(t)) {
                  var r2 = canPlay.call(this, b);
                  if (r2) return r2;
                }
                return r;
              };
            })();
        """

        private const val DESKTOP_HINTS = """
            (function () {
              var brands = [{ brand: 'Chromium', version: '131' }, { brand: 'Google Chrome', version: '131' },
                            { brand: 'Not_A Brand', version: '24' }];
              var full = brands.map(function (b) { return { brand: b.brand, version: b.version + '.0.0.0' }; });
              var high = { architecture: 'x86', bitness: '64', brands: brands, fullVersionList: full, mobile: false,
                           model: '', platform: 'Windows', platformVersion: '15.0.0', uaFullVersion: '131.0.0.0',
                           wow64: false, formFactors: ['Desktop'] };
              var data = { brands: brands, mobile: false, platform: 'Windows',
                           getHighEntropyValues: function () { return Promise.resolve(high); },
                           toJSON: function () { return { brands: brands, mobile: false, platform: 'Windows' }; } };
              var get = function (v) { return { configurable: true, get: function () { return v; } }; };
              try { Object.defineProperty(Navigator.prototype, 'userAgentData', get(data)); } catch (e) {}
              try { Object.defineProperty(Navigator.prototype, 'platform', get('Win32')); } catch (e) {}
            })();
        """

        // El puente con la app (window.Android) aparece también dentro del reproductor de YouTube y
        // delata una WebView: ahí se quita.
        private const val HIDE_BRIDGE = """
            (function () { try { delete window.Android; } catch (e) {} })();
        """

        /**
         * Lo más alto que se deja reproducir en la tele (en líneas, el lado corto del vídeo). Con 2160p60
         * el Fire TV va a saltos: los formatos más altos se contestan como no compatibles.
         */
        private const val MAX_LINES = 1440
        private const val QUALITY_CAP = "window.__mcMax = $MAX_LINES;"

        private const val REAL_SCREEN = """
            (function () {
              var k = window.devicePixelRatio || 1;
              if (k <= 1) return;
              var w = Math.round(screen.width * k), h = Math.round(screen.height * k);
              var max = window.__mcMax || 100000;
              if (Math.min(w, h) > max) { var f = max / Math.min(w, h); w = Math.round(w * f); h = Math.round(h * f); }
              ['width', 'availWidth'].forEach(function (p) {
                try { Object.defineProperty(screen, p, { configurable: true, get: function () { return w; } }); } catch (e) {}
              });
              ['height', 'availHeight'].forEach(function (p) {
                try { Object.defineProperty(screen, p, { configurable: true, get: function () { return h; } }); } catch (e) {}
              });
            })();
        """

        /** El reproductor que está abierto, para los controles del aviso y de la pantalla de bloqueo. */
        @SuppressLint("StaticFieldLeak")
        private var current: PlayerActivity? = null

        fun start(
            context: Context,
            video: Video,
            subtitles: Boolean,
            background: Boolean,
            sponsorBlock: Boolean,
            startAt: Int = -1,
            kids: Boolean = false,
            kidsLimit: Int = 0,
            queue: List<Video> = emptyList(),
            autoNext: Boolean = false,
        ) {
            val next = queue.take(MAX_QUEUE).map { QueueItem(it.vid, it.title, it.channelId, it.channelName, it.stats()) }
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    // ir al reproductor no cuenta como salir de la app (para "Pedir PIN al abrir")
                    .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                    .putExtra(EXTRA_START, startAt)
                    .putExtra(EXTRA_AUTONEXT, autoNext)
                    .putExtra(EXTRA_QUEUE, queueJson.encodeToString(ListSerializer(QueueItem.serializer()), next))
                    .putExtra(EXTRA_KIDS, kids)
                    .putExtra(EXTRA_KIDS_LIMIT, kidsLimit)
                    .putExtra(EXTRA_VID, video.vid)
                    .putExtra(EXTRA_TITLE, video.title)
                    .putExtra(EXTRA_CHANNEL, video.channelName)
                    .putExtra(EXTRA_SUBTITLES, subtitles)
                    .putExtra(EXTRA_BACKGROUND, background)
                    .putExtra(EXTRA_STATS, video.stats())
                    .putExtra(EXTRA_SPONSORBLOCK, sponsorBlock)
                    .putExtra(EXTRA_DESCRIPTION, video.description),
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

        private fun playerHtml(
            vid: String,
            title: String,
            channel: String,
            subtitles: Boolean,
            tv: Boolean,
            stats: String,
            description: String,
            start: Int,
            cast: Boolean,
            minimizable: Boolean,
        ) = """
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
              /* los avisos cortos (subtítulos, patrocinio saltado), en la tele y en el móvil */
              #toast { position: absolute; top: 24px; right: 24px; z-index: 3; padding: 8px 14px; border-radius: 18px;
                       background: rgba(0,0,0,.75); font-size: 15px; pointer-events: none; transition: opacity .3s; }
              /* en el móvil en vertical: el vídeo arriba y debajo el título y la descripción */
              #info { display: none; }
              @media (orientation: portrait) {
                .phone #wrap { height: 56.25vw; }
                .phone #info { display: block; position: absolute; top: 56.25vw; left: 0; right: 0; bottom: 0;
                               overflow-y: auto; padding: 14px 16px 32px; box-sizing: border-box; background: #0f0f0f; }
              }
              #info h1 { font-size: 18px; line-height: 1.3; margin: 0 0 6px; }
              #info .sub { font-size: 13px; color: #aaa; }
              #totv { display: none; margin-top: 12px; padding: 8px 16px; border: 0; border-radius: 18px;
                      background: #272727; color: #fff; font-size: 14px; }
              #mini { display: none; margin: 12px 0 0 8px; padding: 8px 16px; border: 0; border-radius: 18px;
                      background: #272727; color: #fff; font-size: 14px; }
              /* reproducción continua: el siguiente vídeo */
              #next { display: none; position: absolute; right: 24px; bottom: 96px; z-index: 4; max-width: 460px;
                      padding: 16px 20px; border-radius: 14px; background: rgba(15,15,15,.92); }
              #next .label { font-size: 13px; color: #aaa; }
              #nexttitle { font-size: 18px; font-weight: bold; margin: 4px 0 8px; line-height: 1.3; }
              #nextcount { font-size: 14px; color: #ddd; }
              #next button { margin: 12px 8px 0 0; padding: 8px 16px; border: 0; border-radius: 18px; background: #272727;
                             color: #fff; font-size: 14px; }
              @media (orientation: portrait) { .phone #next { left: 16px; right: 16px; top: calc(56.25vw + 12px); bottom: auto; } }
              #info .desc { white-space: pre-wrap; font-size: 14px; line-height: 1.45; color: #ddd; margin-top: 16px;
                            overflow-wrap: anywhere; }
            </style>
            </head><body>
            <div id="wrap"><div id="p"></div></div>
            <div id="toast" class="hidden"></div>
            <div id="info"><h1 id="ititle"></h1><div class="sub" id="ichannel"></div><div class="sub" id="istats"></div>
              <button id="totv" onclick="toTv()">📺 Ver en la tele</button><button id="mini" onclick="Android.minimize()">⌄ Minimizar</button>
              <div class="desc" id="idesc"></div></div>
            <div id="next"><div class="label">Siguiente</div><div id="nexttitle"></div><div id="nextcount"></div>
              <div id="nextbuttons"><button onclick="Android.playNext()">Ver ya</button><button onclick="Android.cancelNext()">Cancelar</button></div></div>
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
            if (!tv) {
              document.getElementById('top').style.display = 'none';
              document.getElementById('bottom').style.display = 'none';
              document.body.className = 'phone';
              document.getElementById('ititle').textContent = ${js(title)};
              document.getElementById('ichannel').textContent = ${js(channel)};
              document.getElementById('istats').textContent = ${js(stats)};
              document.getElementById('idesc').textContent = ${js(description)};
              if ($cast) document.getElementById('totv').style.display = 'inline-block';
              if ($minimizable) document.getElementById('mini').style.display = 'inline-block';
            }
            // reproducción continua: el aviso del siguiente (en la tele, OK lo pone ya y Atrás sale)
            var nextTimer = null;
            function showNext(title, seconds, isTv) {
              document.getElementById('nexttitle').textContent = title;
              document.getElementById('nextbuttons').style.display = isTv ? 'none' : 'block';
              var left = seconds;
              var count = function () {
                document.getElementById('nextcount').textContent =
                  'Empieza en ' + left + ' s' + (isTv ? '   ·   OK: ya   ·   Atrás: salir' : '');
                left = Math.max(0, left - 1);
              };
              count();
              clearInterval(nextTimer);
              nextTimer = setInterval(count, 1000);
              document.getElementById('next').style.display = 'block';
            }
            function hideNext() { clearInterval(nextTimer); document.getElementById('next').style.display = 'none'; }
            function loadNext(id, title, channel, stats, start) {
              hideNext();
              document.getElementById('title').textContent = title;
              document.getElementById('channel').textContent = channel;
              document.getElementById('ititle').textContent = title;
              document.getElementById('ichannel').textContent = channel;
              document.getElementById('istats').textContent = stats;
              document.getElementById('idesc').textContent = '';
              segs = []; skipped = {}; subsDone = false; started = false;
              if (ready()) player.loadVideoById({ videoId: id, startSeconds: start || 0 });
            }
            function toTv() {
              var t = ready() ? Math.floor(player.getCurrentTime() || 0) : 0;
              pause();
              Android.toTv(t);
            }
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
                              vq: 'hd1080', start: $start, origin: '$ORIGIN' },
                events: {
                  onReady: function (e) {
                    applySubs();
                    if (pending >= 0) { e.target.seekTo(pending, true); pending = -1; }
                    e.target.playVideo();
                  },
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
            // ir a un segundo (el progreso de otro aparato); si aún no está listo, al estarlo
            var pending = -1;
            function jumpTo(s) { if (ready()) player.seekTo(s, true); else pending = s; }
            // SponsorBlock: los tramos [inicio, fin] que se saltan, una vez cada uno
            var segs = [], skipped = {};
            function setSegments(s) { segs = s; skipped = {}; }
            function flash(text) { note = text; noteUntil = Date.now() + 2500; }
            function skipSponsors(t) {
              for (var i = 0; i < segs.length; i++) {
                if (!skipped[i] && t >= segs[i][0] && t < segs[i][1] - 0.5) {
                  skipped[i] = true;
                  player.seekTo(segs[i][1], true);
                  flash('Patrocinio saltado');
                }
              }
            }
            // el progreso, para seguir otro día donde lo dejaste
            setInterval(function () {
              if (ready() && player.getPlayerState() == 1) Android.progress(player.getCurrentTime() || 0, player.getDuration() || 0);
            }, 5000);
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
              flash(subs ? 'Subtítulos: sí' : 'Subtítulos: no');
            }
            function touched() { lastKey = Date.now(); update(); }
            function two(n) { return (n < 10 ? '0' : '') + n; }
            function quality(q) {
              return { tiny: '144p', small: '240p', medium: '360p', large: '480p', hd720: '720p', hd1080: '1080p',
                       hd1440: '1440p', hd2160: '4K', highres: '8K' }[q] || '';
            }
            function clock(s) {
              s = Math.max(0, Math.floor(s));
              var h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
              return (h > 0 ? h + ':' + two(m) : m) + ':' + two(x);
            }
            function update() {
              var state = ready() ? player.getPlayerState() : -1;
              var t = ready() ? player.getCurrentTime() || 0 : 0;
              var d = ready() ? player.getDuration() || 0 : 0;
              if (state == 1) { started = true; skipSponsors(t); }
              document.getElementById('fill').style.width = (d > 0 ? Math.min(100, t / d * 100) : 0) + '%';
              // la calidad a la que está sonando de verdad, junto al tiempo
              var q = ready() && player.getPlaybackQuality ? quality(player.getPlaybackQuality()) : '';
              document.getElementById('time').textContent = d > 0 ? clock(t) + ' / ' + clock(d) + (q ? '   ·   ' + q : '') : '';
              var noting = Date.now() < noteUntil;
              var toast = document.getElementById('toast');
              toast.textContent = note;
              toast.className = noting ? '' : 'hidden';
              var msg = !started ? 'Cargando…' : (state == 2 ? 'En pausa' : '');
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
    fun progress(time: Double, duration: Double) = activity.runOnUiThread { activity.onProgress(time, duration) }

    @JavascriptInterface
    fun error(code: Int) = activity.runOnUiThread { activity.onPlayerError(code) }

    @JavascriptInterface
    fun captions(on: Boolean) = activity.runOnUiThread { activity.onCaptions(on) }

    @JavascriptInterface
    fun toTv(start: Int) = activity.runOnUiThread { activity.sendToTv(start) }

    @JavascriptInterface
    fun minimize() = activity.runOnUiThread { activity.minimize() }

    @JavascriptInterface
    fun playNext() = activity.runOnUiThread { activity.playNext() }

    @JavascriptInterface
    fun cancelNext() = activity.runOnUiThread { activity.cancelNext() }
}

/**
 * Una WebView que no se da por oculta cuando se apaga la pantalla: si no, pausa el vídeo por su
 * cuenta. Al bloquear el móvil, Android avisa a la ventana con GONE (no con INVISIBLE), así que
 * se ignoran los dos. Solo se usa en el móvil con "Seguir sonando con la pantalla apagada".
 */
private class AlwaysVisibleWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}

/** Un vídeo de la cola de la reproducción continua: lo justo para enseñarlo y ponerlo. */
@kotlinx.serialization.Serializable
data class QueueItem(
    val vid: String,
    val title: String,
    val channelId: String = "",
    val channelName: String = "",
    val stats: String = "",
)

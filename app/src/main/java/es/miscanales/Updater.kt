package es.miscanales

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Las versiones nuevas, desde GitHub: cada cambio publica en Releases ("latest") el APK y un
 * version.json con su número. La app lo mira al abrirse; si hay una más nueva, la ofrece, la baja
 * y se la pasa al instalador de Android (que pide confirmar, y solo acepta la misma firma).
 */
object Updater {
    private const val TAG = "MisCanales"
    private const val BASE = "https://github.com/Enjamao/mis-canales/releases/download/latest"

    /** Cada cuánto se mira sola, como mucho. */
    private const val EVERY = 6 * 60 * 60 * 1000L

    @Serializable
    data class Release(val versionCode: Int, val versionName: String)

    /** La versión nueva que hay para instalar, si la hay. */
    var available by mutableStateOf<Release?>(null)
        private set

    /** Mirando o bajando. */
    var busy by mutableStateOf(false)
        private set

    /** Cuánto lleva bajado (0 a 100), mientras baja. */
    var progress by mutableStateOf<Int?>(null)
        private set

    /** Lo último que ha pasado, para enseñarlo. */
    var message by mutableStateOf<String?>(null)
        private set

    private var checkedAt = 0L
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** El número de esta versión (en la variante de prueba se puede fingir uno anterior). */
    private fun current(context: Context): Int {
        if (BuildConfig.DEBUG) {
            val fake = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("test_version_code", 0)
            if (fake > 0) return fake
        }
        return BuildConfig.VERSION_CODE
    }

    /** Mira si hay versión nueva. Sola, cada unas horas y sin decir nada si no hay; con [byHand], ahora y contándolo. */
    suspend fun check(context: Context, byHand: Boolean = false) {
        val now = System.currentTimeMillis()
        if (busy || (!byHand && now - checkedAt < EVERY)) return
        checkedAt = now
        busy = true
        if (byHand) message = "Mirando si hay versión nueva…"
        try {
            val release = withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url("$BASE/version.json").build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("GitHub contesta ${response.code}")
                    json.decodeFromString(Release.serializer(), response.body.string())
                }
            }
            available = release.takeIf { it.versionCode > current(context) }
            Log.i(TAG, "Actualizar: en GitHub está la ${release.versionName} (${release.versionCode}); aquí, la ${current(context)}")
            if (byHand) message = if (available != null) "Hay una versión nueva: ${release.versionName}." else "Tienes la última versión."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Actualizar: no se ha podido mirar", e)
            // (mientras se publica una versión, unos segundos, el archivo no está)
            if (byHand) message = "No se ha podido mirar: ${e.message}"
        } finally {
            busy = false
        }
    }

    /** Baja la versión nueva y abre el instalador de Android. */
    suspend fun install(context: Context) {
        val release = available ?: return
        if (busy) return
        // Android pide permiso para que una app instale otras
        if (!context.packageManager.canRequestPackageInstalls()) {
            if (context.isTv()) {
                // en el Fire no se puede abrir ese ajuste desde aquí: se dice dónde está, y se prueba igual
                message = "Si la tele no deja instalar: Configuración → Mi Fire TV → Opciones para desarrolladores → " +
                    "Instalar aplicaciones desconocidas → Mis Canales: activar. Luego, otra vez Actualizar."
            } else {
                message = "Dale permiso a Mis Canales para instalar aplicaciones y vuelve a tocar Actualizar."
                val opened = runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (opened) return
            }
        } else {
            message = null
        }
        busy = true
        progress = 0
        try {
            val file = withContext(Dispatchers.IO) { download(context) }
            // que sea de verdad esta app y más nueva (que la firma sea la misma lo comprueba Android)
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            if (info == null || info.packageName != context.packageName || info.longVersionCode <= current(context)) {
                file.delete()
                throw IOException("lo que se ha bajado no es una versión más nueva de Mis Canales")
            }
            Log.i(TAG, "Actualizar: bajada la ${info.versionName} (${info.longVersionCode}); al instalador")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.update", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    // ir al instalador no cuenta como salir de la app (para "Pedir PIN al abrir")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
            if (message == null) message = "Bajada la ${release.versionName}: confirma la instalación."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Actualizar: no se ha podido", e)
            message = "No se ha podido actualizar: ${e.message}"
        } finally {
            busy = false
            progress = null
        }
    }

    private fun download(context: Context): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val file = File(dir, "mis-canales.apk")
        http.newCall(Request.Builder().url("$BASE/mis-canales.apk").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub contesta ${response.code}")
            val total = response.body.contentLength()
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) progress = (done * 100 / total).toInt()
                    }
                }
            }
        }
        return file
    }

    /** Lo que pone el botón. */
    fun label(): String {
        val release = available ?: return "Buscar actualizaciones"
        return progress?.let { "Bajando la ${release.versionName}… $it %" } ?: "Actualizar a la ${release.versionName}"
    }
}

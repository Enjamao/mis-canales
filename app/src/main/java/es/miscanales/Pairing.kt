package es.miscanales

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume

/**
 * Pasar la conexión con Home Assistant del móvil a la tele por la wifi de casa (en la tele, escribir
 * el token con el mando es imposible). Mientras la tele tiene abierta su pantalla de Home Assistant,
 * se anuncia en la red y enseña un código; el móvil la encuentra, y le manda la conexión con ese código.
 */
object Pairing {
    private const val TAG = "MisCanales"

    /** Cómo se anuncia la tele en la red (DNS-SD). */
    const val SERVICE_TYPE = "_miscanales._tcp."

    /** Los códigos que se pueden fallar antes de que la tele deje de escuchar. */
    private const val MAX_TRIES = 5

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Message(val code: String, val config: HaConfig)

    /** El nombre de este aparato ("Fire TV de Eloy"), o su modelo. */
    fun deviceName(context: Context): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    /** En la tele: escucha mientras la pantalla está abierta. */
    class Receiver(private val context: Context) {
        val code = (1000..9999).random().toString()
        @Volatile private var server: ServerSocket? = null
        private var registration: NsdManager.RegistrationListener? = null
        private val nsd get() = context.getSystemService(NsdManager::class.java)

        /**
         * Espera hasta [stop]: cada vez que llega una conexión con el código bueno, [handle] la prueba y
         * devuelve lo que se le contesta al móvil.
         */
        suspend fun run(handle: suspend (HaConfig) -> String) = withContext(Dispatchers.IO) {
            val socket = try {
                ServerSocket(0)
            } catch (e: IOException) {
                Log.w(TAG, "Emparejar: no se puede escuchar", e)
                return@withContext
            }
            server = socket
            register(socket.localPort)
            var wrong = 0
            try {
                while (true) {
                    // stop() cierra el socket, y accept() sale con un error
                    val client = try {
                        socket.accept()
                    } catch (e: IOException) {
                        break
                    }
                    client.use { c ->
                        c.soTimeout = 15_000
                        val reply = try {
                            val line = c.getInputStream().bufferedReader().readLine().orEmpty()
                            val message = json.decodeFromString(Message.serializer(), line)
                            if (message.code.trim() != code) {
                                wrong++
                                if (wrong >= MAX_TRIES) "Demasiados códigos mal: en la tele, vuelve a abrir Home Assistant." else "Ese no es el código de la tele."
                            } else {
                                withContext(Dispatchers.Main) { handle(message.config) }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "No se ha podido: ${e.message}"
                        }
                        val out = c.getOutputStream().bufferedWriter()
                        out.write(reply.replace('\n', ' '))
                        out.newLine()
                        out.flush()
                    }
                    if (wrong >= MAX_TRIES) break
                }
            } finally {
                stop()
            }
        }

        private fun register(port: Int) {
            val info = NsdServiceInfo().apply {
                serviceName = "Mis Canales · ${deviceName(context)}"
                serviceType = SERVICE_TYPE
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Log.i(TAG, "Emparejar: anunciada como ${info.serviceName}, puerto $port")
                override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Log.w(TAG, "Emparejar: no se ha podido anunciar ($error)")
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
            }
            registration = listener
            runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
                .onFailure { Log.w(TAG, "Emparejar: no se ha podido anunciar", it) }
        }

        fun stop() {
            registration?.let { runCatching { nsd.unregisterService(it) } }
            registration = null
            runCatching { server?.close() }
            server = null
        }
    }

    /** En el móvil: busca teles con la pantalla de Home Assistant abierta; devuelve cómo dejar de buscar. */
    fun discover(context: Context, onChange: (List<NsdServiceInfo>) -> Unit): () -> Unit {
        val nsd = context.getSystemService(NsdManager::class.java)
        val main = Handler(Looper.getMainLooper())
        val found = linkedMapOf<String, NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    found[info.serviceName] = info
                    onChange(found.values.toList())
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                main.post {
                    found.remove(info.serviceName)
                    onChange(found.values.toList())
                }
            }

            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, error: Int) = Log.w(TAG, "Emparejar: no se puede buscar ($error)")
            override fun onStopDiscoveryFailed(serviceType: String, error: Int) {}
        }
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.w(TAG, "Emparejar: no se puede buscar", it) }
        return { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    /** El nombre de una tele encontrada, sin el "Mis Canales · " del principio. */
    fun name(info: NsdServiceInfo): String = info.serviceName.removePrefix("Mis Canales · ")

    /** En el móvil: le manda a la tele la conexión, con su código. Devuelve lo que contesta la tele. */
    suspend fun send(context: Context, tv: NsdServiceInfo, code: String, config: HaConfig): String {
        val resolved = resolve(context, tv) ?: return "No se encuentra la tele en la red. ¿Sigue abierta su pantalla de Home Assistant?"
        @Suppress("DEPRECATION")
        val host = resolved.host ?: return "No se encuentra la tele en la red."
        return withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, resolved.port), 5_000)
                // la tele prueba la conexión con Home Assistant antes de contestar
                socket.soTimeout = 45_000
                val out = socket.getOutputStream().bufferedWriter()
                out.write(json.encodeToString(Message.serializer(), Message(code.trim(), config.copy(tv = ""))))
                out.newLine()
                out.flush()
                socket.getInputStream().bufferedReader().readLine() ?: "La tele no ha contestado."
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(context: Context, tv: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        val nsd = context.getSystemService(NsdManager::class.java)
        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                if (cont.isActive) cont.resume(info)
            }

            override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                Log.w(TAG, "Emparejar: no se ha podido resolver ${info.serviceName} ($error)")
                if (cont.isActive) cont.resume(null)
            }
        }
        runCatching { nsd.resolveService(tv, listener) }.onFailure { if (cont.isActive) cont.resume(null) }
    }
}

package es.miscanales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Lo que dura cada vídeo. El feed RSS de YouTube no lo trae, así que se saca de la pestaña Vídeos de
 * cada canal (ver [Feed]) y se apunta aquí, para no volver a buscarlo. Es lo mismo para todos los
 * perfiles y para Kids: un archivo para todo el aparato.
 *
 * Un vídeo apuntado con "" ya se buscó y no salía (un Short, un directo): no se vuelve a buscar.
 */
object Durations {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val scope = MainScope()
    private var file: File? = null

    // del más viejo al más nuevo, para quitar los más viejos al pasar de MAX
    private val known = LinkedHashMap<String, String>()

    /** Lee lo apuntado, la primera vez (en [dir], la carpeta de la app). */
    @Synchronized
    fun init(dir: File) {
        if (file != null) return
        val f = File(dir, "duraciones.json")
        file = f
        runCatching { known.putAll(json.decodeFromString(serializer, f.readText())) }
    }

    /** Si ya se ha buscado lo que dura [vid] (aunque no se encontrara). */
    @Synchronized
    fun knows(vid: String): Boolean = vid in known

    /** Lo que dura [vid], o "" si no se sabe. */
    @Synchronized
    fun of(vid: String): String = known[vid].orEmpty()

    /** Apunta lo que duran unos vídeos ("" para los buscados y no encontrados) y lo guarda. */
    @Synchronized
    fun putAll(found: Map<String, String>) {
        if (found.isEmpty()) return
        found.forEach { (vid, duration) ->
            known.remove(vid)
            known[vid] = duration
        }
        while (known.size > MAX) known.remove(known.keys.first())
        val copy = HashMap(known)
        val f = file ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { f.writeText(json.encodeToString(serializer, copy)) }
        }
    }

    /** Los vídeos que se guardan, como mucho: de sobra para los de todos los canales. */
    private const val MAX = 4000
}

/** El vídeo con lo que dura, si no lo trae y ya se sabe. */
fun Video.withDuration(): Video =
    if (duration.isNotBlank()) this else Durations.of(vid).takeIf { it.isNotBlank() }?.let { copy(duration = it) } ?: this

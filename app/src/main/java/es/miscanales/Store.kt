package es.miscanales

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Un canal de YouTube que has añadido. */
@Serializable
data class Channel(
    /** El ID del canal, el que empieza por UC. */
    val id: String,
    val name: String,
    /** La foto del canal, si se pudo sacar de su página. */
    val avatar: String? = null,
    /** Cuándo lo añadiste, en milisegundos. */
    val addedAt: Long = 0,
)

/** Tus canales, los vídeos que ya has visto y los ajustes, guardados en el aparato. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val channelList = ListSerializer(Channel.serializer())

    val channels = mutableStateListOf<Channel>().apply {
        val saved = prefs.getString("channels", null)
        if (saved != null) addAll(runCatching { json.decodeFromString(channelList, saved) }.getOrDefault(emptyList()))
    }

    /** Los vídeos que has visto, del más antiguo al más reciente. */
    val watched = mutableStateListOf<String>().apply {
        addAll(prefs.getString("watched", "").orEmpty().split(",").filter { it.isNotEmpty() })
    }

    var hideShorts by mutableStateOf(prefs.getBoolean("hideShorts", true))
        private set

    /** Si los vídeos se ven en la app de YouTube en vez de en el reproductor de Mis Canales. */
    var useYouTubeApp by mutableStateOf(prefs.getBoolean("useYouTubeApp", false))
        private set

    /** Dónde está tu Home Assistant, si los canales se sincronizan con él. */
    var ha by mutableStateOf(
        prefs.getString("ha", null)?.let { saved -> runCatching { json.decodeFromString(HaConfig.serializer(), saved) }.getOrNull() },
    )
        private set

    /** Si los vídeos empiezan con subtítulos. En el reproductor se cambia con abajo en el mando. */
    var subtitles by mutableStateOf(prefs.getBoolean(SUBTITLES, false))
        private set

    fun channel(id: String): Channel? = channels.firstOrNull { it.id == id }

    fun has(id: String): Boolean = channels.any { it.id == id }

    fun add(channel: Channel) {
        val old = channel(channel.id)
        channels.removeAll { it.id == channel.id }
        // si ya estaba, conserva la fecha en que se añadió
        channels += if (old != null) channel.copy(addedAt = old.addedAt) else channel
        saveChannels()
    }

    fun remove(id: String) {
        channels.removeAll { it.id == id }
        saveChannels()
    }

    /** Deja los canales como [list] (la lista que viene de Home Assistant). */
    fun replaceChannels(list: List<Channel>) {
        if (list == channels.toList()) return
        channels.clear()
        channels.addAll(list)
        saveChannels()
    }

    fun saveHa(config: HaConfig?) {
        ha = config
        prefs.edit {
            if (config == null) remove("ha") else putString("ha", json.encodeToString(HaConfig.serializer(), config))
        }
    }

    fun isWatched(vid: String): Boolean = vid in watched

    fun setWatched(vid: String, value: Boolean) {
        watched.remove(vid)
        if (value) watched.add(vid)
        if (watched.size > MAX_WATCHED) watched.removeRange(0, watched.size - MAX_WATCHED)
        prefs.edit { putString("watched", watched.joinToString(",")) }
    }

    fun toggleShorts() {
        hideShorts = !hideShorts
        prefs.edit { putBoolean("hideShorts", hideShorts) }
    }

    fun togglePlayer() {
        useYouTubeApp = !useYouTubeApp
        prefs.edit { putBoolean("useYouTubeApp", useYouTubeApp) }
    }

    fun toggleSubtitles() {
        subtitles = !subtitles
        prefs.edit { putBoolean(SUBTITLES, subtitles) }
    }

    /** Vuelve a leer los ajustes que puede cambiar el reproductor. */
    fun reloadSettings() {
        subtitles = prefs.getBoolean(SUBTITLES, false)
    }

    /**
     * Nuevo: no lo has visto y salió como mucho una semana antes de añadir el canal.
     * Así, al añadir un canal solo salen como nuevos los de esa semana, y luego todos los que vayan saliendo.
     */
    fun isNew(video: Video): Boolean {
        val added = channel(video.channelId)?.addedAt ?: return false
        return video.published > added - WEEK && !isWatched(video.vid)
    }

    private fun saveChannels() {
        prefs.edit { putString("channels", json.encodeToString(channelList, channels.toList())) }
    }

    private companion object {
        const val MAX_WATCHED = 3000
        const val WEEK = 7L * 24 * 60 * 60 * 1000
    }
}

private const val PREFS = "store"
private const val SUBTITLES = "subtitles"

/** Guarda si se quieren subtítulos desde el reproductor, que no tiene el [Store]. */
fun saveSubtitles(context: Context, on: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(SUBTITLES, on) }
}

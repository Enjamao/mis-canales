package es.miscanales

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Un vídeo del historial y cuándo se abrió (en milisegundos). */
@Serializable
data class HistoryEntry(val video: Video, val at: Long)

/**
 * El historial: los vídeos que se han abierto en este aparato, el último primero, sin repetir (al volver a
 * abrir uno, sube arriba). Uno por perfil (Kids no tiene); se queda en el aparato, no va a Home Assistant
 * (lo de por dónde vas en cada vídeo, sí: eso es [ProgressSync]).
 */
class History private constructor(val profile: Profile) {
    companion object {
        private const val KEY = "history"

        /** Los que se guardan, como mucho. */
        private const val MAX = 300
        private val all = mutableMapOf<Profile, History>()

        @Synchronized
        fun of(profile: Profile): History = all.getOrPut(profile) { History(profile) }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(HistoryEntry.serializer())
    private var loaded = false

    /** El último abierto, primero. */
    val entries = mutableStateListOf<HistoryEntry>()

    /** Lee el guardado en el aparato (una vez). */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = context.getSharedPreferences(profile.prefs, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching { json.decodeFromString(serializer, saved) }.getOrNull()?.let { entries.addAll(it) }
    }

    private fun save(context: Context) {
        context.getSharedPreferences(profile.prefs, Context.MODE_PRIVATE)
            .edit { putString(KEY, json.encodeToString(serializer, entries.toList())) }
    }

    /** Apunta que se ha abierto [video] (sin la descripción, que ocupa mucho). */
    fun add(context: Context, video: Video) {
        load(context)
        entries.removeAll { it.video.vid == video.vid }
        entries.add(0, HistoryEntry(video.copy(description = ""), System.currentTimeMillis()))
        while (entries.size > MAX) entries.removeAt(entries.lastIndex)
        save(context)
    }

    /** Borra todo el historial. */
    fun clear(context: Context) {
        entries.clear()
        save(context)
    }
}

/** El nombre del grupo del historial en que va algo abierto en [at]: "Hoy", "Ayer", "Esta semana"… */
fun historyGroup(at: Long, now: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0L -> "Hoy"
        days == 1L -> "Ayer"
        days < 7L -> "Esta semana"
        days < 31L -> "Este mes"
        else -> "Antes"
    }
}

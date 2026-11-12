package es.miscanales

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.text.Normalizer
import kotlin.coroutines.cancellation.CancellationException

/** Un canal que ha salido al buscar en YouTube. */
data class FoundChannel(
    val id: String,
    val name: String,
    val avatar: String?,
    /** "@nombre", si lo trae. */
    val handle: String,
    /** "3,77 M de suscriptores", tal como lo da YouTube. */
    val subscribers: String,
) {
    fun toChannel() = Channel(id, name, avatar, System.currentTimeMillis())
}

/** Una página de resultados. Con [more] se pide la siguiente (null: no hay más). */
data class Results(
    val channels: List<FoundChannel> = emptyList(),
    val videos: List<Video> = emptyList(),
    val more: String? = null,
)

/**
 * Buscar en YouTube y ver más vídeos de un canal, leyendo los datos que la web de YouTube trae
 * dentro de sus páginas (ytInitialData) y los de su API interna para las continuaciones. Sin clave,
 * como el resto de la app; si YouTube cambia su web, aquí es donde hay que tocar.
 */
object Search {
    private val json = Json { ignoreUnknownKeys = true }

    /** Busca [query] en todo YouTube: canales y vídeos (sin Shorts si [hideShorts]). */
    suspend fun youtube(query: String, hideShorts: Boolean): Results {
        val html = YouTube.get("https://www.youtube.com/results?search_query=${Uri.encode(query.trim())}&hl=es&gl=ES")
        return page(initialData(html), hideShorts)
    }

    /** La página siguiente de una búsqueda en todo YouTube. */
    suspend fun youtubeMore(token: String, hideShorts: Boolean): Results =
        page(json.parseToJsonElement(YouTube.post("search", token)), hideShorts)

    /**
     * Los vídeos de tus [channels] que encajan con [query]: los ya cargados en la app y los que
     * encuentra YouTube buscando dentro de cada canal (así salen también los antiguos).
     */
    suspend fun mine(query: String, channels: List<Channel>, loaded: List<Video>, hideShorts: Boolean): List<Video> =
        coroutineScope {
            val local = loaded.filter { matches(it, query) }
            val remote = channels.map { channel ->
                async {
                    try {
                        inChannel(channel, query, hideShorts)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
            (local + remote).distinctBy { it.vid }.sortedByDescending { it.published }
        }

    /** Busca [query] dentro de un canal (su pestaña de búsqueda). */
    private suspend fun inChannel(channel: Channel, query: String, hideShorts: Boolean): List<Video> {
        val html = YouTube.get(
            "https://www.youtube.com/channel/${channel.id}/search?query=${Uri.encode(query.trim())}&hl=es&gl=ES",
        )
        return page(initialData(html), hideShorts).videos
            .filter { it.channelId.isEmpty() || it.channelId == channel.id }
            .map { it.copy(channelId = channel.id, channelName = channel.name) }
    }

    /** Si [video] encaja con lo escrito: todas las palabras en el título o en el canal, sin mirar tildes. */
    fun matches(video: Video, query: String): Boolean {
        val words = plain(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        val text = plain(video.title + " " + video.channelName)
        return words.all { it in text }
    }

    private fun plain(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /**
     * Los vídeos de la pestaña Vídeos de [channel], de 30 en 30, del más nuevo al más viejo:
     * la primera página con [token] null, y las siguientes con el [Results.more] de la anterior.
     */
    suspend fun channelVideos(channel: Channel, token: String?): Results {
        val data = if (token == null) {
            val all = initialData(YouTube.get("https://www.youtube.com/channel/${channel.id}/videos?hl=es&gl=ES"))
            // solo la cuadrícula de vídeos (la página trae otras continuaciones, como los comentarios)
            find(all, "richGridRenderer") ?: throw IOException("La página del canal no trae vídeos")
        } else {
            json.parseToJsonElement(YouTube.post("browse", token))
        }
        val page = page(data, hideShorts = false)
        return page.copy(
            channels = emptyList(),
            videos = page.videos.map { it.copy(channelId = channel.id, channelName = channel.name) },
        )
    }

    // ---- leer los datos ----

    /** El objeto ytInitialData de una página de YouTube. */
    private fun initialData(html: String): JsonElement {
        Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.let { YouTube.clientVersion = it.groupValues[1] }
        val marker = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ").firstOrNull { html.contains(it) }
            ?: throw IOException(if (html.contains("consent.youtube.com")) "YouTube pide aceptar cookies" else "La página no trae datos")
        val start = html.indexOf(marker) + marker.length
        val end = html.indexOf(";</script>", start).takeIf { it > 0 } ?: throw IOException("Datos de la página cortados")
        return json.parseToJsonElement(html.substring(start, end))
    }

    private val wanted = setOf("videoRenderer", "channelRenderer", "lockupViewModel", "continuationItemRenderer")

    /** Lo que interesa de unos datos, en orden: vídeos, canales y la continuación. */
    private fun page(data: JsonElement, hideShorts: Boolean): Results {
        val found = mutableListOf<Pair<String, JsonObject>>()
        collect(data, found)
        val now = System.currentTimeMillis()
        val channels = mutableListOf<FoundChannel>()
        val videos = mutableListOf<Video>()
        var more: String? = null
        for ((kind, item) in found) {
            when (kind) {
                "videoRenderer" -> {
                    val (video, short) = fromRenderer(item, now) ?: continue
                    if (!(short && hideShorts)) videos += video
                }
                "lockupViewModel" -> fromLockup(item, now)?.let { videos += it }
                "channelRenderer" -> fromChannel(item)?.let { channels += it }
                "continuationItemRenderer" ->
                    item.at("continuationEndpoint", "continuationCommand", "token").str()?.let { more = it }
            }
        }
        return Results(channels.distinctBy { it.id }, videos.distinctBy { it.vid }, more)
    }

    /** Recorre los datos y apunta cada objeto que interesa (sin entrar dentro de ellos). */
    private fun collect(node: JsonElement, out: MutableList<Pair<String, JsonObject>>) {
        when (node) {
            is JsonObject -> for ((key, value) in node) {
                if (key in wanted && value is JsonObject) out += key to value else collect(value, out)
            }
            is JsonArray -> node.forEach { collect(it, out) }
            else -> {}
        }
    }

    /** El primer objeto con ese nombre, buscando a cualquier profundidad. */
    private fun find(node: JsonElement, key: String): JsonElement? = when (node) {
        is JsonObject -> node[key] ?: node.values.firstNotNullOfOrNull { find(it, key) }
        is JsonArray -> node.firstNotNullOfOrNull { find(it, key) }
        else -> null
    }

    /** Un vídeo del formato de siempre (búsquedas), y si es un Short. */
    private fun fromRenderer(v: JsonObject, now: Long): Pair<Video, Boolean>? {
        val vid = v["videoId"].str() ?: return null
        val title = v["title"].text().ifBlank { return null }
        val owner = (v["ownerText"] ?: v["longBylineText"] ?: v["shortBylineText"]).at("runs", 0)
        val live = (v["badges"] as? JsonArray).orEmpty().any {
            it.at("metadataBadgeRenderer", "style").str() == "BADGE_STYLE_TYPE_LIVE_NOW"
        }
        val endpoint = v["navigationEndpoint"]
        val short = endpoint.at("reelWatchEndpoint") != null ||
            endpoint.at("commandMetadata", "webCommandMetadata", "url").str().orEmpty().startsWith("/shorts/")
        val video = Video(
            vid = vid,
            title = title,
            channelId = owner.at("navigationEndpoint", "browseEndpoint", "browseId").str().orEmpty(),
            channelName = owner.at("text").str().orEmpty(),
            published = ago(v["publishedTimeText"].text(), now) ?: 0L,
            description = v.at("detailedMetadataSnippets", 0, "snippetText").text(),
            views = count(v["viewCountText"].text()),
            duration = v["lengthText"].text().ifBlank { if (live) "EN DIRECTO" else "" },
        )
        return video to short
    }

    /** Un vídeo del formato nuevo (la pestaña Vídeos de los canales). */
    private fun fromLockup(l: JsonObject, now: Long): Video? {
        if (l["contentType"].str() != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val vid = l["contentId"].str() ?: return null
        val meta = l.at("metadata", "lockupMetadataViewModel")
        val title = meta.at("title", "content").str()?.ifBlank { null } ?: return null
        val parts = (meta.at("metadata", "contentMetadataViewModel", "metadataRows") as? JsonArray).orEmpty()
            .flatMap { row -> (row.at("metadataParts") as? JsonArray).orEmpty() }
            .mapNotNull { it.at("text", "content").str() }
        val published = parts.firstNotNullOfOrNull { ago(it, now) }
        val badges = mutableListOf<Pair<String, JsonObject>>()
        collectBadges(l["contentImage"], badges)
        return Video(
            vid = vid,
            title = title,
            channelId = "",
            channelName = "",
            published = published ?: 0L,
            views = parts.firstOrNull { ago(it, now) == null }?.let(::count),
            duration = badges.firstNotNullOfOrNull { it.second["text"].str() }.orEmpty(),
        )
    }

    private fun collectBadges(node: JsonElement?, out: MutableList<Pair<String, JsonObject>>) {
        when (node) {
            is JsonObject -> for ((key, value) in node) {
                if (key == "thumbnailBadgeViewModel" && value is JsonObject) out += key to value else collectBadges(value, out)
            }
            is JsonArray -> node.forEach { collectBadges(it, out) }
            else -> {}
        }
    }

    private fun fromChannel(c: JsonObject): FoundChannel? {
        val id = c["channelId"].str() ?: return null
        val name = c["title"].text().ifBlank { return null }
        val avatar = (c.at("thumbnail", "thumbnails") as? JsonArray)?.lastOrNull().at("url").str()
            ?.let { if (it.startsWith("//")) "https:$it" else it }
        // YouTube pone el @nombre en subscriberCountText y los suscriptores en videoCountText
        val texts = listOf(c["subscriberCountText"].text(), c["videoCountText"].text()).filter { it.isNotBlank() }
        return FoundChannel(
            id = id,
            name = name,
            avatar = avatar,
            handle = texts.firstOrNull { it.startsWith("@") }.orEmpty(),
            subscribers = texts.firstOrNull { !it.startsWith("@") }.orEmpty().replace(' ', ' '),
        )
    }

    // ---- fechas y números, en español o en inglés ----

    private val agoPatterns = listOf(
        // "hace 3 días", "hace 23 h", "hace 2 a", "hace 3 sem.", "Emitido hace 1 mes"
        Regex(
            "hace\\s+(\\d+)\\s*(segundos?|minutos?|horas?|días?|dias?|semanas?|meses|mes|años?|min|sem|seg|s|h|d|m|a)(?![a-zñ])",
            RegexOption.IGNORE_CASE,
        ) to mapOf(
            "s" to SECOND, "seg" to SECOND, "segundo" to SECOND, "min" to MINUTE, "minuto" to MINUTE,
            "h" to HOUR, "hora" to HOUR, "d" to DAY, "día" to DAY, "dia" to DAY, "sem" to WEEK, "semana" to WEEK,
            "m" to MONTH, "mes" to MONTH, "a" to YEAR, "año" to YEAR,
        ),
        // "3 days ago", "Streamed 2 weeks ago"
        Regex("(\\d+)\\s+(second|minute|hour|day|week|month|year)s?\\s+ago", RegexOption.IGNORE_CASE) to mapOf(
            "second" to SECOND, "minute" to MINUTE, "hour" to HOUR, "day" to DAY,
            "week" to WEEK, "month" to MONTH, "year" to YEAR,
        ),
        // "23h ago", "5d ago", "3mo ago", "9y ago"
        Regex("(\\d+)\\s*(mo|s|m|h|d|w|y)\\s+ago", RegexOption.IGNORE_CASE) to mapOf(
            "s" to SECOND, "m" to MINUTE, "h" to HOUR, "d" to DAY, "w" to WEEK, "mo" to MONTH, "y" to YEAR,
        ),
    )

    /** "hace 3 días" (o "3 days ago") → la fecha aproximada, o null si no es una fecha. */
    fun ago(text: String, now: Long): Long? {
        for ((pattern, units) in agoPatterns) {
            val match = pattern.find(text) ?: continue
            val unit = match.groupValues[2].lowercase()
            val base = when {
                unit in units -> unit
                unit == "meses" -> "mes"
                else -> unit.removeSuffix("s")
            }
            val millis = units[base] ?: continue
            return now - match.groupValues[1].toLong() * millis
        }
        return null
    }

    /** "1.102.147 visualizaciones", "2,9 M", "95,673 views", "63,7 K" → el número. */
    fun count(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val t = text.replace(' ', ' ')
        val match = Regex("(\\d+(?:[.,]\\d+)*)\\s*(millones|millón|mil|K|M|B)?(?![a-zñ])").find(t) ?: return null
        val number = match.groupValues[1]
        val multiplier = when (match.groupValues[2]) {
            "K", "mil" -> 1e3
            "M", "millones", "millón" -> 1e6
            "B" -> 1e9
            else -> return number.filter { it.isDigit() }.toLongOrNull()
        }
        return number.replace(',', '.').toDoubleOrNull()?.let { (it * multiplier).toLong() }
    }

    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
    private const val MONTH = 30 * DAY
    private const val YEAR = 365 * DAY
}

// ---- moverse por los datos ----

private fun JsonElement?.at(vararg path: Any): JsonElement? {
    var node = this
    for (step in path) {
        node = when (step) {
            is String -> (node as? JsonObject)?.get(step)
            is Int -> (node as? JsonArray)?.getOrNull(step)
            else -> null
        } ?: return null
    }
    return node
}

private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** El texto de los objetos de texto de YouTube: {simpleText}, {runs: [{text}]} o {content}. */
private fun JsonElement?.text(): String {
    val o = this as? JsonObject ?: return ""
    o["simpleText"].str()?.let { return it }
    o["content"].str()?.let { return it }
    return (o["runs"] as? JsonArray).orEmpty().joinToString("") { it.at("text").str().orEmpty() }
}

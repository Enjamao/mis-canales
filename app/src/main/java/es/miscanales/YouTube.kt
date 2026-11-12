package es.miscanales

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.util.Xml
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** Un vídeo de uno de tus canales, tal como viene en el feed RSS de YouTube. */
@Serializable
data class Video(
    val vid: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    /** Cuándo se publicó, en milisegundos. */
    val published: Long,
    val description: String = "",
    val views: Long? = null,
)

class HttpError(val code: Int) : IOException("HTTP $code")

/** No hay ningún canal con ese nombre o enlace; [reason] dice por qué, para poder arreglarlo. */
class ChannelNotFound(reason: String) : IOException(reason)

/**
 * Lo poco que la app necesita de YouTube: el feed RSS público de cada canal (sus últimos 15 vídeos)
 * y la página del canal, para pasar de @nombre a su ID. Sin cuenta, sin clave y sin recomendaciones.
 */
object YouTube {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val BROWSER =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val idInText = Regex("(?:^|/channel/)(UC[\\w-]{22})")
    private val videoInText = Regex("(?:youtu\\.be/|[?&]v=|/shorts/|/live/|/embed/)([\\w-]{11})")

    /** La dirección del canal que ha subido un vídeo, según el oEmbed de YouTube. */
    private suspend fun authorOf(videoId: String): String {
        val watch = Uri.encode("https://www.youtube.com/watch?v=$videoId")
        val body = try {
            get("https://www.youtube.com/oembed?format=json&url=$watch")
        } catch (e: HttpError) {
            throw ChannelNotFound("no encuentro ese vídeo (${e.code})")
        }
        val author = runCatching {
            Json.parseToJsonElement(body).jsonObject["author_url"]?.jsonPrimitive?.content
        }.getOrNull()
        return author?.takeIf { "youtube.com/" in it } ?: throw ChannelNotFound("no sé de qué canal es ese vídeo")
    }

    // Dónde aparece el ID en la página de un canal, de más a menos fiable.
    private val idInPage = listOf(
        Regex("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/channel/(UC[\\w-]{22})\""),
        Regex("\"externalId\":\"(UC[\\w-]{22})\""),
        Regex("itemprop=\"identifier\" content=\"(UC[\\w-]{22})\""),
        Regex("\"browseId\":\"(UC[\\w-]{22})\""),
        // el enlace al feed RSS del canal (en la cabecera y en "rssUrl")
        Regex("channel_id=(UC[\\w-]{22})"),
    )

    /** Los vídeos de todos los [channels] juntos, del más nuevo al más viejo, y los canales que fallaron. */
    suspend fun everything(channels: List<Channel>, hideShorts: Boolean): Pair<List<Video>, List<Channel>> =
        coroutineScope {
            val results = channels.map { channel ->
                async { channel to runCatching { videos(channel, hideShorts) } }
            }.awaitAll()
            results.forEach { (_, result) ->
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
            }
            val videos = results.flatMap { (_, result) -> result.getOrDefault(emptyList()) }
                .distinctBy { it.vid }
                .sortedByDescending { it.published }
            videos to results.filter { it.second.isFailure }.map { it.first }
        }

    /**
     * Los últimos vídeos de [channel]. Con [hideShorts], los de su lista de vídeos largos, que no tiene Shorts.
     * El feed RSS de YouTube a veces responde 404 a todo durante horas; entonces se sacan de la página del canal.
     */
    suspend fun videos(channel: Channel, hideShorts: Boolean): List<Video> {
        val rss = try {
            if (hideShorts) {
                // UULF + el ID sin "UC" es la lista de vídeos del canal sin Shorts ni directos
                try {
                    feed("playlist_id=UULF" + channel.id.removePrefix("UC"))
                } catch (e: HttpError) {
                    // un canal sin vídeos largos no tiene esa lista
                    feed("channel_id=${channel.id}")
                }
            } else {
                feed("channel_id=${channel.id}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 404, 500, o una página que no es un feed
            return try {
                pageVideos(channel)
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                throw e
            }
        }
        return rss.videos.map { it.copy(channelId = channel.id, channelName = channel.name) }
    }

    /** Plan B: los vídeos de la pestaña "Vídeos" del canal, sacados de los datos de la página. */
    private suspend fun pageVideos(channel: Channel): List<Video> {
        // en inglés, para entender "2 days ago"
        val html = get("https://www.youtube.com/channel/${channel.id}/videos?hl=en&gl=ES")
        val now = System.currentTimeMillis()
        val videos = mutableListOf<Video>()
        // el formato de siempre, y el nuevo con el que YouTube va cambiando sus listas
        for (part in parts(html, "\"videoRenderer\":{")) {
            val vid = grab(part, "^\"videoRenderer\":\\{\"videoId\":\"([\\w-]{11})\"") ?: continue
            val title = grab(part, "\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")?.let(::unescape) ?: continue
            val ago = grab(part, "\"publishedTimeText\":\\{\"simpleText\":\"([^\"]*)\"")
            val views = grab(part, "\"viewCountText\":\\{\"simpleText\":\"([\\d,.]+) views")?.filter { it.isDigit() }?.toLongOrNull()
            videos += Video(vid, title, channel.id, channel.name, ago?.let { agoToMillis(it, now) } ?: 0L, "", views)
        }
        for (part in parts(html, "\"lockupViewModel\":{")) {
            val vid = grab(part, "\"contentId\":\"([\\w-]{11})\",\"contentType\":\"LOCKUP_CONTENT_TYPE_VIDEO\"") ?: continue
            val title = grab(part, "\"lockupMetadataViewModel\":\\{\"title\":\\{\"content\":\"((?:[^\"\\\\]|\\\\.)*)\"")?.let(::unescape) ?: continue
            val ago = grab(part, "\"content\":\"((?:Streamed )?\\d+ (?:second|minute|hour|day|week|month|year)s? ago)\"")
            videos += Video(vid, title, channel.id, channel.name, ago?.let { agoToMillis(it, now) } ?: 0L)
        }
        val unique = videos.distinctBy { it.vid }.take(15)
        if (unique.isEmpty()) throw IOException("La página del canal no trae vídeos")
        return unique
    }

    /** Los trozos de [html] que empiezan en cada [marker], hasta el siguiente. */
    private fun parts(html: String, marker: String): List<String> {
        val starts = generateSequence(html.indexOf(marker).takeIf { it >= 0 }) { from ->
            html.indexOf(marker, from + marker.length).takeIf { it >= 0 }
        }.toList()
        return starts.mapIndexed { i, start ->
            html.substring(start, starts.getOrNull(i + 1) ?: minOf(html.length, start + 20_000))
        }
    }

    private fun grab(text: String, pattern: String): String? = Regex(pattern).find(text)?.groupValues?.get(1)

    /** Un texto tal como va dentro de una cadena JSON ("\u0026", "\"") a texto normal. */
    private fun unescape(raw: String): String =
        runCatching { Json.parseToJsonElement("\"$raw\"").jsonPrimitive.content }.getOrDefault(raw)

    /** "3 days ago" → la fecha aproximada. */
    private fun agoToMillis(text: String, now: Long): Long? {
        val match = Regex("(\\d+) (second|minute|hour|day|week|month|year)s? ago").find(text) ?: return null
        val n = match.groupValues[1].toLong()
        val unit = when (match.groupValues[2]) {
            "second" -> 1_000L
            "minute" -> 60_000L
            "hour" -> 3_600_000L
            "day" -> 86_400_000L
            "week" -> 7 * 86_400_000L
            "month" -> 30 * 86_400_000L
            else -> 365 * 86_400_000L
        }
        return now - n * unit
    }

    /**
     * Busca el canal de lo que se haya escrito: @nombre, el enlace del canal o su ID.
     * Todo sale de la página del canal; el feed RSS solo hace falta si la página no trae el nombre.
     */
    suspend fun resolve(input: String): Channel {
        val text = input.trim()
        if (text.isEmpty()) throw ChannelNotFound("no has escrito nada")
        val typedId = idInText.find(text)?.groupValues?.get(1)
        // un vídeo (lo normal al compartir desde la app de YouTube): se busca el canal de quien lo ha subido
        val videoId = if (typedId == null) videoInText.find(text)?.groupValues?.get(1) else null
        if (videoId != null) return resolve(authorOf(videoId))
        val html: String? = if (typedId != null) {
            // con el ID ya basta; la página solo hace falta para el nombre y la foto
            try {
                get("https://www.youtube.com/channel/$typedId")
            } catch (e: IOException) {
                null
            }
        } else {
            val url = pageUrl(text)
            try {
                get(url)
            } catch (e: HttpError) {
                throw ChannelNotFound("YouTube responde ${e.code} a ${url.removePrefix("https://www.")}")
            }
        }
        val id = typedId ?: html?.let(::idIn)
            ?: throw ChannelNotFound("la página del canal no trae su ID" + (html?.let { consentHint(it) } ?: ""))
        var name = html?.let { meta(it, "og:title") }.orEmpty()
        if (name.isBlank()) {
            name = try {
                feed("channel_id=$id").title
            } catch (e: IOException) {
                ""
            }
        }
        val avatar = html?.let { meta(it, "og:image") }?.replace(Regex("=s\\d+"), "=s240")
        return Channel(id, name.ifBlank { id }, avatar, System.currentTimeMillis())
    }

    /** Si YouTube ha devuelto su página de cookies o de "no eres un robot", lo dice. */
    private fun consentHint(html: String): String = when {
        "consent.youtube.com" in html || "consent.google.com" in html -> " (sale la página de cookies)"
        "/sorry/" in html || "unusual traffic" in html -> " (YouTube pide comprobar que no eres un robot)"
        else -> ""
    }

    /** La página del canal: youtube.com/@nombre, /c/nombre o /user/nombre. */
    private fun pageUrl(text: String): String {
        val path = text.substringAfter("youtube.com/", text).substringBefore('?').trim('/')
        val clean = when {
            path.startsWith("@") -> path.substringBefore('/')
            path.startsWith("c/") || path.startsWith("user/") -> path.split('/').take(2).joinToString("/")
            else -> "@" + path.replace(" ", "")
        }
        return "https://www.youtube.com/" + Uri.encode(clean, "@/")
    }

    private fun idIn(html: String): String? = idInPage.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }

    private fun meta(html: String, property: String): String? =
        Regex("<meta property=\"$property\" content=\"([^\"]*)\"").find(html)?.groupValues?.get(1)
            ?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }

    private class RssFeed(val title: String, val videos: List<Video>)

    private suspend fun feed(query: String): RssFeed =
        parse(get("https://www.youtube.com/feeds/videos.xml?$query"))

    private fun parse(xml: String): RssFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())
        val videos = mutableListOf<Video>()
        var feedTitle = ""
        var inEntry = false
        var vid = ""
        var title = ""
        var channelId = ""
        var published = 0L
        var description = ""
        var views: Long? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val event = parser.eventType
            if (event != XmlPullParser.START_TAG && event != XmlPullParser.END_TAG) continue
            // sin espacios de nombres, "yt:videoId" y "media:title" llegan con su prefijo
            val name = parser.name.substringAfter(':')
            if (event == XmlPullParser.END_TAG) {
                if (name == "entry") {
                    inEntry = false
                    if (vid.isNotEmpty()) {
                        videos += Video(vid, title, channelId, "", published, description.trim(), views)
                    }
                }
                continue
            }
            when (name) {
                "entry" -> {
                    inEntry = true
                    vid = ""
                    title = ""
                    channelId = ""
                    published = 0L
                    description = ""
                    views = null
                }
                // <title> va antes que <media:title>, y fuera de <entry> es el nombre del canal o la lista
                "title" -> {
                    val text = parser.nextText()
                    if (inEntry) {
                        if (title.isEmpty()) title = text
                    } else if (feedTitle.isEmpty()) {
                        feedTitle = text
                    }
                }
                "videoId" -> if (inEntry) vid = parser.nextText().trim()
                "channelId" -> if (inEntry) channelId = parser.nextText().trim()
                "published" -> if (inEntry) published = date(parser.nextText())
                "description" -> if (inEntry) description = parser.nextText()
                "statistics" -> if (inEntry) views = parser.getAttributeValue(null, "views")?.toLongOrNull()
            }
        }
        return RssFeed(feedTitle, videos)
    }

    private fun date(text: String): Long =
        runCatching { OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli() }.getOrDefault(0L)

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("User-Agent", BROWSER)
            .header("Accept-Language", "es-ES,es;q=0.9")
            // se salta la página de consentimiento de cookies que YouTube enseña en la UE
            .header("Cookie", "SOCS=CAI; CONSENT=YES+1")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpError(response.code)
            response.body.string()
        }
    }
}

private val youtubeApps = listOf(
    "com.google.android.youtube.tv", // Android TV y Google TV
    "com.amazon.firetv.youtube", // Fire TV
    "com.amazon.firetv.youtube.tv", // Fire TV, app nueva
    null, // cualquier otra app que abra enlaces de YouTube
)

/** Abre [vid] en la app de YouTube de la tele, que vuelve aquí al acabar o al pulsar Atrás. */
fun Context.playOnYouTube(vid: String) {
    val link = "https://www.youtube.com/watch?v=$vid".toUri()
    for (app in youtubeApps) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, link).setPackage(app)
                    // YouTube para TV abre así solo el reproductor, y al terminar vuelve aquí
                    // en vez de seguir con sus recomendaciones.
                    .putExtra("force_fullscreen", true)
                    .putExtra("finish_on_ended", true),
            )
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    Toast.makeText(this, "Instala YouTube para ver los vídeos", Toast.LENGTH_LONG).show()
}

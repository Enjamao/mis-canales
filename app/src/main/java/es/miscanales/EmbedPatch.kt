package es.miscanales

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * En la tele: la app pide ella misma la página del reproductor de YouTube (la que va dentro del
 * marco) y le añade unos scripts al principio, antes que los de YouTube, para que crea estar en el
 * Chrome de un PC con una pantalla grande. Así funciona con cualquier WebView: la del Fire TV no
 * sabe meter scripts al empezar una página por su cuenta.
 */
object EmbedPatch {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    /** Cabeceras que no se copian: las pone OkHttp, o dirían que esto es un Android. */
    private fun skipRequest(name: String) = name.lowercase().let {
        it == "accept-encoding" || it == "user-agent" || it == "cookie" || it == "x-requested-with" ||
            it.startsWith("sec-ch-ua")
    }

    /** Cabeceras de la respuesta que ya no valen: el cuerpo cambia y los scripts añadidos no llevan permiso. */
    private fun skipResponse(name: String) = name.lowercase().let {
        it.startsWith("content-security-policy") || it == "content-encoding" || it == "content-length" ||
            it == "transfer-encoding" || it == "content-type" || it == "set-cookie"
    }

    private val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)

    fun isEmbed(request: WebResourceRequest): Boolean {
        val url = request.url
        return request.method == "GET" && !request.isForMainFrame &&
            url.scheme == "https" && url.host == "www.youtube.com" && url.path.orEmpty().startsWith("/embed/")
    }

    /** La página del reproductor con [scripts] al principio, o null si algo falla (la WebView la pide normal). */
    fun fetch(request: WebResourceRequest, userAgent: String, referer: String, scripts: String): WebResourceResponse? {
        return runCatching {
            val url = request.url.toString()
            val builder = Request.Builder().url(url)
            request.requestHeaders.forEach { (name, value) -> if (!skipRequest(name)) builder.header(name, value) }
            builder.header("User-Agent", userAgent)
            builder.header("Sec-CH-UA", "\"Chromium\";v=\"131\", \"Google Chrome\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            builder.header("Sec-CH-UA-Mobile", "?0")
            builder.header("Sec-CH-UA-Platform", "\"Windows\"")
            // sin quién lo inserta, YouTube da el error 153
            if (request.requestHeaders.keys.none { it.equals("referer", true) }) builder.header("Referer", referer)
            val cookies = CookieManager.getInstance()
            cookies.getCookie(url)?.let { builder.header("Cookie", it) }
            http.newCall(builder.build()).execute().use { response ->
                response.headers("Set-Cookie").forEach { cookies.setCookie(url, it) }
                val type = response.header("Content-Type").orEmpty()
                if (response.code != 200 || !type.contains("text/html")) return null
                val html = response.body.string()
                val match = head.find(html) ?: return null
                val patched = html.substring(0, match.range.last + 1) +
                    "<script>" + scripts + "</script>" + html.substring(match.range.last + 1)
                val headers = LinkedHashMap<String, String>()
                response.headers.names().forEach { name -> if (!skipResponse(name)) headers[name] = response.headers(name).last() }
                WebResourceResponse(
                    "text/html", "utf-8", 200, "OK", headers,
                    ByteArrayInputStream(patched.toByteArray(Charsets.UTF_8)),
                )
            }
        }.getOrNull()
    }
}

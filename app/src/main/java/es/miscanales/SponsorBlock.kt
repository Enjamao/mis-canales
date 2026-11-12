package es.miscanales

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Los tramos de patrocinio de un vídeo, de SponsorBlock (sponsor.ajay.app), una base de datos
 * pública que hace la comunidad. Se pregunta solo por los 4 primeros caracteres del hash del
 * vídeo, así que SponsorBlock no sabe qué vídeo estás viendo.
 */
object SponsorBlock {
    /** Patrocinios, autopromoción y los "suscríbete y dale a la campanita". */
    private val categories = listOf("sponsor", "selfpromo", "interaction")

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Los tramos que hay que saltar, como pares [inicio, fin] en segundos. Vacío si no hay o si falla. */
    suspend fun segments(vid: String): List<List<Double>> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = MessageDigest.getInstance("SHA-256").digest(vid.toByteArray())
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            val wanted = Uri.encode(categories.joinToString(",", "[", "]") { "\"$it\"" })
            val request = Request.Builder()
                .url("https://sponsor.ajay.app/api/skipSegments/${hash.take(4)}?categories=$wanted")
                .header("User-Agent", "MisCanales (Android)")
                .build()
            http.newCall(request).execute().use { response ->
                // 404: no hay ningún tramo para esos vídeos
                if (!response.isSuccessful) return@runCatching emptyList()
                Json.parseToJsonElement(response.body.string()).jsonArray
                    .map { it.jsonObject }
                    .firstOrNull { it["videoID"]?.jsonPrimitive?.content == vid }
                    ?.get("segments")?.jsonArray
                    ?.map { it.jsonObject }
                    ?.filter { (it["actionType"]?.jsonPrimitive?.content ?: "skip") == "skip" }
                    ?.mapNotNull { segment -> segment["segment"]?.jsonArray?.map { it.jsonPrimitive.double } }
                    ?.filter { it.size == 2 && it[1] > it[0] }
                    .orEmpty()
            }
        }.getOrDefault(emptyList())
    }
}

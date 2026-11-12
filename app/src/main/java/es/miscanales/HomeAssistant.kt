package es.miscanales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Dónde está tu Home Assistant y con qué token se entra. */
@Serializable
data class HaConfig(
    /** La dirección de fuera de casa, por ejemplo https://micasa.duckdns.org:8123 */
    val url: String,
    /** Un token de acceso de larga duración (tu perfil de HA → Seguridad). */
    val token: String,
    /** La dirección de dentro de casa (opcional), por ejemplo http://192.168.1.10:8123. Se prueba primero. */
    val localUrl: String = "",
    /** La lista de tareas donde se guardan los canales. */
    val entity: String = DEFAULT_ENTITY,
) {
    companion object {
        const val DEFAULT_ENTITY = "todo.mis_canales"
    }
}

/** Un elemento de la lista: el nombre del canal y, en la descripción, su ID y su foto. */
data class HaItem(val uid: String, val summary: String, val description: String)

/** Home Assistant ha contestado, pero con un error (token, lista que no existe…). */
class HaError(message: String) : IOException(message)

/**
 * La lista de canales compartida entre la tele y el móvil: una lista de tareas de Home Assistant
 * (la integración "Local To-do"), manejada con las acciones todo.get_items, add_item, update_item
 * y remove_item de su API REST.
 */
object HomeAssistant {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // para la dirección de casa: si no contesta enseguida es que no estás en casa
    private val quick = http.newBuilder().connectTimeout(3, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    // [list] es la lista de tareas: la de canales salvo que se diga otra (la del progreso)

    suspend fun items(config: HaConfig, list: String = config.entity): List<HaItem> {
        val response = call(config, "get_items?return_response", buildJsonObject { put("entity_id", list) })
        val items = (response as? JsonObject)
            ?.get("service_response")?.jsonObject
            ?.get(list)?.jsonObject
            ?.get("items")?.jsonArray
            ?: throw HaError("no ha devuelto la lista $list")
        return items.map { it.jsonObject }.map { HaItem(it.text("uid"), it.text("summary"), it.text("description")) }
    }

    suspend fun add(config: HaConfig, summary: String, description: String, list: String = config.entity) {
        call(
            config,
            "add_item",
            buildJsonObject {
                put("entity_id", list)
                put("item", summary)
                put("description", description)
            },
        )
    }

    suspend fun update(config: HaConfig, uid: String, rename: String, description: String, list: String = config.entity) {
        call(
            config,
            "update_item",
            buildJsonObject {
                put("entity_id", list)
                put("item", uid)
                put("rename", rename)
                put("description", description)
            },
        )
    }

    suspend fun remove(config: HaConfig, uid: String, list: String = config.entity) {
        call(
            config,
            "remove_item",
            buildJsonObject {
                put("entity_id", list)
                put("item", uid)
            },
        )
    }

    /** Llama a una acción de la lista, primero por la dirección de casa (si hay) y luego por la de fuera. */
    private suspend fun call(config: HaConfig, action: String, body: JsonObject): JsonElement = withContext(Dispatchers.IO) {
        val bases = listOf(config.localUrl, config.url)
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() }
            .distinct()
        var last: IOException = HaError("falta la dirección de Home Assistant")
        for ((i, base) in bases.withIndex()) {
            val client = if (i < bases.lastIndex) quick else http
            val request = try {
                Request.Builder()
                    .url("$base/api/services/todo/$action")
                    .header("Authorization", "Bearer ${config.token.trim()}")
                    .post(body.toString().toRequestBody(jsonType))
                    .build()
            } catch (e: IllegalArgumentException) {
                last = HaError("la dirección $base no es válida (tiene que empezar por http:// o https://)")
                continue
            }
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    when {
                        response.code == 401 -> throw HaError("no acepta el token (401)")
                        !response.isSuccessful -> throw HaError("responde ${response.code}: ${text.take(160)}")
                    }
                    return@withContext json.parseToJsonElement(text.ifBlank { "[]" })
                }
            } catch (e: HaError) {
                // HA ha contestado: probar la otra dirección no cambiaría nada
                throw e
            } catch (e: IOException) {
                last = e
            }
        }
        throw last
    }
}

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

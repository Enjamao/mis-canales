package es.miscanales

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * "Ver en la tele" desde el móvil. Home Assistant, con su integración Android Debug Bridge (ADB),
 * despierta la tele y le abre Mis Canales con el vídeo, desde el momento en que ibas.
 * En la tele lo recoge [MainActivity] (los extras "play" y "start").
 */
object Tele {
    private val videoId = Regex("[\\w-]{11}")

    // no depende de la pantalla desde la que se manda: se puede cerrar mientras tanto
    private val scope = MainScope()

    /** Manda el vídeo y lo cuenta con avisos cortos. Si se encuentra la tele, se guarda con [remember]. */
    fun sendAndTell(context: Context, config: HaConfig, vid: String, start: Int, remember: (String) -> Unit) {
        val app = context.applicationContext
        Toast.makeText(app, "Mandando a la tele…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val message = try {
                send(config, vid, start, remember)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(e)
            }
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    /** El comando para la tele. El ID del vídeo solo lleva letras, números, - y _, así que no hay nada que escapar. */
    fun command(vid: String, start: Int): String {
        require(videoId.matches(vid)) { "ID de vídeo raro: $vid" }
        return "input keyevent KEYCODE_WAKEUP; am start -n es.miscanales/.MainActivity " +
            "--activity-clear-top --activity-single-top --es play $vid --ei start ${start.coerceAtLeast(0)}"
    }

    /**
     * Manda el vídeo a la tele y devuelve qué decirle al usuario. Si no hay tele elegida y Home
     * Assistant solo tiene una, se usa esa y se guarda con [remember].
     */
    suspend fun send(config: HaConfig, vid: String, start: Int, remember: (String) -> Unit): String {
        val tv = config.tv.ifBlank {
            val found = HomeAssistant.tvs(config)
            when (found.size) {
                0 -> return "No encuentro la tele en Home Assistant: añade el Fire TV con la integración " +
                    "Android Debug Bridge (en Canales → Ajustes está explicado)."
                1 -> found[0].entity.also(remember)
                else -> return "Home Assistant tiene varias teles: elige cuál en Canales → Ajustes."
            }
        }
        HomeAssistant.adb(config, tv, command(vid, start))
        return "Enviado a la tele" + if (start > 0) " (desde ${clock(start)})" else ""
    }

    /** El mensaje para cuando no se ha podido mandar. */
    fun error(e: Exception): String = when (e) {
        is HaError -> "No se ha podido mandar a la tele: ${e.message}."
        else -> "No se ha podido mandar a la tele (${e.message}). Mira la conexión."
    }
}

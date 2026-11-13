package es.miscanales

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.security.MessageDigest
import java.time.LocalDate

/** El PIN de los mayores: se guarda solo su huella, en cada aparato. */
object Pin {
    const val LENGTH = 4

    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("miscanales-pin:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/**
 * El tiempo que llevan viendo hoy en Mis Canales Kids (en este aparato). Lo suma el reproductor
 * mientras hay un vídeo en marcha; al día siguiente vuelve a cero. Con el PIN se dan 30 min más.
 */
object KidsTime {
    const val EXTRA_SECONDS = 30 * 60

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today() = LocalDate.now().toString()

    /** Empieza un día nuevo si hace falta. */
    private fun roll(context: Context) {
        val prefs = prefs(context)
        if (prefs.getString("kidsDay", null) != today()) {
            prefs.edit {
                putString("kidsDay", today())
                putInt("kidsUsed", 0)
                putInt("kidsExtra", 0)
            }
        }
    }

    /** Segundos vistos hoy. */
    fun used(context: Context): Int {
        roll(context)
        return prefs(context).getInt("kidsUsed", 0)
    }

    @Synchronized
    fun add(context: Context, seconds: Int) {
        if (seconds <= 0) return
        roll(context)
        val prefs = prefs(context)
        prefs.edit { putInt("kidsUsed", prefs.getInt("kidsUsed", 0) + seconds) }
    }

    /** Los segundos que quedan hoy con un límite de [limitMinutes] (null: sin límite). */
    fun remaining(context: Context, limitMinutes: Int): Int? {
        if (limitMinutes <= 0) return null
        roll(context)
        val prefs = prefs(context)
        return limitMinutes * 60 + prefs.getInt("kidsExtra", 0) - prefs.getInt("kidsUsed", 0)
    }

    /** Media hora más por hoy (con el PIN). */
    fun grant(context: Context) {
        roll(context)
        val prefs = prefs(context)
        prefs.edit { putInt("kidsExtra", prefs.getInt("kidsExtra", 0) + EXTRA_SECONDS) }
    }

    /** "1 h 05 min", "25 min". */
    fun text(seconds: Int): String {
        val minutes = (seconds.coerceAtLeast(0) + 59) / 60
        return if (minutes >= 60) "${minutes / 60} h %02d min".format(minutes % 60) else "$minutes min"
    }
}

/**
 * "Pedir PIN al abrir" en la app de los mayores: cuando se sale de ella con Home (o se cierra), al
 * volver pide el PIN. Ir al reproductor y volver no cuenta como salir.
 */
object AppLock {
    @Volatile
    var locked = true
        private set

    /** Se ha salido de la app (Home, recientes): al volver, PIN. */
    fun leaving() {
        locked = true
    }

    fun unlock() {
        locked = false
    }
}

/**
 * Pedir el PIN (o crearlo, si aún no hay, o cambiarlo con [create]): lo que se escribe y los
 * mensajes. La pantalla (diálogo en el móvil, teclado en la tele) solo lo enseña.
 */
class PinFlow(private val store: Store, create: Boolean = !store.hasPin, private val onSuccess: () -> Unit) {
    private val creating = create
    private var first: String? = null

    var prompt by mutableStateOf(promptFor())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Las cifras escritas en el teclado de la tele. */
    var digits by mutableStateOf("")
        private set

    private fun promptFor() = when {
        !creating -> "Escribe el PIN de los mayores"
        first == null -> "Crea un PIN de 4 cifras para los mayores"
        else -> "Repite el PIN"
    }

    fun type(digit: Char) {
        if (digits.length >= Pin.LENGTH) return
        digits += digit
        error = null
        if (digits.length == Pin.LENGTH) enter(digits)
    }

    fun erase() {
        digits = digits.dropLast(1)
    }

    fun enter(pin: String) {
        digits = ""
        if (!creating) {
            if (store.checkPin(pin)) onSuccess() else error = "PIN incorrecto"
            return
        }
        val firstPin = first
        if (firstPin == null) {
            first = pin
            error = null
        } else if (firstPin == pin) {
            store.setPin(pin)
            onSuccess()
            return
        } else {
            first = null
            error = "No coinciden. Vuelve a empezar."
        }
        prompt = promptFor()
    }
}

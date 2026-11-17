package es.miscanales

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color

/**
 * Quién usa la app de los mayores: cada uno tiene su icono, con sus canales, vistos, "seguir viendo",
 * listas y avisos. Lo del aparato es común: la conexión con Home Assistant, el PIN y Mis Canales Kids
 * (que sale de los canales «Para niños» del perfil principal).
 *
 * En Home Assistant, cada perfil usa sus propias listas de tareas: el principal "Mis Canales",
 * "Mis Canales progreso" y "Mis Canales listas"; los demás, con su clave detrás de "Mis Canales"
 * (Canales M, clave "montse": "Mis Canales Montse", "Mis Canales Montse progreso" y "Mis Canales Montse listas").
 */
enum class Profile(
    /** Lo que distingue sus datos ("" en el principal). */
    val key: String,
    /** El nombre del icono. */
    val label: String,
    /** El título de arriba: [titleStart] en blanco y [titleEnd] en su color. */
    val titleStart: String,
    val titleEnd: String,
    /** Su color. */
    val accent: Color,
    /** El icono que lo abre. */
    val activity: Class<out Activity>,
) {
    Main("", "Mis Canales", "Mis ", "Canales", Color(0xFFFF0033), MainActivity::class.java),
    Montse("montse", "Canales M", "Canales ", "M", Color(0xFFA855F7), MontseActivity::class.java);

    /** Dónde se guardan sus datos (el principal, donde siempre: así no se pierde nada al actualizar). */
    val prefs: String get() = if (key.isEmpty()) PREFS else "${PREFS}_$key"

    /** La caché de sus vídeos. */
    val cacheFile: String get() = if (key.isEmpty()) "videos.json" else "videos_$key.json"

    /** La conexión con Home Assistant, con sus listas (la guardada es la del principal). */
    fun forProfile(config: HaConfig): HaConfig = if (key.isEmpty()) config else config.copy(entity = config.entity + "_" + key)

    /** El nombre de su lista de canales en Home Assistant, como se escribe al crearla. */
    // las listas de Home Assistant van por la clave del perfil, no por su nombre: "Mis Canales Montse"
    val haListName: String get() = if (key.isEmpty()) "Mis Canales" else "Mis Canales " + key.replaceFirstChar { it.uppercase() }

    companion object {
        fun of(key: String?): Profile = entries.firstOrNull { it.key == key.orEmpty() } ?: Main

        /** La conexión de Home Assistant como se guarda (común a todos): sin las listas de un perfil. */
        fun shared(config: HaConfig): HaConfig =
            entries.filter { it.key.isNotEmpty() }.fold(config) { c, p -> c.copy(entity = c.entity.removeSuffix("_" + p.key)) }
    }
}

/** Qué iconos de la app salen en este aparato (cada uno elige: en el móvil de cada uno, el suyo). */
object Launcher {
    /** Los iconos, en el orden en que se enseñan. */
    val icons: List<Pair<String, Class<out Activity>>> = Profile.entries.map { it.label to it.activity } +
        ("Mis Canales Kids" to KidsActivity::class.java)

    fun shown(context: Context, activity: Class<out Activity>): Boolean =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, activity)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun shown(context: Context, profile: Profile): Boolean = shown(context, profile.activity)

    fun set(context: Context, activity: Class<out Activity>, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, activity),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

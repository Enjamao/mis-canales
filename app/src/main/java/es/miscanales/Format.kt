package es.miscanales

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Con las mismas palabras que YouTube en España.

private val spanish: Locale = Locale.forLanguageTag("es-ES")

/** "12 mil visualizaciones • hace 3 días". */
fun Video.stats(): String = listOfNotNull(views?.let(::viewCount), ago(published)).joinToString(" • ")

private fun viewCount(n: Long): String = when {
    n >= 1_000_000 -> "${oneDecimal(n / 1e6)} M de visualizaciones"
    n >= 1_000 -> "${Math.round(n / 1e3)} mil visualizaciones"
    n == 1L -> "1 visualización"
    else -> "$n visualizaciones"
}

private fun oneDecimal(x: Double): String = String.format(spanish, "%.1f", x).removeSuffix(",0")

/** "hace 5 min", "hace 3 horas", "hace 2 semanas"… */
fun ago(millis: Long): String? {
    if (millis <= 0) return null
    val minutes = Duration.between(Instant.ofEpochMilli(millis), Instant.now()).toMinutes().coerceAtLeast(1)
    val day = 60L * 24
    return when {
        minutes < 60 -> "hace $minutes min"
        minutes < day -> plural(minutes / 60, "hora", "horas")
        minutes < day * 7 -> plural(minutes / day, "día", "días")
        minutes < day * 30 -> plural(minutes / (day * 7), "semana", "semanas")
        minutes < day * 365 -> plural(minutes / (day * 30), "mes", "meses")
        else -> plural(minutes / (day * 365), "año", "años")
    }
}

private fun plural(n: Long, one: String, many: String) = "hace $n ${if (n == 1L) one else many}"

private val longDate = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", spanish)

/** "10 de noviembre de 2026, 17:00". */
fun fullDate(millis: Long): String =
    if (millis <= 0) "" else longDate.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** 75 → "1:15"; 3725 → "1:02:05". */
fun clock(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

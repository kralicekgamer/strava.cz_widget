package cz.kralicekgamer.strava_cz_widget.data

import cz.kralicekgamer.strava_cz_widget.api.DayMenu
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object DayPicker {
    /** Od této hodiny už widget ukazuje další den. */
    private val SWITCH_TIME: LocalTime = LocalTime.of(15, 0)

    /** Dnešek do 15:00, jinak nejbližší další den, na který je vypsaný jídelníček. */
    fun pick(days: List<DayMenu>, now: LocalDateTime): DayMenu? {
        val today = now.toLocalDate()
        val from = if (now.toLocalTime() < SWITCH_TIME) today else today.plusDays(1)
        return days.firstOrNull { it.date >= from && it.meals.isNotEmpty() }
    }

    fun label(date: LocalDate, today: LocalDate): String {
        val day = "${WEEKDAYS.getValue(date.dayOfWeek)} ${date.dayOfMonth}. ${date.monthValue}."
        return when (date) {
            today -> "Dnes, $day"
            today.plusDays(1) -> "Zítra, $day"
            else -> day.replaceFirstChar { it.uppercase() }
        }
    }

    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to "pondělí",
        DayOfWeek.TUESDAY to "úterý",
        DayOfWeek.WEDNESDAY to "středa",
        DayOfWeek.THURSDAY to "čtvrtek",
        DayOfWeek.FRIDAY to "pátek",
        DayOfWeek.SATURDAY to "sobota",
        DayOfWeek.SUNDAY to "neděle",
    )
}

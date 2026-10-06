package cz.kralicekgamer.strava_cz_widget

import cz.kralicekgamer.strava_cz_widget.data.AppLog
import cz.kralicekgamer.strava_cz_widget.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AppLogTest {
    private val zone = ZoneId.of("Europe/Prague")
    private fun at(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `entries survive a round trip and line breaks cannot split a record`() {
        val entry = LogEntry(at(6, 12, 36), "Obnovení selhalo:\nIOException\tHTTP 500")

        val decoded = AppLog.decode(AppLog.encode(entry))

        assertEquals(LogEntry(entry.time, "Obnovení selhalo: IOException HTTP 500"), decoded)
        assertNull(AppLog.decode("poškozený řádek"))
    }

    @Test
    fun `only the newest entries are kept`() {
        val entries = (1..250).map { LogEntry(it.toLong(), "záznam $it") }

        val trimmed = AppLog.trim(entries)

        assertEquals(AppLog.MAX_ENTRIES, trimmed.size)
        assertEquals("záznam 250", trimmed.last().text)
        assertEquals("záznam 51", trimmed.first().text)
    }

    @Test
    fun `today shows only the time and older entries include the date`() {
        val today = LocalDate.of(2026, 10, 6)

        assertEquals("9:05", AppLog.formatTime(at(6, 9, 5), today, zone))
        assertEquals("5. 10. 18:30", AppLog.formatTime(at(5, 18, 30), today, zone))
    }
}

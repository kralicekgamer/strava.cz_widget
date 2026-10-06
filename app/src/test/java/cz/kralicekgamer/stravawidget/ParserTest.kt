package cz.kralicekgamer.stravawidget

import cz.kralicekgamer.stravawidget.api.MenuParser
import cz.kralicekgamer.stravawidget.api.StravaClient
import cz.kralicekgamer.stravawidget.api.StravaException
import cz.kralicekgamer.stravawidget.data.DayPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ParserTest {

    // Tvar odpovědi `objednavky` na verzi 5 (podle README knihovny strava_cz_api).
    private val v5 = """
        {
          "table1": [
            {"datum": "07.10.2026", "druh_popis": "Polévka", "nazev": "Gulášová", "polevka": "N", "pocet": 1, "veta": "180"},
            {"datum": "07.10.2026", "druh_popis": "Oběd 1", "nazev": "Svíčková, knedlík", "polevka": "N", "pocet": 1, "veta": "181"}
          ],
          "table0": [
            {"datum": "06.10.2026", "druh_popis": "Polévka", "nazev": "Kuřecí vývar", "polevka": "A", "pocet": 1, "veta": "172"},
            {"datum": "06.10.2026", "druh_popis": "Oběd 1", "nazev": "Řízek, brambor", "polevka": "N", "pocet": 0, "veta": "173"},
            {"datum": "06.10.2026", "druh_popis": "Oběd 2", "nazev": "Rizoto", "polevka": "N", "pocet": 1, "veta": "174"}
          ]
        }
    """.trimIndent()

    // Tvar veřejného jídelníčku na verzi 4 (demo jídelna 0000): pole v poli, bez `pocet`.
    private val v4 = """
        [{"table0": [
          {"datum": "06.10.2026", "chod": "C", "druh": "P", "druh_popis": "polévka", "nazev": "masový vývar", "polevka": "A", "version": 4},
          {"datum": "06.10.2026", "chod": "C", "druh": "4", "druh_popis": "oběd 1", "nazev": "pečené kuře, rýže", "polevka": "N", "version": 4}
        ]}]
    """.trimIndent()

    @Test
    fun `v5 days are sorted by date and split into soup and ordered meals`() {
        val days = MenuParser.parse(v5)

        assertEquals(listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)), days.map { it.date })
        assertEquals("Kuřecí vývar", days[0].soup?.name)
        assertEquals(listOf("Rizoto"), days[0].ordered.map { it.name })
        assertEquals("Oběd 2", days[0].ordered.single().label)
    }

    @Test
    fun `soup is recognised by its label when the polevka flag is missing`() {
        val day = MenuParser.parse(v5)[1]

        assertEquals("Gulášová", day.soup?.name)
        assertEquals(listOf("Svíčková, knedlík"), day.ordered.map { it.name })
    }

    @Test
    fun `v4 public menu parses without order counts`() {
        val day = MenuParser.parse(v4).single()

        assertEquals("masový vývar", day.soup?.name)
        assertTrue(day.ordered.isEmpty())
        assertEquals("Oběd 1", day.meals[1].label)
    }

    @Test
    fun `dish is taken from popis when nazev only repeats the course name`() {
        val json = """{"table0": [
            {"datum": "06.10.2026", "druh_popis": "Oběd", "nazev": "Oběd č. 1", "popis": "Guláš, knedlík", "pocet": 1}
        ]}"""

        assertEquals("Guláš, knedlík", MenuParser.parse(json).single().ordered.single().name)
    }

    @Test
    fun `widget shows everything ordered plus the soup in the order of the day`() {
        val json = """{"table0": [
            {"datum": "06.10.2026", "druh_popis": "Snídaně", "nazev": "Vejce", "pocet": 1},
            {"datum": "06.10.2026", "druh_popis": "Svačina", "nazev": "Chléb", "pocet": 0},
            {"datum": "06.10.2026", "druh_popis": "Polévka", "nazev": "Vývar", "polevka": "A", "pocet": 0},
            {"datum": "06.10.2026", "druh_popis": "Oběd 1", "nazev": "Řízek", "pocet": 0},
            {"datum": "06.10.2026", "druh_popis": "Oběd 2", "nazev": "Guláš", "pocet": 1},
            {"datum": "06.10.2026", "druh_popis": "Oběd 3", "nazev": "Rizoto", "pocet": 0},
            {"datum": "06.10.2026", "druh_popis": "Večeře", "nazev": "Těstoviny", "pocet": 1}
        ]}"""

        val day = MenuParser.parse(json).single()

        assertEquals(listOf("Snídaně", "Polévka", "Oběd 2", "Večeře"), day.shown.map { it.label })
        assertEquals(listOf("Vejce", "Guláš", "Těstoviny"), day.ordered.map { it.name })
    }

    @Test
    fun `today is shown until three in the afternoon`() {
        val days = MenuParser.parse(v5)

        assertEquals(LocalDate.of(2026, 10, 6), DayPicker.pick(days, LocalDateTime.of(2026, 10, 6, 14, 59))?.date)
        assertEquals(LocalDate.of(2026, 10, 7), DayPicker.pick(days, LocalDateTime.of(2026, 10, 6, 15, 0))?.date)
    }

    @Test
    fun `weekend skips to the next day with a menu and nothing is left after the last one`() {
        val days = MenuParser.parse(v5)

        assertEquals(LocalDate.of(2026, 10, 6), DayPicker.pick(days, LocalDateTime.of(2026, 10, 4, 10, 0))?.date)
        assertNull(DayPicker.pick(days, LocalDateTime.of(2026, 10, 7, 16, 0)))
    }

    @Test
    fun `day labels`() {
        val today = LocalDate.of(2026, 10, 6)

        assertEquals("Dnes, úterý 6. 10.", DayPicker.label(today, today))
        assertEquals("Zítra, středa 7. 10.", DayPicker.label(today.plusDays(1), today))
        assertEquals("Pondělí 12. 10.", DayPicker.label(LocalDate.of(2026, 10, 12), today))
    }

    @Test
    fun `api error response raises StravaException with its number`() {
        val error = assertThrows(StravaException::class.java) {
            StravaClient.raiseApiError("""{"state": "error", "number": 15, "message": "Chybný SID"}""")
        }

        assertEquals(15, error.number)
        StravaClient.raiseApiError("""{"table0": []}""")
    }

    @Test
    fun `cookies from the response replace and extend the old ones`() {
        val merged = StravaClient.mergeCookies("NEXT_LOCALE=cs; a=1", listOf("a=2; Path=/; HttpOnly", "sid=xyz; Secure"))

        assertEquals("NEXT_LOCALE=cs; a=2; sid=xyz", merged)
    }

    /** Běží jen když existuje soubor vytvořený skriptem tools/fetch_demo.sh. */
    @Test
    fun `real demo response parses into days with meals`() {
        val resource = javaClass.getResource("/objednavky_demo.json")
        assumeNotNull(resource)

        val days = MenuParser.parse(resource!!.readText())

        assertFalse(days.isEmpty())
        assertTrue(days.all { it.meals.isNotEmpty() })
    }
}

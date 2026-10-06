package cz.kralicekgamer.stravawidget.api

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Meal(
    /** Popisek chodu, např. „Oběd 1“ nebo „Polévka“. */
    val label: String,
    /** Co se vaří, např. „pečené kuře, rýže“. */
    val name: String,
    val isSoup: Boolean,
    val ordered: Boolean,
)

data class DayMenu(val date: LocalDate, val meals: List<Meal>) {
    val soup: Meal?
        get() = meals.filter { it.isSoup }.let { soups -> soups.firstOrNull { it.ordered } ?: soups.firstOrNull() }

    val ordered: List<Meal>
        get() = meals.filter { it.ordered && !it.isSoup }

    /** Co ukazuje widget: všechno objednané a polévka, v pořadí dne (snídaně, oběd, večeře). */
    val shown: List<Meal>
        get() = soup.let { soup -> meals.filter { it.ordered || it === soup } }
}

object MenuParser {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Převede odpověď `objednavky` (nebo veřejného `jidelnicky`) na dny seřazené podle data. */
    fun parse(json: String): List<DayMenu> {
        val tables = when (val root = JSONTokener(json).nextValue()) {
            is JSONObject -> listOf(root)
            is JSONArray -> (0 until root.length()).mapNotNull { root.optJSONObject(it) }
            else -> emptyList()
        }

        val byDate = sortedMapOf<LocalDate, MutableList<Meal>>()
        for (table in tables) {
            for (key in table.keys()) {
                if (!key.startsWith("table")) continue
                val items = table.optJSONArray(key) ?: continue
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val date = parseDate(item.string("datum")) ?: continue
                    val meal = parseMeal(item) ?: continue
                    byDate.getOrPut(date) { mutableListOf() }.add(meal)
                }
            }
        }
        return byDate.map { (date, meals) -> DayMenu(date, meals) }
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.trim(), DATE)
    } catch (e: Exception) {
        null
    }

    private fun parseMeal(item: JSONObject): Meal? {
        val label = item.string("druh_popis").ifBlank { item.string("druh_chod") }.trim()
        val nazev = item.string("nazev").trim()
        val popis = item.string("popis").trim()

        // Některé jídelny mají v `nazev` jen název chodu („Oběd č. 1“) a jídlo až v `popis`.
        val nazevIsLabel = nazev.isBlank() || (label.isNotBlank() && plain(nazev).startsWith(plain(label)))
        val name = if (nazevIsLabel && popis.isNotBlank() && plain(popis) != plain(label)) popis else nazev
        if (name.isBlank()) return null

        val isSoup = item.string("polevka") == "A" || plain(label).contains("polev")
        val ordered = item.optInt("pocet", 0) >= 1
        return Meal(label.replaceFirstChar { it.uppercase() }, name, isSoup, ordered)
    }

    private fun plain(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

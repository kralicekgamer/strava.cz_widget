package cz.kralicekgamer.strava_cz_widget.api

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Chyba, kterou vrátila strava.cz (`{"state":"error","number":…,"message":…}`). */
class StravaException(val number: Int?, message: String) : Exception(message) {
    companion object {
        const val JIDELNA_NENALEZENA = 6
        const val CHYBNE_SID = 15
        const val CHYBNY_UZIVATEL = 13404
        const val CHYBNE_HESLO = 13405
    }
}

data class Session(val sid: String, val s5url: String, val cookie: String)

/**
 * Kotlin port knihovny strava_cz_api (https://github.com/kralicekgamer/strava.cz_api).
 * Python verze posílá u některých volání GET s tělem, což HttpURLConnection neumí;
 * strava.cz ale stejné endpointy přijímá i jako POST.
 */
object StravaClient {
    private const val BASE = "https://app.strava.cz/api"
    private const val DEFAULT_COOKIE = "NEXT_LOCALE=cs"
    private const val LANG = "CZ"

    fun login(canteen: String, username: String, password: String): Session {
        val payload = JSONObject()
            .put("cislo", canteen)
            .put("jmeno", username)
            .put("heslo", password)
            .put("zustatPrihlasen", true)
            .put("environment", "W")
            .put("lang", LANG)

        val (body, cookie) = post("login", payload, DEFAULT_COOKIE)
        val data = JSONObject(body)
        val sid = data.string("sid")
        if (sid.isBlank()) throw StravaException(null, "Strava nevrátila SID.")
        return Session(sid, data.string("s5url"), cookie)
    }

    /** Vrátí syrový JSON objednávek (`table0`, `table1`, …) a případně obnovenou session. */
    fun orders(canteen: String, session: Session): Pair<String, Session> {
        val payload = JSONObject()
            .put("cislo", canteen)
            .put("sid", session.sid)
            .put("s5url", session.s5url)
            .put("lang", LANG)
            .put("konto", 0)
            .put("podminka", "")
            .put("ignoreCert", "false")

        val (body, cookie) = post("objednavky", payload, session.cookie)
        return body to session.copy(cookie = cookie)
    }

    private fun post(endpoint: String, payload: JSONObject, cookie: String): Pair<String, String> {
        val connection = URL("$BASE/$endpoint").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8")
            connection.setRequestProperty("Referer", "https://app.strava.cz/")
            connection.setRequestProperty("Cookie", cookie.ifBlank { DEFAULT_COOKIE })
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            raiseApiError(body)
            if (code >= 400) throw IOException("HTTP $code")

            val setCookies = connection.headerFields
                .filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
                .values.flatten()
            return body to mergeCookies(cookie, setCookies)
        } finally {
            connection.disconnect()
        }
    }

    internal fun raiseApiError(body: String) {
        if (!body.trimStart().startsWith("{")) return
        val data = try {
            JSONObject(body)
        } catch (e: Exception) {
            return
        }
        if (data.string("state") != "error") return
        val number = if (data.has("number")) data.optInt("number") else null
        throw StravaException(number, data.string("message").ifBlank { "Neznámá chyba" })
    }

    internal fun mergeCookies(old: String, setCookieHeaders: List<String>): String {
        val cookies = LinkedHashMap<String, String>()
        old.split(";").forEach { part ->
            val index = part.indexOf('=')
            if (index > 0) cookies[part.substring(0, index).trim()] = part.substring(index + 1).trim()
        }
        setCookieHeaders.forEach { header ->
            val pair = header.substringBefore(';')
            val index = pair.indexOf('=')
            if (index > 0) cookies[pair.substring(0, index).trim()] = pair.substring(index + 1).trim()
        }
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}

/** `optString` na Androidu vrací pro JSON null text "null". */
internal fun JSONObject.string(key: String): String = if (isNull(key)) "" else optString(key)

package cz.kralicekgamer.stravawidget.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LogEntry(val time: Long, val text: String)

/**
 * Záznam toho, co aplikace dělala (přihlášení, obnovení, chyby). Drží posledních [MAX_ENTRIES]
 * řádků v soukromém souboru. Nikdy sem nepatří heslo, SID ani cookie.
 */
object AppLog {
    const val MAX_ENTRIES = 200
    private const val FILE_NAME = "log.txt"

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    private var loaded = false

    /** Od nejstaršího po nejnovější. */
    val entries: StateFlow<List<LogEntry>> = _entries

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val file = file(context)
        _entries.value = if (file.exists()) file.readLines().mapNotNull(::decode) else emptyList()
        loaded = true
    }

    @Synchronized
    fun add(context: Context, text: String) {
        load(context)
        val updated = trim(_entries.value + LogEntry(System.currentTimeMillis(), text))
        _entries.value = updated
        file(context).writeText(updated.joinToString("") { encode(it) + "\n" })
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
        _entries.value = emptyList()
        loaded = true
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    internal fun trim(entries: List<LogEntry>, max: Int = MAX_ENTRIES): List<LogEntry> = entries.takeLast(max)

    internal fun encode(entry: LogEntry): String = "${entry.time}\t${entry.text.replace('\n', ' ').replace('\t', ' ')}"

    internal fun decode(line: String): LogEntry? {
        val index = line.indexOf('\t')
        if (index <= 0) return null
        val time = line.substring(0, index).toLongOrNull() ?: return null
        return LogEntry(time, line.substring(index + 1))
    }

    private val TIME = DateTimeFormatter.ofPattern("H:mm")
    private val DATE_TIME = DateTimeFormatter.ofPattern("d. M. H:mm")

    /** Dnešní záznamy jen časem, starší i s datem. */
    fun formatTime(time: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val moment = Instant.ofEpochMilli(time).atZone(zone)
        return moment.format(if (moment.toLocalDate() == today) TIME else DATE_TIME)
    }

    fun export(entries: List<LogEntry>, zone: ZoneId = ZoneId.systemDefault()): String {
        val full = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return entries.joinToString("\n") { "${Instant.ofEpochMilli(it.time).atZone(zone).format(full)}  ${it.text}" }
    }
}

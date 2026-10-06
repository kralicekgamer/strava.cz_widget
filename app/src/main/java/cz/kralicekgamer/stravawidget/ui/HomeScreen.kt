package cz.kralicekgamer.stravawidget.ui

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.kralicekgamer.stravawidget.BuildConfig
import cz.kralicekgamer.stravawidget.data.AppLog
import cz.kralicekgamer.stravawidget.data.CredentialStore
import cz.kralicekgamer.stravawidget.data.LogEntry
import cz.kralicekgamer.stravawidget.data.MenuRepository
import cz.kralicekgamer.stravawidget.widget.RefreshWorker
import cz.kralicekgamer.stravawidget.widget.StravaWidgetReceiver
import kotlinx.coroutines.launch
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SOURCE_URL = "https://github.com/kralicekgamer/strava_widget"

/** Delší odpověď by okno zpomalila; zkopírovat jde vždy celá. */
private const val RAW_PREVIEW_CHARS = 6000

@Composable
internal fun HomeScreen(store: CredentialStore, version: Int) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    fun refresh(source: String) {
        if (busy) return
        scope.launch {
            busy = true
            MenuRepository.refresh(context, source)
            busy = false
        }
    }

    // Přežije otočení displeje i přepnutí tmavého režimu, aby se nestahovalo zbytečně znovu.
    var refreshedOnOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        AppLog.load(context)
        RefreshWorker.schedule(context)
        if (!refreshedOnOpen) {
            refreshedOnOpen = true
            refresh("při otevření aplikace")
        }
    }

    Spacer(Modifier.height(24.dp))
    Text("Strava widget", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "${store.username}, jídelna ${store.canteen}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    WidgetSection(store, version, busy, onRefresh = { refresh("ručně") })
    DiagnosticsSection(store, version)
    AboutSection()

    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        onClick = { confirmLogout = true },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Odhlásit se")
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Odhlásit účet ${store.username}?",
            text = "Widget přestane ukazovat jídla, dokud se znovu nepřihlásíš. Číslo jídelny a jméno zůstanou předvyplněné.",
            confirm = "Odhlásit se",
            dismiss = "Zůstat přihlášen",
            onConfirm = {
                confirmLogout = false
                scope.launch { MenuRepository.logout(context) }
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun WidgetSection(store: CredentialStore, version: Int, busy: Boolean, onRefresh: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val updated = remember(version) { store.menuTime }
    val problem = remember(version) { store.lastError }
    val widgetManager = remember { AppWidgetManager.getInstance(context) }

    Section("Widget") {
        InfoRow(
            "Poslední obnovení",
            if (updated > 0) SimpleDateFormat("d. M. H:mm", Locale.ROOT).format(Date(updated)) else "Zatím neproběhlo",
        )
        if (problem != null && !busy) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                problem.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(16.dp)) {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                BusyLabel(busy, idle = "Obnovit jídelníček", working = "Načítám…")
            }
            Spacer(Modifier.height(8.dp))
            if (widgetManager.isRequestPinAppWidgetSupported) {
                OutlinedButton(
                    onClick = {
                        widgetManager.requestPinAppWidget(
                            ComponentName(context, StravaWidgetReceiver::class.java), null, null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Přidat widget na plochu")
                }
            } else {
                Text(
                    "Widget přidáš tak, že podržíš prst na ploše, zvolíš Widgety a vybereš Strava.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(store: CredentialStore, version: Int) {
    val context = LocalContext.current.applicationContext
    val entries by AppLog.entries.collectAsState()
    val rawJson = remember(version) { store.menuJson }
    var showLogs by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    Section("Diagnostika") {
        ActionRow("Otevřít logy", value = logCount(entries.size)) { showLogs = true }
        if (rawJson != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("Otevřít odpověď Stravy") { showRaw = true }
        }
    }

    if (showLogs) {
        TextWindow(
            title = "Logy",
            // Nejnovější nahoře.
            text = remember(entries) { AppLog.export(entries.asReversed()) },
            emptyText = "Zatím žádné záznamy.",
            onClose = { showLogs = false },
            onCopy = { copy(context, "Logy Strava widgetu", exportLog(entries.asReversed())) },
            clearQuestion = "Smazat ${logCount(entries.size)}? Nepůjdou obnovit. Widget ani přihlášení se nezmění.",
            clearLabel = "Smazat logy",
            onClear = { AppLog.clear(context) },
        )
    }

    if (showRaw && rawJson != null) {
        val pretty = remember(rawJson) { prettyJson(rawJson) }
        val preview = remember(pretty) { pretty.take(RAW_PREVIEW_CHARS) }
        TextWindow(
            title = "Odpověď Stravy",
            text = preview,
            note = if (pretty.length > preview.length) {
                "Vidíš prvních ${preview.length} z ${pretty.length} znaků. Zkopíruje se celá odpověď."
            } else {
                null
            },
            onClose = { showRaw = false },
            onCopy = { copy(context, "Odpověď Stravy", pretty) },
        )
    }
}

private fun logCount(count: Int): String = when (count) {
    1 -> "1 záznam"
    2, 3, 4 -> "$count záznamy"
    else -> "$count záznamů"
}

/**
 * Okno s obyčejným posuvným textem (logy, odpověď Stravy). Smazání se potvrzuje ve stejném okně,
 * aby se neotvíralo okno přes okno.
 */
@Composable
private fun TextWindow(
    title: String,
    text: String,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    emptyText: String = "",
    note: String? = null,
    clearQuestion: String = "",
    clearLabel: String = "",
    onClear: (() -> Unit)? = null,
) {
    var confirmingClear by remember { mutableStateOf(false) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = maxHeight),
        ) {
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                if (confirmingClear) {
                    Text(clearQuestion, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { confirmingClear = false }) { Text("Ponechat") }
                        TextButton(
                            onClick = {
                                confirmingClear = false
                                onClear?.invoke()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(clearLabel)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                emptyText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            SelectionContainer {
                                Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    if (note != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (onClear != null && text.isNotEmpty()) {
                            TextButton(
                                onClick = { confirmingClear = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Smazat")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (text.isNotEmpty()) TextButton(onClick = onCopy) { Text("Zkopírovat") }
                        TextButton(onClick = onClose) { Text("Zavřít") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current

    Section("O aplikaci") {
        InfoRow("Verze", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(
            "Sestaveno",
            SimpleDateFormat("d. M. yyyy H:mm", Locale.ROOT).format(Date(BuildConfig.BUILD_TIME)) +
                ", ${BuildConfig.BUILD_TYPE}",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow("Commit", BuildConfig.GIT_COMMIT)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow("Zařízení", "${Build.MANUFACTURER} ${Build.MODEL}")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("Otevřít zdrojový kód na GitHubu") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
        }
    }
}

/** Nadpis sekce s volitelnými akcemi vpravo a orámovaný panel s řádky. */
@Composable
private fun Section(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Spacer(Modifier.height(24.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
    }
}

/** Řádek, na který se dá klepnout; barva akcentu ho odliší od řádků s údaji. */
@Composable
private fun ActionRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

private fun exportLog(entries: List<LogEntry>): String =
    "Strava widget ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}, ${BuildConfig.GIT_COMMIT}), " +
        "Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}\n" +
        AppLog.export(entries)

private fun prettyJson(json: String): String = try {
    when (val root = JSONTokener(json).nextValue()) {
        is org.json.JSONObject -> root.toString(2)
        is org.json.JSONArray -> root.toString(2)
        else -> json
    }
} catch (e: Exception) {
    json
}

private fun copy(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

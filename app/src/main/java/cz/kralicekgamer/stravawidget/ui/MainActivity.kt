package cz.kralicekgamer.stravawidget.ui

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cz.kralicekgamer.stravawidget.data.CredentialStore
import cz.kralicekgamer.stravawidget.data.Failure
import cz.kralicekgamer.stravawidget.data.MenuRepository
import cz.kralicekgamer.stravawidget.data.WidgetContent
import cz.kralicekgamer.stravawidget.widget.RefreshWorker
import cz.kralicekgamer.stravawidget.widget.StravaWidgetReceiver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StravaTheme { App() }
        }
    }
}

// Barvy pro Android 11 a starší; novější systém použije barvy z tapety stejně jako widget.
private val LightColors = lightColorScheme(primary = Color(0xFF1F6F5C), secondary = Color(0xFF4C635B))
private val DarkColors = darkColorScheme(primary = Color(0xFF8AD6BE), secondary = Color(0xFFB3CCC2))

@Composable
private fun StravaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun App() {
    val context = LocalContext.current.applicationContext
    val store = remember { CredentialStore(context) }
    val version by MenuRepository.version.collectAsState()
    val loggedIn = remember(version) { store.isLoggedIn }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            ) {
                if (loggedIn) Account(store, version) else LoginForm(store)
            }
        }
    }
}

@Composable
private fun LoginForm(store: CredentialStore) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // Číslo jídelny a jméno se pamatují z minula; heslo se nepředvyplňuje.
    var canteen by rememberSaveable { mutableStateOf(store.canteen) }
    var username by rememberSaveable { mutableStateOf(store.username) }
    var password by rememberSaveable { mutableStateOf("") }
    var canteenError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Failure?>(null) }

    val canteenFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    fun canteenProblem() = if (canteen.isBlank()) "Zadej číslo jídelny, např. 0000." else null
    fun usernameProblem() = if (username.isBlank()) "Zadej uživatelské jméno ze Stravy." else null
    fun passwordProblem() = if (password.isEmpty()) "Zadej heslo ke Stravě." else null

    fun submit() {
        if (busy) return
        canteenError = canteenProblem()
        usernameError = usernameProblem()
        passwordError = passwordProblem()
        when {
            canteenError != null -> canteenFocus.requestFocus()
            usernameError != null -> usernameFocus.requestFocus()
            passwordError != null -> passwordFocus.requestFocus()
            else -> scope.launch {
                busy = true
                failure = MenuRepository.login(context, canteen.trim(), username.trim(), password)
                if (failure == null) RefreshWorker.schedule(context)
                busy = false
            }
        }
    }

    Text("Přihlášení ke Stravě", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Widget pak na ploše ukáže polévku a jídlo, které máš objednané.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    Field(
        label = "Číslo jídelny",
        value = canteen,
        onValueChange = { canteen = it; canteenError = null },
        error = canteenError,
        onBlur = { canteenError = canteenProblem() },
        focusRequester = canteenFocus,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(16.dp))
    Field(
        label = "Uživatelské jméno",
        value = username,
        onValueChange = { username = it; usernameError = null },
        error = usernameError,
        onBlur = { usernameError = usernameProblem() },
        focusRequester = usernameFocus,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(16.dp))
    Field(
        label = "Heslo",
        value = password,
        onValueChange = { password = it; passwordError = null },
        error = passwordError,
        helper = "Uloží se šifrovaně jen v tomto telefonu.",
        onBlur = { passwordError = passwordProblem() },
        focusRequester = passwordFocus,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailing = {
            TextButton(onClick = { showPassword = !showPassword }) {
                Text(if (showPassword) "Skrýt" else "Zobrazit")
            }
        },
    )

    failure?.let {
        Spacer(Modifier.height(16.dp))
        ErrorNotice(it)
    }

    Spacer(Modifier.height(24.dp))
    Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) {
        BusyLabel(busy, idle = "Přihlásit se", working = "Přihlašuji…")
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    onBlur: () -> Unit,
    focusRequester: FocusRequester,
    keyboardOptions: KeyboardOptions,
    helper: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    // Chyba se poprvé ukáže až po opuštění pole, ne během psaní.
    var wasFocused by remember { mutableStateOf(false) }
    val supporting = error ?: helper

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailing,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) wasFocused = true else if (wasFocused) onBlur()
            },
    )
}

@Composable
private fun Account(store: CredentialStore, version: Int) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val content = remember(version) { MenuRepository.current(context) }
    val updated = remember(version) { store.menuTime }

    fun refresh() {
        if (busy) return
        scope.launch {
            busy = true
            MenuRepository.refresh(context)
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        RefreshWorker.schedule(context)
        refresh()
    }

    Text("Jídelníček", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Přihlášen jako ${store.username}, jídelna ${store.canteen}.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { Preview(content) }
    }

    if (updated > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Aktualizováno ${SimpleDateFormat("d. M. H:mm", Locale.ROOT).format(Date(updated))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val problem = (content as? WidgetContent.Day)?.problem ?: (content as? WidgetContent.Error)?.failure
    if (problem != null && !busy) {
        Spacer(Modifier.height(16.dp))
        ErrorNotice(problem)
    }

    Spacer(Modifier.height(24.dp))
    Button(onClick = ::refresh, modifier = Modifier.fillMaxWidth()) {
        BusyLabel(busy, idle = "Obnovit jídelníček", working = "Načítám…")
    }
    Spacer(Modifier.height(8.dp))

    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    if (widgetManager.isRequestPinAppWidgetSupported) {
        OutlinedButton(
            onClick = {
                widgetManager.requestPinAppWidget(ComponentName(context, StravaWidgetReceiver::class.java), null, null)
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

    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = { scope.launch { MenuRepository.logout(context) } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Odhlásit se")
    }
}

/** Stejný obsah, jaký ukazuje widget. */
@Composable
private fun Preview(content: WidgetContent) {
    when (content) {
        is WidgetContent.Day -> {
            Text(content.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content.meals.forEach { PreviewRow(it.label, it.name, emphasized = !it.isSoup) }
                if (content.nothingOrdered) PreviewRow("Jídlo", "Nic objednáno", emphasized = false)
            }
        }
        WidgetContent.Loading -> PreviewMessage("Načítám jídelníček", "Chvilku to potrvá.")
        WidgetContent.NoMenu -> PreviewMessage(
            "Žádný jídelníček",
            "Na nejbližší dny jídelna nic nevypsala. Zkus jídelníček obnovit později.",
        )
        is WidgetContent.Error -> PreviewMessage(
            "Jídelníček se nenačetl",
            "Zatím není co ukázat. Zkus jídelníček obnovit.",
        )
        WidgetContent.LoggedOut -> Unit
    }
}

@Composable
private fun PreviewRow(label: String, name: String, emphasized: Boolean) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(72.dp).alignByBaseline(),
        )
        Text(
            name,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
    }
}

@Composable
private fun PreviewMessage(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorNotice(failure: Failure) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)) {
            Text(failure.message, style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Strava widget", failure.details))
                },
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Text("Zkopírovat podrobnosti")
            }
        }
    }
}

@Composable
private fun BusyLabel(busy: Boolean, idle: String, working: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
        Spacer(Modifier.width(8.dp))
        Text(working)
    } else {
        Text(idle)
    }
}

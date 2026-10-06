package cz.kralicekgamer.stravawidget.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cz.kralicekgamer.stravawidget.data.CredentialStore
import cz.kralicekgamer.stravawidget.data.Failure
import cz.kralicekgamer.stravawidget.data.MenuRepository
import cz.kralicekgamer.stravawidget.widget.RefreshWorker
import kotlinx.coroutines.launch

@Composable
internal fun LoginScreen(store: CredentialStore) {
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

    Spacer(Modifier.height(24.dp))
    Text("Přihlášení ke Stravě", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Widget pak na ploše ukáže, co máš na dnešek objednané.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Field(
                label = "Číslo jídelny",
                value = canteen,
                onValueChange = { canteen = it; canteenError = null },
                error = canteenError,
                onBlur = { canteenError = canteenProblem() },
                focusRequester = canteenFocus,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            Field(
                label = "Uživatelské jméno",
                value = username,
                onValueChange = { username = it; usernameError = null },
                error = usernameError,
                onBlur = { usernameError = usernameProblem() },
                focusRequester = usernameFocus,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(12.dp))
                Text(
                    it.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) {
                BusyLabel(busy, idle = "Přihlásit se", working = "Přihlašuji…")
            }
        }
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

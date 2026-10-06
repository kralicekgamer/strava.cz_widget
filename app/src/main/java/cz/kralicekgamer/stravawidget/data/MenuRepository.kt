package cz.kralicekgamer.stravawidget.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import cz.kralicekgamer.stravawidget.api.Meal
import cz.kralicekgamer.stravawidget.api.MenuParser
import cz.kralicekgamer.stravawidget.api.Session
import cz.kralicekgamer.stravawidget.api.StravaClient
import cz.kralicekgamer.stravawidget.api.StravaException
import cz.kralicekgamer.stravawidget.widget.StravaWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime

/** Chybová hláška pro uživatele a technické podrobnosti ke zkopírování. */
data class Failure(val message: String, val details: String)

/** Co má widget (a náhled v aplikaci) právě ukázat. */
sealed interface WidgetContent {
    data object LoggedOut : WidgetContent
    data object Loading : WidgetContent
    /**
     * [meals] jsou objednaná jídla a polévka v pořadí dne; [nothingOrdered] platí, když v nich
     * kromě polévky nic není. [problem] je chyba posledního obnovení; data jsou pak z dřívějška.
     */
    data class Day(
        val label: String,
        val meals: List<Meal>,
        val nothingOrdered: Boolean,
        val problem: Failure?,
    ) : WidgetContent
    data object NoMenu : WidgetContent
    data class Error(val failure: Failure) : WidgetContent
}

object MenuRepository {
    private val _version = MutableStateFlow(0)

    /** Zvýší se při každé změně uložených dat; widget i aplikace se podle toho překreslí. */
    val version: StateFlow<Int> = _version

    /** Přihlásí uživatele, uloží údaje a stáhne jídelníček. Vrací chybu, nebo null při úspěchu. */
    suspend fun login(context: Context, canteen: String, username: String, password: String): Failure? {
        val store = CredentialStore(context)
        val failure = withContext(Dispatchers.IO) {
            try {
                val session = StravaClient.login(canteen, username, password)
                store.canteen = canteen
                store.username = username
                store.password = password
                store.session = session
                fetch(store, session)
                null
            } catch (e: Exception) {
                describe(e)
            }
        }
        if (store.isLoggedIn) {
            failure?.let(store::saveError)
            notifyChanged(context)
        }
        // Chyba jen při stahování jídelníčku po úspěšném přihlášení se ukáže v náhledu.
        return failure.takeUnless { store.isLoggedIn }
    }

    /** Stáhne jídelníček; když session vypršela, jednou se znovu přihlásí. */
    suspend fun refresh(context: Context): Failure? {
        val store = CredentialStore(context)
        if (!store.isLoggedIn) return null

        val failure = withContext(Dispatchers.IO) {
            try {
                val session = store.session
                try {
                    fetch(store, session ?: relogin(store))
                } catch (e: StravaException) {
                    if (session == null) throw e
                    fetch(store, relogin(store))
                }
                null
            } catch (e: Exception) {
                describe(e)
            }
        }
        failure?.let(store::saveError)
        notifyChanged(context)
        return failure
    }

    suspend fun logout(context: Context) {
        CredentialStore(context).logout()
        notifyChanged(context)
    }

    /** Překreslí widget bez stahování, např. když se po 15:00 přepíná na další den. */
    suspend fun notifyChanged(context: Context) {
        _version.update { it + 1 }
        StravaWidget().updateAll(context)
    }

    fun current(context: Context, now: LocalDateTime = LocalDateTime.now()): WidgetContent {
        val store = CredentialStore(context)
        if (!store.isLoggedIn) return WidgetContent.LoggedOut

        val json = store.menuJson
        if (json == null) {
            return store.lastError?.let { WidgetContent.Error(it) } ?: WidgetContent.Loading
        }

        val days = try {
            MenuParser.parse(json)
        } catch (e: Exception) {
            return WidgetContent.Error(describe(e))
        }
        val day = DayPicker.pick(days, now) ?: return WidgetContent.NoMenu
        return WidgetContent.Day(
            label = DayPicker.label(day.date, now.toLocalDate()),
            meals = day.shown,
            nothingOrdered = day.ordered.isEmpty(),
            problem = store.lastError,
        )
    }

    private fun relogin(store: CredentialStore): Session {
        val password = store.password ?: throw StravaException(StravaException.CHYBNE_HESLO, "Uložené heslo nejde přečíst.")
        return StravaClient.login(store.canteen, store.username, password).also { store.session = it }
    }

    private fun fetch(store: CredentialStore, session: Session) {
        val (json, updated) = StravaClient.orders(store.canteen, session)
        store.session = updated
        store.saveMenu(json)
    }

    private fun describe(e: Exception): Failure {
        val message = when {
            e is StravaException && e.number == StravaException.JIDELNA_NENALEZENA ->
                "Jídelna s tímto číslem neexistuje. Zkontroluj číslo jídelny a zkus to znovu."
            e is StravaException && (e.number == StravaException.CHYBNE_HESLO || e.number == StravaException.CHYBNY_UZIVATEL) ->
                "Jméno nebo heslo nesedí. Zkontroluj je a přihlas se znovu."
            e is StravaException ->
                "Strava teď jídelníček nevydala. Zkus to za chvíli znovu."
            e is IOException ->
                "Nepodařilo se spojit se Stravou. Zkontroluj připojení a zkus to znovu."
            else ->
                "Jídelníček se nepodařilo načíst. Zkus to znovu."
        }
        val number = (e as? StravaException)?.number?.let { " #$it" }.orEmpty()
        return Failure(message, "${e.javaClass.simpleName}$number: ${e.message}")
    }
}

package cz.kralicekgamer.stravawidget.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cz.kralicekgamer.stravawidget.api.Session
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Přihlašovací údaje, session a poslední stažený jídelníček. Heslo je šifrované klíčem z Android Keystore. */
class CredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("strava", Context.MODE_PRIVATE)

    var canteen: String
        get() = prefs.getString(KEY_CANTEEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CANTEEN, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)?.let(::decrypt)
        set(value) = prefs.edit().putString(KEY_PASSWORD, value?.let(::encrypt)).apply()

    val isLoggedIn: Boolean
        get() = prefs.contains(KEY_PASSWORD)

    var session: Session?
        get() {
            val sid = prefs.getString(KEY_SID, null) ?: return null
            return Session(sid, prefs.getString(KEY_S5URL, "").orEmpty(), prefs.getString(KEY_COOKIE, "").orEmpty())
        }
        set(value) = prefs.edit()
            .putString(KEY_SID, value?.sid)
            .putString(KEY_S5URL, value?.s5url)
            .putString(KEY_COOKIE, value?.cookie)
            .apply()

    val menuJson: String?
        get() = prefs.getString(KEY_MENU, null)

    val menuTime: Long
        get() = prefs.getLong(KEY_MENU_TIME, 0)

    fun saveMenu(json: String) = prefs.edit()
        .putString(KEY_MENU, json)
        .putLong(KEY_MENU_TIME, System.currentTimeMillis())
        .remove(KEY_ERROR)
        .remove(KEY_ERROR_DETAILS)
        .apply()

    val lastError: Failure?
        get() {
            val message = prefs.getString(KEY_ERROR, null) ?: return null
            return Failure(message, prefs.getString(KEY_ERROR_DETAILS, "").orEmpty())
        }

    fun saveError(failure: Failure) = prefs.edit()
        .putString(KEY_ERROR, failure.message)
        .putString(KEY_ERROR_DETAILS, failure.details)
        .apply()

    /** Číslo jídelny a jméno zůstávají, aby je nebylo potřeba psát znovu. */
    fun logout() = prefs.edit()
        .remove(KEY_PASSWORD)
        .remove(KEY_SID).remove(KEY_S5URL).remove(KEY_COOKIE)
        .remove(KEY_MENU).remove(KEY_MENU_TIME)
        .remove(KEY_ERROR).remove(KEY_ERROR_DETAILS)
        .apply()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val (iv, encrypted) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "strava_password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        const val KEY_CANTEEN = "canteen"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_SID = "sid"
        const val KEY_S5URL = "s5url"
        const val KEY_COOKIE = "cookie"
        const val KEY_MENU = "menu_json"
        const val KEY_MENU_TIME = "menu_time"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_DETAILS = "error_details"
    }
}

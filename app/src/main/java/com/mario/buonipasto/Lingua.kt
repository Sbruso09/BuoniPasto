package com.mario.buonipasto

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Lingua dell'app: Automatica (quella del telefono), Italiano, English. Il testo di base e' italiano. */
object Lingua {
    val CODICI = listOf("", "it", "en")

    fun scelta(): String {
        val l = AppCompatDelegate.getApplicationLocales()
        return if (l.isEmpty) "" else l[0]?.language.orEmpty()
    }

    fun imposta(codice: String) {
        AppCompatDelegate.setApplicationLocales(
            if (codice.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(codice),
        )
    }

    /** true se i testi sono in inglese (scelto, o telefono in inglese in modalita' automatica). */
    fun inglese(): Boolean {
        val l = AppCompatDelegate.getApplicationLocales()
        val loc = if (l.isEmpty) Locale.getDefault() else l[0] ?: Locale.getDefault()
        return loc.language == "en"
    }

    fun locale(): Locale = if (inglese()) Locale.UK else Locale.ITALY

    /** Contesto con la lingua scelta, per notifiche e lavoro in background. */
    fun contesto(c: Context): Context {
        val conf = Configuration(c.resources.configuration)
        conf.setLocale(if (inglese()) Locale.ENGLISH else Locale.ITALIAN)
        return c.createConfigurationContext(conf)
    }

    fun nome(c: Context, codice: String): String = when (codice) {
        "it" -> "Italiano"
        "en" -> "English"
        else -> c.getString(R.string.lingua_automatica)
    }
}

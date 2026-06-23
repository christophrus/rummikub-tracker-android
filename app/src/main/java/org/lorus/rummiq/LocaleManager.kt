package org.lorus.rummiq

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleManager {

    val supportedLocales = mapOf(
        "en" to Locale.ENGLISH,
        "de" to Locale.GERMAN,
        "fr" to Locale.FRENCH
    )

    @Volatile
    var currentLanguageCode: String = "en"
        private set

    fun setLanguage(code: String) {
        currentLanguageCode = code
    }

    fun wrapContext(context: Context): Context {
        val locale = supportedLocales[currentLanguageCode] ?: Locale.ENGLISH
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

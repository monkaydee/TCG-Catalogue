package com.monkaydee.tcgcatalogue.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Text outside the screens (view models, the repository, background work) in the app's
 * language. The app language can differ from the phone's: it is chosen in Settings, or follows
 * the phone when set to "System".
 */
object AppStrings {
    private lateinit var app: Context
    private var cached: Pair<String, Context>? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** A context whose resources are in the app's language. */
    fun context(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return app
        val tags = locales.toLanguageTags()
        cached?.takeIf { it.first == tags }?.let { return it.second }
        val config = Configuration(app.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tags)) }
        return app.createConfigurationContext(config).also { cached = tags to it }
    }

    fun get(@StringRes id: Int, vararg args: Any): String = context().getString(id, *args)
}

/** The languages the app is translated into, as BCP 47 tags with their own names. */
object AppLanguages {
    val all = listOf(
        "en" to "English",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "pt-BR" to "Português (Brasil)",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "sv" to "Svenska",
        "tr" to "Türkçe",
        "ru" to "Русский",
        "uk" to "Українська",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "th" to "ไทย",
        "vi" to "Tiếng Việt",
        "id" to "Bahasa Indonesia",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
    )

    /** The language chosen in the app, or null when it follows the phone. */
    fun current(): String? = AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }?.toLanguageTags()

    /** Switches the app's language ([tag] null = follow the phone); the screens restart in it. */
    fun set(tag: String?) {
        AppCompatDelegate.setApplicationLocales(if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
    }
}

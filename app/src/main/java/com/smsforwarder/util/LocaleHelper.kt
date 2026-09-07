package com.smsforwarder.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

object LocaleHelper {

    fun getLocale(langCode: String): Locale {
        return when (langCode) {
            "fa" -> Locale("fa")
            "en" -> Locale("en")
            else -> Locale.getDefault()
        }
    }

    fun getLayoutDirection(langCode: String): LayoutDirection {
        val locale = getLocale(langCode)
        return if (locale.language == "fa" || locale.language == "ar") {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }

    fun applyLocale(context: Context, langCode: String): Context {
        val locale = getLocale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(config)
    }
}

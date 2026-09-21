package app.daybricks.planner.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import java.text.Collator

object AppLanguage {
    val supportedTags = listOf(
        "ar", "bn", "de", "en", "es", "fa", "fil", "fr", "hi", "id", "it", "he", "ja", "ko",
        "ms", "pl", "pt", "pt-BR", "ru", "sw", "th", "tr", "uk", "ur", "vi", "zh-Hans", "zh-Hant",
    )

    private const val PREFERENCES = "app_language"
    private const val LANGUAGE_TAG = "language_tag"
    private const val MIGRATED_TO_FRAMEWORK = "migrated_to_framework"
    private const val FIRST_RUN_INITIALIZED = "first_run_initialized"
    private const val LANGUAGE_CHOSEN = "language_chosen"
    private const val ONBOARDING_COMPLETE = "onboarding_complete"

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val tag = preferences(context).getString(LANGUAGE_TAG, null) ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return context.createConfigurationContext(configuration)
    }

    fun initialize(context: Context) {
        val preferences = preferences(context)
        if (!preferences.getBoolean(FIRST_RUN_INITIALIZED, false)) {
            preferences.edit()
                .putBoolean(FIRST_RUN_INITIALIZED, true)
                .putBoolean(LANGUAGE_CHOSEN, false)
                .putBoolean(ONBOARDING_COMPLETE, false)
                .apply()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (preferences.getBoolean(MIGRATED_TO_FRAMEWORK, false)) return
        val savedTag = preferences.getString(LANGUAGE_TAG, null)
        val localeManager = context.getSystemService(LocaleManager::class.java)
        if (localeManager.applicationLocales.isEmpty && savedTag != null) {
            localeManager.applicationLocales = LocaleList.forLanguageTags(savedTag)
        }
        preferences.edit().remove(LANGUAGE_TAG).putBoolean(MIGRATED_TO_FRAMEWORK, true).apply()
    }

    fun currentTag(context: Context): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales
            .takeUnless(LocaleList::isEmpty)?.get(0)?.toLanguageTag()?.let(::normalizeTag)
    } else {
        preferences(context).getString(LANGUAGE_TAG, null)?.let(::normalizeTag)
    }

    fun set(activity: Activity, tag: String?) {
        require(tag == null || tag in supportedTags)
        preferences(activity).edit().putBoolean(LANGUAGE_CHOSEN, true).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(tag.orEmpty())
        } else {
            preferences(activity).edit().apply {
                if (tag == null) remove(LANGUAGE_TAG) else putString(LANGUAGE_TAG, tag)
            }.apply()
            activity.recreate()
        }
    }

    fun isLanguageChosen(context: Context): Boolean = preferences(context).getBoolean(LANGUAGE_CHOSEN, false)

    fun isOnboardingComplete(context: Context): Boolean = preferences(context).getBoolean(ONBOARDING_COMPLETE, false)

    fun completeOnboarding(context: Context) {
        preferences(context).edit().putBoolean(ONBOARDING_COMPLETE, true).apply()
    }

    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { first ->
            if (first.isLowerCase()) first.titlecase(locale) else first.toString()
        }
    }

    fun sortedTags(displayLocale: Locale): List<String> {
        val collator = Collator.getInstance(displayLocale)
        return supportedTags.sortedWith { first, second ->
            collator.compare(displayName(first), displayName(second))
        }
    }

    private fun normalizeTag(tag: String): String = when (tag.lowercase(Locale.ROOT)) {
        "iw", "he" -> "he"
        "in", "id" -> "id"
        "zh-cn", "zh-sg", "zh-hans" -> "zh-Hans"
        "zh-tw", "zh-hk", "zh-mo", "zh-hant" -> "zh-Hant"
        else -> supportedTags.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

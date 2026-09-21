package app.daybricks.planner.domain

object StarterTemplates {
    // English is the first authored starter set. The language argument keeps the
    // installation path ready for localized starter sets without changing IDs.
    fun forLanguage(languageTag: String): List<BlockTemplate> {
        val translationTag = when (languageTag.lowercase()) {
            "zh-hans", "zh-cn", "zh-sg" -> "zh-CN"
            "zh-hant", "zh-tw", "zh-hk", "zh-mo" -> "zh-TW"
            else -> languageTag
        }
        val text = if (translationTag == "pt") portugueseStarterTranslation else
            starterTranslations[translationTag] ?: starterTranslations[translationTag.substringBefore('-')] ?: return english
        require(text.size == 14)
        return listOf(
            english[0].copy(title = text[0], description = text[1]),
            english[1].copy(title = text[2], description = text[3], presets = listOf(
                english[1].presets[0].copy(title = text[4], description = text[5]),
                english[1].presets[1].copy(title = text[6], description = text[7]),
                english[1].presets[2].copy(title = text[8], description = text[9]),
            )),
            english[2].copy(title = text[10], description = text[11]),
            english[3].copy(title = text[12], description = text[13]),
        )
    }

    private val english = listOf(
        BlockTemplate(
            id = "00000000-0000-4000-8000-000000000001",
            title = "Plan tomorrow",
            icon = "🗓️",
            description = "Open tomorrow's calendar and write down the three things that matter most. Start with the first one.",
            defaultDurationMinutes = 15,
            sortOrder = 100,
        ),
        BlockTemplate(
            id = "00000000-0000-4000-8000-000000000002",
            title = "Move a little",
            icon = "🌿",
            description = "Choose the kind of movement that feels possible today. The smallest option still counts.",
            defaultDurationMinutes = 10,
            presets = listOf(
                BlockPreset(
                    id = "00000000-0000-4000-8000-000000000201",
                    title = "Stretch and reset",
                    durationMinutes = 10,
                    icon = "🤸",
                    description = "Stand up, loosen your shoulders, and begin with one gentle stretch.",
                ),
                BlockPreset(
                    id = "00000000-0000-4000-8000-000000000202",
                    title = "Take a walk",
                    durationMinutes = 20,
                    icon = "🚶",
                    description = "Put on comfortable shoes and walk to the nearest easy landmark.",
                ),
                BlockPreset(
                    id = "00000000-0000-4000-8000-000000000203",
                    title = "Longer movement",
                    durationMinutes = 35,
                    icon = "🏃",
                    description = "Pick a familiar activity and spend the first five minutes warming up gently.",
                ),
            ),
            sortOrder = 200,
        ),
        BlockTemplate(
            id = "00000000-0000-4000-8000-000000000003",
            title = "Learn one thing",
            icon = "📚",
            description = "Open the book, lesson, or notes you already have and begin with one page or one question.",
            defaultDurationMinutes = 25,
            sortOrder = 300,
        ),
        BlockTemplate(
            id = "00000000-0000-4000-8000-000000000004",
            title = "Reset one space",
            icon = "✨",
            description = "Choose one small surface or area. Put away the first five things you can see.",
            defaultDurationMinutes = 15,
            sortOrder = 400,
        ),
    )
}

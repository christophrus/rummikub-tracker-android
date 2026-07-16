package org.lorus.rummiq.domain.model

object Config {
    val TIMER_PRESETS = listOf(
        30_000 to "timer_30s",
        45_000 to "timer_45s",
        60_000 to "timer_1m",
        90_000 to "timer_1_5m",
        120_000 to "timer_2m",
        180_000 to "timer_3m",
        300_000 to "timer_5m"
    )

    val REPLENISH_ROUNDS_OPTIONS = listOf(3, 4, 5, 6)

    val EXTENSION_DURATION_MS = 30_000L

    val TTS_LANGUAGES = listOf(
        "en", "de", "fr", "es", "it", "nl", "pl", "ru", "tr", "cs"
    )

    val UI_LANGUAGES = listOf("en", "de", "fr")

    const val MAX_IMAGE_SIZE = 200
    const val JPEG_QUALITY = 80

    const val TICK_START_SECONDS = 10
    const val CLOCK_COLOR_YELLOW_SECONDS = 15
    const val CLOCK_COLOR_RED_SECONDS = 10

    const val MAX_EXTENSIONS_LIMIT = 10
}

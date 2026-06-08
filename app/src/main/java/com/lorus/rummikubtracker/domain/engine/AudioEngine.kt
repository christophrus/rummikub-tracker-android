package com.lorus.rummikubtracker.domain.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var soundPool: SoundPool? = null
    private var tts: TextToSpeech? = null

    private var tickSoundId: Int = 0
    private var turnSoundId: Int = 0
    private var extendSoundId: Int = 0
    private var victorySoundId: Int = 0

    private var currentTtsLanguage: Locale = Locale.ENGLISH

    fun initialize() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load sounds from raw resources (will be created as placeholder WAVs)
        try {
            tickSoundId = soundPool?.load(context, android.R.raw, 1) ?: 0
            turnSoundId = soundPool?.load(context, android.R.raw, 1) ?: 0
            extendSoundId = soundPool?.load(context, android.R.raw, 1) ?: 0
            victorySoundId = soundPool?.load(context, android.R.raw, 1) ?: 0
        } catch (_: Exception) {
            // Sounds will be bundled later
        }

        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = currentTtsLanguage
            }
        }
    }

    fun setTtsLanguage(languageCode: String) {
        val locale = when (languageCode) {
            "de" -> Locale.GERMAN
            "fr" -> Locale.FRENCH
            "es" -> Locale("es")
            "it" -> Locale.ITALIAN
            "nl" -> Locale("nl")
            "pl" -> Locale("pl")
            "ru" -> Locale("ru")
            "tr" -> Locale("tr")
            "cs" -> Locale("cs")
            else -> Locale.ENGLISH
        }
        currentTtsLanguage = locale
        tts?.language = locale
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rummikub_tts_${System.currentTimeMillis()}")
    }

    fun playTick() {
        soundPool?.play(tickSoundId, 0.5f, 0.5f, 1, 0, 1.0f)
    }

    fun playTurnNotification() {
        soundPool?.play(turnSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun playExtend() {
        soundPool?.play(extendSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
    }

    fun playVictory() {
        soundPool?.play(victorySoundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun announcePlayer(playerName: String) {
        speak(playerName)
    }

    fun destroy() {
        soundPool?.release()
        soundPool = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

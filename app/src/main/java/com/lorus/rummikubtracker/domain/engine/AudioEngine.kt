package com.lorus.rummikubtracker.domain.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null

    private var currentTtsLanguage: Locale = Locale.ENGLISH

    fun initialize() {
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
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
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    }

    fun playTurnNotification() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    fun playExtend() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 100)
    }

    fun playVictory() {
        // Four-note ascending "Ta-Taa-Taa-Daaa" fanfare
        val tg = toneGenerator ?: return
        val handler = Handler(Looper.getMainLooper())
        tg.startTone(ToneGenerator.TONE_DTMF_1, 120)       // "Ta"
        handler.postDelayed({
            tg.startTone(ToneGenerator.TONE_DTMF_5, 120)   // "Taa"
        }, 150)
        handler.postDelayed({
            tg.startTone(ToneGenerator.TONE_DTMF_9, 120)   // "Taa"
        }, 300)
        handler.postDelayed({
            tg.startTone(ToneGenerator.TONE_DTMF_S, 400)   // "Daaa!"
        }, 450)
    }

    fun announcePlayer(playerName: String) {
        speak(playerName)
    }

    fun destroy() {
        toneGenerator?.release()
        toneGenerator = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

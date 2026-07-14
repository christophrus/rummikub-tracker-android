package org.lorus.rummiq.domain.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null

    private var currentTtsLanguage: Locale = Locale.ENGLISH

    /** Managed scope for one-shot audio playback; cancelled in [destroy]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Idempotent: repeated calls must not leak a second ToneGenerator/TextToSpeech. */
    fun initialize() {
        if (toneGenerator == null) {
            toneGenerator = try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            } catch (e: RuntimeException) {
                // ToneGenerator can throw on some devices when the audio resource is unavailable.
                null
            }
        }
        if (tts == null) {
            initTts()
        }
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

    suspend fun playTurnNotification() {
        withContext(Dispatchers.IO) {
            playTurnJingle()
        }
    }

    private fun playTurnJingle() {
        val sampleRate = 44100
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .build()

        // C5 (523.25 Hz) at 0ms, E5 (659.25 Hz) at 150ms
        val notes = listOf(
            Triple(523.25, 0.0, 0.15),
            Triple(659.25, 0.15, 0.15),
        )
        val totalDuration = 0.3
        val totalSamples = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)

        for ((freq, start, duration) in notes) {
            val startSample = (start * sampleRate).toInt()
            val noteSamples = (duration * sampleRate).toInt()
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / sampleRate
                val attack = (i.toDouble() / (0.005 * sampleRate)).coerceIn(0.0, 1.0)
                val release = ((noteSamples - i).toDouble() / (0.01 * sampleRate)).coerceIn(0.0, 1.0)
                val envelope = attack * release
                val sample = (sin(2 * PI * freq * t) * 0.5 * envelope * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[startSample + i] = sample.toShort()
            }
        }

        track.play()
        track.write(buffer, 0, totalSamples)
        Thread.sleep(350)
        track.stop()
        track.release()
    }

    fun playExtend() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 100)
    }

    fun playVictory() {
        scope.launch {
            playFanfare()
        }
    }

    private fun playFanfare() {
        val sampleRate = 44100
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .build()

        // C5-E5-G5-C6 fanfare (freq, startSeconds, durationSeconds)
        val notes = listOf(
            Triple(523.25, 0.0, 0.15),    // C5
            Triple(659.25, 0.15, 0.15),   // E5
            Triple(783.99, 0.3, 0.15),    // G5
            Triple(1046.50, 0.45, 0.4),   // C6 (longer)
        )

        val totalDuration = notes.maxOf { it.second + it.third }
        val totalSamples = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)

        for ((freq, start, duration) in notes) {
            val startSample = (start * sampleRate).toInt()
            val noteSamples = (duration * sampleRate).toInt()
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / sampleRate
                // Envelope: quick attack, sustain, quick release
                val attack = (i.toDouble() / (0.01 * sampleRate)).coerceIn(0.0, 1.0)
                val release = ((noteSamples - i).toDouble() / (0.02 * sampleRate)).coerceIn(0.0, 1.0)
                val envelope = attack * release
                val sample = (sin(2 * PI * freq * t) * 0.6 * envelope * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[startSample + i] = sample.toShort()
            }
        }

        track.play()
        track.write(buffer, 0, totalSamples)
        // Let the last note ring out
        Thread.sleep((totalDuration * 1000).toLong() + 50)
        track.stop()
        track.release()
    }

    fun announcePlayer(playerName: String) {
        speak(playerName)
    }

    fun destroy() {
        scope.cancel()
        toneGenerator?.release()
        toneGenerator = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

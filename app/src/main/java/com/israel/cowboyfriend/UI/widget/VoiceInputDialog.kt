package com.israel.cowboyfriend.UI.widget

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.global.AppLanguage

/**
 * In-app voice input dialog (styled for the Wild West screen) on top of [SpeechRecognizer].
 *
 * - [resolve] turns a recognized sentence into the value the field needs, or null when the sentence
 *   is not a usable answer (background noise, wind, other people talking). Unusable results are
 *   ignored and the dialog keeps listening.
 * - For short answers the dialog closes by itself as soon as a usable answer stays unchanged for a
 *   moment, without waiting for the recognizer's own end-of-speech timeout.
 */
class VoiceInputDialog(
    private val context: Context,
    private val prompt: String,
    private val freeText: Boolean,
    private val biasStrings: List<String>,
    private val resolve: (String) -> String?,
    private val onResult: (String) -> Unit,
    private val onFailed: (lastHeard: String?) -> Unit,
    //dictation mode (comments): the dialog keeps listening and collecting sentences until one of these words is said as the
    //last word of a sentence (e.g. "end"); empty = the normal mode that closes after one answer
    private val endWords: List<String> = emptyList()
) {
    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var recognizer: SpeechRecognizer? = null
    private var tvStatus: TextView? = null
    private var tvPartial: TextView? = null
    private var ring: View? = null

    //dictation mode: the sentences collected so far, and the number of listening rounds in a row without any speech
    private val dictation = endWords.isNotEmpty()
    private val endWordsLower = endWords.map { it.lowercase() }
    private var dictated = ""
    private var idleRounds = 0

    //how many times listening was restarted because nothing usable was heard
    private var attempts = 0
    private var finished = false
    private var lastHeard: String? = null
    //usable answer seen in the partial results, accepted if it stays unchanged for EARLY_ACCEPT_DELAY_MS
    private var pendingValue: String? = null
    private val acceptPending = Runnable { pendingValue?.let { finishWith(it) } }

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_voice_input, null, false)
        view.findViewById<TextView>(R.id.tvVoicePrompt).text = prompt
        tvStatus = view.findViewById(R.id.tvVoiceStatus)
        tvPartial = view.findViewById(R.id.tvVoicePartial)
        ring = view.findViewById(R.id.voiceRing)
        view.findViewById<View>(R.id.btnVoiceCancel).setOnClickListener { dismissSilently() }

        val card = view.findViewById<View>(R.id.voiceCard)
        card.alpha = 0f
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()

        dialog = Dialog(context).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(view)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            com.israel.cowboyfriend.global.DialogSizing.apply(this, 0.9f)
            window?.setDimAmount(0.55f)
            setCanceledOnTouchOutside(false)
            setOnCancelListener { dismissSilently() }
            show()
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
        }
        //dictation shows a longer text, so the live text field gets more lines and a smaller font
        if (dictation) {
            tvPartial?.maxLines = 6
            tvPartial?.textSize = 18f
        }
        //short pause so the end of the spoken prompt (TTS) is not picked up by the microphone
        showListeningStatus()
        handler.postDelayed({ startListening() }, START_DELAY_MS)
    }

    private fun startListening() {
        if (finished) return
        pendingValue = null
        //short answers end after 0.7s of silence, free text (comments) waits 2s so sentences are not cut
        val silenceMillis = if (dictation) 2500L else if (freeText) 2000L else 700L
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                if (freeText) RecognizerIntent.LANGUAGE_MODEL_FREE_FORM else RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
            )
            //the recognizer listens in the language chosen in the app
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.speechTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, AppLanguage.speechTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMillis)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMillis)
            //hint the recognizer toward the expected words (digits, gender options), available from Android 13
            if (Build.VERSION.SDK_INT >= 33 && biasStrings.isNotEmpty()) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasStrings))
            }
        }
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            showListeningStatus()
        }

        override fun onBeginningOfSpeech() {
            idleRounds = 0
            if (!dictation) tvStatus?.setText(R.string.voice_hearing)
        }

        //the ring around the mic grows with the voice level
        override fun onRmsChanged(rmsdB: Float) {
            val level = (rmsdB.coerceIn(0f, 10f)) / 10f
            //at most 1.2: the ring (130dp) must stay inside its 160dp frame, a bigger ring would be cut into a square
            val scale = 1f + level * 0.2f
            ring?.animate()?.scaleX(scale)?.scaleY(scale)?.setDuration(90)?.start()
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {
            val candidates = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            if (dictation) {
                //dictation: show everything said so far; the end word as the last word finishes at once
                val ending = candidates.firstOrNull { stripEndWord(it).second }
                if (ending != null) {
                    finishDictation(joinText(dictated, stripEndWord(ending).first))
                } else {
                    candidates.firstOrNull()?.let { tvPartial?.text = joinText(dictated, it) }
                }
                return
            }
            val shown = candidates.firstOrNull() ?: return
            tvPartial?.text = shown
            val value = candidates.firstNotNullOfOrNull { resolve(it) }
            if (value != null && !freeText) {
                //a usable short answer: close as soon as it stays the same for a moment
                if (value != pendingValue) {
                    pendingValue = value
                    handler.removeCallbacks(acceptPending)
                    handler.postDelayed(acceptPending, EARLY_ACCEPT_DELAY_MS)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (dictation) {
                onDictationSegment(candidates)
                return
            }
            val value = candidates.firstNotNullOfOrNull { resolve(it) }
            if (value != null) {
                finishWith(value)
            } else {
                candidates.firstOrNull { it.isNotBlank() }?.let { lastHeard = it }
                retryOrGiveUp()
            }
        }

        //no match / timeout means noise or silence: listen again, other errors close the dialog
        override fun onError(error: Int) {
            if (finished) return
            if (dictation) {
                onDictationError(error)
                return
            }
            pendingValue?.let { finishWith(it); return }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> retryOrGiveUp()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    recognizer?.cancel()
                    handler.postDelayed({ startListening() }, 500)
                }
                else -> {
                    tvStatus?.setText(R.string.voice_error)
                    handler.postDelayed({ giveUp() }, 1200)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun showListeningStatus() {
        //in dictation mode the status always reminds how to finish
        tvStatus?.setText(if (dictation) R.string.voice_dictation_hint else R.string.voice_listening)
    }

    //the text without a trailing end word, and whether the text did end with an end word
    private fun stripEndWord(text: String): Pair<String, Boolean> {
        val tokens = text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val last = tokens.lastOrNull()?.trim { !it.isLetterOrDigit() }?.lowercase()
        return if (last != null && last in endWordsLower) {
            Pair(tokens.dropLast(1).joinToString(" "), true)
        } else {
            Pair(text.trim(), false)
        }
    }

    private fun joinText(old: String, added: String): String =
        if (old.isEmpty()) added.trim() else if (added.isBlank()) old else old + " " + added.trim()

    //a sentence ended (the recognizer detected silence): add it to the text and listen again, unless it ended with an end word
    private fun onDictationSegment(candidates: List<String>) {
        val text = candidates.firstOrNull { stripEndWord(it).second } ?: candidates.firstOrNull { it.isNotBlank() }
        if (text == null) {
            onDictationIdle()
            return
        }
        idleRounds = 0
        val (clean, isEnd) = stripEndWord(text)
        dictated = joinText(dictated, clean)
        tvPartial?.text = dictated
        if (isEnd) finishDictation(dictated) else handler.postDelayed({ startListening() }, 150)
    }

    //nothing was heard in this round: listen again; after many silent rounds the text collected so far is returned
    private fun onDictationIdle() {
        idleRounds++
        if (idleRounds > MAX_IDLE_ROUNDS) finishDictation(dictated) else handler.postDelayed({ startListening() }, 150)
    }

    private fun onDictationError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onDictationIdle()
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                recognizer?.cancel()
                handler.postDelayed({ startListening() }, 500)
            }
            else -> {
                tvStatus?.setText(R.string.voice_error)
                handler.postDelayed({ finishDictation(dictated) }, 1200)
            }
        }
    }

    //the end word was said (or listening was idle for too long): return the text, or report a failure when nothing was said
    private fun finishDictation(text: String) {
        if (text.isBlank()) giveUp() else finishWith(text)
    }

    private fun retryOrGiveUp() {
        if (finished) return
        attempts++
        if (attempts > MAX_RETRIES) {
            giveUp()
            return
        }
        //noise or an unusable sentence: keep the dialog open and listen again
        tvStatus?.setText(R.string.voice_retry)
        tvPartial?.text = ""
        handler.postDelayed({ startListening() }, 400)
    }

    private fun finishWith(value: String) {
        if (finished) return
        finished = true
        cleanup()
        onResult(value)
    }

    private fun giveUp() {
        if (finished) return
        finished = true
        cleanup()
        onFailed(lastHeard)
    }

    private fun dismissSilently() {
        if (finished) return
        finished = true
        cleanup()
    }

    //stop and release the recognizer, the callbacks and the dialog (must always be called when closing)
    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.apply {
            cancel()
            destroy()
        }
        recognizer = null
        dialog?.setOnCancelListener(null)
        dialog?.dismiss()
        dialog = null
    }

    companion object {
        private const val MAX_RETRIES = 2
        //dictation: silent listening rounds in a row (each a few seconds) before the dialog closes by itself
        private const val MAX_IDLE_ROUNDS = 10
        private const val START_DELAY_MS = 700L
        private const val EARLY_ACCEPT_DELAY_MS = 650L
    }
}

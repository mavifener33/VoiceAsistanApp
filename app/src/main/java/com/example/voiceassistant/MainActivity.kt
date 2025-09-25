package com.example.voiceassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import com.google.gson.Gson
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var btnListen: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var listening = false

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // IMPORTANT: Put your OpenAI key in gradle.properties as OPENAI_API_KEY="sk-..."
    private val OPENAI_API_KEY by lazy {
        // fallback to a placeholder if not set; replace in gradle.properties for real use
        BuildConfig.OPENAI_API_KEY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvTranscript = findViewById(R.id.tvTranscript)
        btnListen = findViewById(R.id.btnListen)

        tts = TextToSpeech(this, this)

        btnListen.setOnClickListener {
            if (listening) stopListening() else startListening()
        }

        // runtime permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 123)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "SpeechRecognizer mevcut değil."
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { tvStatus.text = "Dinliyor..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { tvStatus.text = "Ses girişi sonlandı..." }
            override fun onError(error: Int) {
                tvStatus.text = "Hata: $error"
                listening = false
                btnListen.text = "Dinlemeyi Başlat / Durdur"
                speechRecognizer?.destroy()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.joinToString(" ") ?: ""
                tvTranscript.text = text
                tvStatus.text = "Metin tanındı, işleniyor..."
                coroutineScope.launch {
                    processUserText(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = RecognizerIntent.getVoiceDetailsIntent(this)
        val recogIntent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        recogIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuş...")
        speechRecognizer?.startListening(recogIntent)

        listening = true
        btnListen.text = "Durdur"
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        listening = false
        btnListen.text = "Dinlemeyi Başlat / Durdur"
        tvStatus.text = "Durdu"
    }

    private suspend fun processUserText(userText: String) {
        withContext(Dispatchers.IO) {
            try {
                val reply = sendToOpenAI(userText)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Cevap alındı"
                    tvTranscript.text = "Soru: $userText\n\nCevap: $reply"
                    speak(reply)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "İstek başarısız: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun sendToOpenAI(userText: String): String {
        val client = OkHttpClient()
        val gson = Gson()

        val bodyMap = mapOf(
            "model" to "gpt-3.5-turbo",
            "messages" to listOf(mapOf("role" to "user", "content" to userText)),
            "max_tokens" to 300
        )
        val jsonBody = gson.toJson(bodyMap)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = RequestBody.create(mediaType, jsonBody)

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${OPENAI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP error: ${response.code} ${response.message}")
            val respStr = response.body?.string() ?: throw Exception("Empty response")
            val json = gson.fromJson(respStr, Map::class.java)
            val choices = json["choices"] as? List<*>
            val first = choices?.get(0) as? Map<*, *>
            val message = first?.get("message") as? Map<*, *>
            val content = message?.get("content") as? String ?: ""
            return content.trim()
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistantReply")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts.stop()
        tts.shutdown()
        coroutineScope.cancel()
    }
}

package com.israel.cowboyfriend

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private var etSpeechText:EditText?=null
    private var btnTextToSpeech:Button?=null
    private var btnSpeechToText:Button?=null
    private var textToSpeech:TextToSpeech?=null


    /**
     * recognize voice and convert to text
     */
    val resultSpeakLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                    val res =result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if(res!=null && res.size>0) {
                        etSpeechText?.setText(res[0])
                    }
            }
            else -> {
                // logic
            }
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()

        //configure text to speech
        textToSpeech=TextToSpeech(this){ status->
            if(status==TextToSpeech.SUCCESS){
                val result = textToSpeech?.setLanguage(Locale.getDefault())//Locale("iw"))//Locale.getDefault())
                if (result==TextToSpeech.LANG_MISSING_DATA
                    || result==TextToSpeech.LANG_NOT_SUPPORTED){
                    Toast.makeText(this,"language is not supported",Toast.LENGTH_SHORT).show()
                }
            }
        }


        btnTextToSpeech?.setOnClickListener {
            if(etSpeechText?.text?.toString()?.trim()?.isNotEmpty() == true){
                textToSpeech?.speak(etSpeechText?.text?.toString()?.trim(),TextToSpeech.QUEUE_FLUSH,null,null)
            }else{
                Toast.makeText(this,"text require",Toast.LENGTH_SHORT).show()
            }
        }

    }


    private fun initViews() {
        etSpeechText = findViewById(R.id.etSpeechText)
        btnTextToSpeech = findViewById(R.id.btnTextToSpeech)
        btnSpeechToText = findViewById(R.id.btnSpeechToText)
        btnTextToSpeech?.setOnClickListener {

        }
        btnSpeechToText?.setOnClickListener {
            startMyVoice()
        }
    }

    /**
     * open dialog to accept voice
     */
    private fun startMyVoice() {
        textToSpeech?.speak("enter a calf number",TextToSpeech.QUEUE_FLUSH,null,null)
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak Now")
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        //intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, REQUEST_CODE)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")//""en-US")

        try {
            resultSpeakLauncher.launch(intent)
        } catch (a: ActivityNotFoundException) {
            Toast.makeText(applicationContext, "Recogniser not present", Toast.LENGTH_SHORT).show()
        }
    }
}
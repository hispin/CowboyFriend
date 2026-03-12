package com.israel.cowboyfriend.UI

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.global.getStringFromCalendar
import de.hdodenhof.circleimageview.CircleImageView
import java.lang.Long
import java.util.Calendar
import java.util.Locale


class NewCalfFragment : Fragment() , TextToSpeech.OnInitListener{

    private var uri: Uri? = null
    private var textToSpeech:TextToSpeech?=null
    private var etCurrent:EditText? = null
    private var hasToBeNumber:Boolean = false
    private var etNumberOfCalf:EditText? = null
    private var ciNumberOfCalf:CircleImageView?=null
    private var etGenderOfCalf:EditText? = null
    private var ciGenderOfCalf:CircleImageView?=null
    private var etNumberOfMom:EditText? = null
    private var ciNumberOfMom:CircleImageView?=null
    private var ciTakePicture:CircleImageView?=null
    private var ivTakePicture:ImageView?=null
    private var tvDate:TextView?=null



    private val UTTERANCE_ID = "my_unique_utterance_id"

    /**
     *  callback function that is called when the Text-to-Speech (TTS) engine has finished its initialization process
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)

            if (result != TextToSpeech.LANG_MISSING_DATA || result != TextToSpeech.LANG_NOT_SUPPORTED) {
                // Set the progress listener
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d("TTS", "Speech started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d("TTS", "Speech finished: $utteranceId")
                        openDialogToEnterCalfNumber()
                        // Perform actions after speech is done (e.g., update UI on the main thread)
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e("TTS", "Speech error: $utteranceId")
                    }

                    // onRangeStart is available in newer APIs to highlight words as they are spoken
                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        // Handle word highlighting
                    }
                })
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    /**
     * define listener handler after image capture
     */
    var startCamera: ActivityResultLauncher<Intent> =
        registerForActivityResult<Intent, ActivityResult>(
            StartActivityForResult(), object : ActivityResultCallback<ActivityResult?> {

                override fun onActivityResult(result: ActivityResult?) {
                    if (result!!.resultCode == Activity.RESULT_OK) {
                        ivTakePicture?.setImageURI(uri)
                    }
                }
            })

    /**
     * recognize voice and convert to text
     */
    val resultSpeakLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val res =result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if(res!=null && res.size>0) {

                    if(hasToBeNumber) {
                        val number=res[0].toString().toIntOrNull()
                        if(number!=null){
                            etCurrent?.setText(number.toString())
                        }else{
                            //convert text word to number digits(the problem is just in 1-9)
                            covertTextToTextDigits(res[0].toString(),etCurrent)}
                    }else {
                        etCurrent?.setText(res[0].toString())
                    }
                }
            }
            else -> {
                // logic
            }
        }
    }

    /*
    * convert text word to number digits(the problem is just in 1-9)
     */
    private fun covertTextToTextDigits(text: String, etCurrent: EditText?) {
        when(text){
            "אחד"-> {
                etCurrent?.setText("1")
            }
            "שתיים"-> {
                etCurrent?.setText("2")
            }
            "שניים"-> {
                etCurrent?.setText("2")
            }
            "שלוש"-> {
                etCurrent?.setText("3")
            }
            "ארבע"-> {
                etCurrent?.setText("4")
            }
            "חמש"-> {
                etCurrent?.setText("5")
            }
            "שש"-> {
                etCurrent?.setText("6")
            }
            "שבע"-> {
                etCurrent?.setText("7")
            }
            "שמונה"-> {
                etCurrent?.setText("8")
            }
            "תשע"-> {
                etCurrent?.setText("9")
            }
            else->
                etCurrent?.error="הערך השדה זה צריך להיות מספרי"

        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //initialize text to speech
        textToSpeech = TextToSpeech(requireActivity(), this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view= inflater.inflate(R.layout.fragment_new_calf, container, false)

        initView(view)

        //configure text to speech
        textToSpeech=TextToSpeech(requireActivity()){ status->
            if(status== TextToSpeech.SUCCESS){
                val result = textToSpeech?.setLanguage(Locale.getDefault())//Locale("iw"))//Locale.getDefault())
                if (result== TextToSpeech.LANG_MISSING_DATA
                    || result== TextToSpeech.LANG_NOT_SUPPORTED){
                    Toast.makeText(requireActivity(),"language is not supported", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return view
    }

    private fun initView(view: View?) {
        ciNumberOfCalf=view?.findViewById(R.id.ciNumberOfCalf)
        etNumberOfCalf =view?.findViewById(R.id.etNumberOfCalf)
        etGenderOfCalf=view?.findViewById(R.id.etGenderOfCalf)
        ciGenderOfCalf =view?.findViewById(R.id.ciGenderOfCalf)
        etNumberOfMom=view?.findViewById(R.id.etNumberOfMom)
        ciNumberOfMom =view?.findViewById(R.id.ciNumberOfMom)
        ciTakePicture =view?.findViewById(R.id.ciTakePicture)
        ivTakePicture =view?.findViewById(R.id.ivTakePicture)
        tvDate=view?.findViewById(R.id.tvDate)

        tvDate?.text=getStringFromCalendar(Calendar.getInstance(), "dd/MM/yy", requireActivity())

        ciNumberOfCalf?.setOnClickListener {
            speakNow("בחר מספר של העגל",etNumberOfCalf,true)
        }
        ciGenderOfCalf?.setOnClickListener {
            speakNow("בחר את המין של העגל",etGenderOfCalf,false)
        }
        ciNumberOfMom?.setOnClickListener {
            speakNow("בחר מספר של האמא",etNumberOfMom,true)
        }

        ciTakePicture?.setOnClickListener {
            takePicture()
        }

    }

    /*
    Take a picture
     */
    private fun takePicture() {
        val values=ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
        uri=requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values        )
        val cameraIntent=Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        startCamera.launch(cameraIntent)
    }


    /**
     * open dialog to accept voice
     */
    private fun openDialogToEnterCalfNumber(){

        val recognizerIntent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "הכנס מספר עגל")
        recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")//""en-US")
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,  Long(2000))
        // Set longer silence detection
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L) // 5 seconds
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L) // 5 seconds
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        try {
            resultSpeakLauncher.launch(recognizerIntent)
        } catch (a: ActivityNotFoundException) {
            Toast.makeText(requireActivity(), "Recogniser not present", Toast.LENGTH_SHORT).show()
        }
    }



    //convert text to speak
    fun speakNow(text: String, etCurrent: EditText?,hasToBeNumber:Boolean) {
        this.hasToBeNumber = hasToBeNumber
        this.etCurrent=etCurrent
        val params = Bundle()
        params.putString(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")//""en-US")
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    override fun onDestroy() {
        if (textToSpeech != null) {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        super.onDestroy()
    }

}
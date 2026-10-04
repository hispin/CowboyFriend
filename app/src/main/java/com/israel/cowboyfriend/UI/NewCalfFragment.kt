package com.israel.cowboyfriend.UI

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.global.CURRENT_LOCATION_LATITUDE
import com.israel.cowboyfriend.global.CURRENT_LOCATION_LONGITUDE
import com.israel.cowboyfriend.global.GET_CURRENT_SINGLE_LOCATION_KEY
import com.israel.cowboyfriend.global.UIHelper
import com.israel.cowboyfriend.global.baseUrl
import com.israel.cowboyfriend.global.getStringFromCalendar
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.interfaces.CowStorageRespose
import com.israel.cowboyfriend.services.ServiceFindSingleLocation
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import dagger.hilt.android.AndroidEntryPoint
import de.hdodenhof.circleimageview.CircleImageView
import io.github.jan.supabase.auth.auth
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class NewCalfFragment : Fragment() , TextToSpeech.OnInitListener{

    //private var startCamera: ActivityResultLauncher<Intent>?=null
    private var myTextOrder: String?=null
    private var uri: Uri? = null
    private var textToSpeech:TextToSpeech?=null
    private var etCurrent:EditText? = null
    private var hasToBeNumber:Boolean = false
    private var etNumberOfCalf:EditText? = null
    private var ciNumberOfCalf:CircleImageView?=null
    private var spGenderCalf:Spinner? = null
    private var ciGenderOfCalf:CircleImageView?=null
    //private var etNumberOfMom:EditText? = null
    private var ciNumberOfMom:CircleImageView?=null
    private var ciTakePicture:CircleImageView?=null
    private var ivTakePicture:ImageView?=null
    private var tvDate:TextView?=null
    private var ciSave: CircleImageView?=null
    private var myViewModelSupbase: MyViewModelSupbase? = null
    private var etComments: EditText?=null
    private var ciComments: CircleImageView?=null
    //fixed saving overlay (spinning horseshoe) and the result banner shown after a save
    private var loadingOverlay: View?=null
    private var ivLoadingSpinner: ImageView?=null
    private var resultBanner: View?=null
    private var ivResultIcon: ImageView?=null
    private var tvResultText: TextView?=null
    //hides the result banner by itself after RESULT_BANNER_MILLIS
    private val resultHandler = Handler(Looper.getMainLooper())
    private val hideResultRunnable = Runnable { hideResult() }
    private var tvLocationLatitude: TextView?=null
    private var tvLocationLongitude: TextView?=null
    private var btnSaveLocation: Button?=null
    //status icon + text next to the save location button (location captured / not captured)
    private var ivLocationStatus: android.widget.ImageView?=null
    private var tvLocationStatus: TextView?=null
    private var spNewCowType: com.israel.cowboyfriend.UI.widget.CowTypeCarouselView?=null
    private var cbWithMomNew: CheckBox?=null
    private var cbEarTag: CheckBox?=null
    private var tvNumberOfMom: TextView?=null
    private var tvNumberOfCalvings: TextView?=null
    private var tvComments: TextView?=null
    private var etNumberOfCalvings: EditText?=null
    //2-digit odometer that shows / edits the hidden etNumberOfCalvings
    private var odoNumberOfCalvings: com.israel.cowboyfriend.UI.widget.OdometerNumberView?=null
    private var ciNumberOfCalvings: CircleImageView?=null
    private var cowTypeArray: Array<out String?>?=null
    private var spMoms: Spinner? = null
    //how long the save result banner stays on screen
    private val RESULT_BANNER_MILLIS = 3200L
    private var isMomNumberField = false
    private var isGenderField = false
    private val UTTERANCE_ID = "my_unique_utterance_id"
    private val UTTERANCE_ID_MESSAGE = "my_message_utterance_id"

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
                        //only reopen the mic to listen for an answer after speaking a prompt
                        //that expects one - not after a plain spoken message (e.g. an error)
                        if (utteranceId == UTTERANCE_ID) {
                            //the in-app voice dialog replaces the system speech dialog
                            startVoiceInput()
                        }
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


//    fun registerCallbackPicture(){
//        startCamera =
//            registerForActivityResult<Intent, ActivityResult>(
//                StartActivityForResult(), object : ActivityResultCallback<ActivityResult?> {
//
//                    override fun onActivityResult(result: ActivityResult?) {
//                        if (result!!.resultCode == Activity.RESULT_OK) {
//                            ivTakePicture?.setImageURI(uri)
//                        }
//                    }
//                })
//
//    }

//    /**
//     * define listener handler after image capture
//     */
//    var startCamera: ActivityResultLauncher<Intent> =
//        registerForActivityResult<Intent, ActivityResult>(
//            StartActivityForResult(), object : ActivityResultCallback<ActivityResult?> {
//
//                override fun onActivityResult(result: ActivityResult?) {
//                    if (result!!.resultCode == Activity.RESULT_OK) {
//                        ivTakePicture?.setImageURI(uri)
//                    }
//                }
//            })

    /**
     * recognize voice and convert to text
     */
    val resultSpeakLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val res = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (res != null && res.size > 0) {
                val value = res.firstNotNullOfOrNull { resolveVoiceCandidate(it) }
                if (value != null) applyVoiceValue(value) else onVoiceNotUnderstood(res[0])
            }
        }
    }

    //asks for the microphone once; when denied the system speech dialog is used instead
    private val micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showVoiceDialog()
        } else {
            Toast.makeText(requireActivity(), R.string.voice_permission_denied, Toast.LENGTH_SHORT).show()
            openSystemVoiceDialog()
        }
    }

    /*
    * turn a recognized sentence into the value the currently selected field needs:
    * number fields -> digits, gender -> a spinner option, other fields -> the text itself.
    * null means the sentence is not a usable answer (noise, unrelated talking)
     */
    private fun resolveVoiceCandidate(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return when {
            hasToBeNumber -> extractNumber(trimmed)
            isGenderField -> findSpinnerOption(spGenderCalf, trimmed)
            else -> trimmed
        }
    }

    //put a recognized value into the field that started the voice input
    private fun applyVoiceValue(value: String) {
        when {
            hasToBeNumber && isMomNumberField ->
                //mom number is picked from the spMoms spinner, not typed freely
                selectInSpinnerOrSpeakError(spMoms, value, R.string.mom_number_not_found)
            hasToBeNumber -> etCurrent?.setText(value)
            isGenderField ->
                //gender is picked from the spGenderCalf spinner, not typed freely
                selectInSpinnerOrSpeakError(spGenderCalf, value, R.string.gender_not_found)
            else -> etCurrent?.setText(value)
        }
    }

    //nothing usable was recognized: speak / show the matching error (nothing heard at all stays silent)
    private fun onVoiceNotUnderstood(heard: String?) {
        when {
            heard.isNullOrBlank() -> {}
            hasToBeNumber && isMomNumberField -> speakMessage(resources.getString(R.string.mom_number_not_found))
            hasToBeNumber -> etCurrent?.error = getString(R.string.field_must_be_number)
            isGenderField -> speakMessage(resources.getString(R.string.gender_not_found))
        }
    }

    /*
    * pull a number out of a recognized sentence: "123", "1 2 3", "מספר 45", "ארבע"
     */
    private fun extractNumber(text: String): String? {
        if (text.matches(Regex("""[\d\s]+"""))) {
            return text.replace(" ", "").toIntOrNull()?.toString()
        }
        Regex("""\d+""").find(text)?.let { match ->
            match.value.toIntOrNull()?.let { return it.toString() }
        }
        for (word in text.split(" ", ",", ".")) {
            hebrewWordToDigit(word)?.let { return it }
        }
        return null
    }

    /*
    * map a Hebrew number word (0-10) to its digit string, or null if not recognized
     */
    private fun hebrewWordToDigit(text: String): String? {
        return when(text){
            "אפס"-> "0"
            "אחד","אחת"-> "1"
            "שתיים","שניים","שתים","שנים"-> "2"
            "שלוש","שלושה"-> "3"
            "ארבע","ארבעה"-> "4"
            "חמש","חמישה"-> "5"
            "שש","שישה"-> "6"
            "שבע","שבעה"-> "7"
            "שמונה"-> "8"
            "תשע","תשעה"-> "9"
            "עשר","עשרה"-> "10"
            else-> null
        }
    }

    /*
    * find the spinner option (other than the first "choose" placeholder) that appears in the sentence
     */
    private fun findSpinnerOption(spinner: Spinner?, text: String): String? {
        val adapter = spinner?.adapter ?: return null
        for (i in 1 until adapter.count) {
            val option = adapter.getItem(i)?.toString() ?: continue
            if (option.isNotBlank() && text.contains(option)) return option
        }
        return null
    }

    /*
    * search the recognized value in the given spinner (spMoms or spGenderCalf): if it exists,
    * select it there; otherwise speak (using notFoundMessageResId) that it's not in the list
     */
    private fun selectInSpinnerOrSpeakError(spinner: Spinner?, recognizedValue: String, notFoundMessageResId: Int) {
        val adapter = spinner?.adapter
        var foundPosition = -1
        if (adapter != null) {
            for (i in 0 until adapter.count) {
                if (adapter.getItem(i)?.toString() == recognizedValue) {
                    foundPosition = i
                    break
                }
            }
        }

        if (foundPosition >= 0) {
            spinner?.setSelection(foundPosition)
        } else {
            speakMessage(resources.getString(notFoundMessageResId))
        }
    }

    /*
    * speak a plain message, unlike speakNow this does not reopen the mic to listen
    * for an answer afterward
     */
    private fun speakMessage(text: String) {
        val params = Bundle()
        params.putString(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID_MESSAGE)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID_MESSAGE)
    }


    override fun onStart() {
        super.onStart()
        setFilter()

    }

    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //initialize text to speech
        textToSpeech = TextToSpeech(requireActivity(), this)

        takePictureLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->

            ivTakePicture?.setImageURI(uri)
            //viewModel.setCaptureSuccess(success)
            // התוצאה נשמרת ב-ViewModel
        }
        //registerCallbackPicture()
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

        myViewModelSupbase = ViewModelProvider(requireActivity())[MyViewModelSupbase::class.java]

       cowTypeArray= context?.resources?.getStringArray(R.array.cow_type)

       val cowTypes = cowTypeArray?.filterNotNull() ?: emptyList()
       spNewCowType?.setItems(cowTypes, 0)
       spNewCowType?.onSelectionChanged = { _, value -> updateFieldsByCowType(value) }
       updateFieldsByCowType(cowTypes.getOrNull(0))

       setObservers()

       return view
    }


    /**
     * set observers
     */
    private fun setObservers() {
        myViewModelSupbase?._cowsDetails?.observe(viewLifecycleOwner) {
            if(activity==null) return@observe
            val cows =ArrayList<CowDetails>()
            cows.addAll(it)
            val moms =ArrayList<String>()
            //placeholder shown by default instead of auto-selecting the first mom number
            moms.add(resources.getString(R.string.select_mom_placeholder))
            val iterator=cows.iterator()
            while (iterator.hasNext()) {
                val item=iterator.next()
                if(item.cowType.equals(cowTypeArray?.get(2))) {
                    moms.add(item.number.toString())
                }
            }
            //custom item layout: bold black text, larger font, so the selected mom
            //number stands out in the spinner
            val adapter =ArrayAdapter(
                requireActivity(), R.layout.item_spinner_mom, moms
            )

            adapter.setDropDownViewResource(R.layout.item_spinner_mom);

        //Attach the adapter to the spinner
            spMoms?.adapter = adapter
        }
    }


    /**
     * show/hide fields according to the selected cow type:
     * calf -> mom fields, cow / first-cow -> number of calvings, bull -> neither
     */
    private fun updateFieldsByCowType(selectedType: Any?) {
        val isCalf = cowTypeArray?.get(0)?.equals(selectedType) == true
        val hasCalvings = cowTypeArray?.get(1)?.equals(selectedType) == true
                || cowTypeArray?.get(2)?.equals(selectedType) == true

        val momVisibility = if (isCalf) View.VISIBLE else View.GONE
        tvNumberOfMom?.visibility = momVisibility
        spMoms?.visibility = momVisibility
        ciNumberOfMom?.visibility = momVisibility
        cbWithMomNew?.visibility = momVisibility

        //the ear tag checkbox is for a calf only - clear it when hidden so a stale
        //"checked" value is never saved for another cow type
        cbEarTag?.visibility = momVisibility
        if (!isCalf) {
            cbEarTag?.isChecked = false
        }

        val calvingsVisibility = if (hasCalvings) View.VISIBLE else View.GONE
        tvNumberOfCalvings?.visibility = calvingsVisibility
        //the hidden etNumberOfCalvings stays gone, only its odometer is shown
        odoNumberOfCalvings?.visibility = calvingsVisibility
        ciNumberOfCalvings?.visibility = calvingsVisibility

    }

    /**
     * the odometer shows / edits the value of a hidden EditText (voice input writes to the EditText)
     */
    private fun bindOdometer(odo: com.israel.cowboyfriend.UI.widget.OdometerNumberView?, holder: EditText?) {
        odo?.onNumberChanged = { holder?.setText(it.toString()) }
        holder?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                odo?.setNumber(s?.toString()?.toIntOrNull())
            }
        })
    }

    /**
     * fixed loading indicator (does not scroll with the form); the horseshoe is an animated vector
     * so it keeps spinning even while the save blocks the main thread
     */
    private fun showLoading() {
        loadingOverlay?.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(150).start()
        }
        (ivLoadingSpinner?.drawable as? android.graphics.drawable.Animatable)?.start()
    }

    private fun hideLoading() {
        (ivLoadingSpinner?.drawable as? android.graphics.drawable.Animatable)?.stop()
        loadingOverlay?.visibility = View.GONE
    }

    /**
     * fixed result message at the bottom of the screen, slides in and hides itself
     */
    private fun showResult(success: Boolean) {
        val banner = resultBanner ?: return
        ivResultIcon?.setImageResource(if (success) R.drawable.ic_banner_success else R.drawable.ic_banner_fail)
        tvResultText?.setText(if (success) R.string.cow_saved else R.string.cow_save_failed)
        resultHandler.removeCallbacks(hideResultRunnable)
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = 80 * resources.displayMetrics.density
        banner.animate().alpha(1f).translationY(0f).setDuration(220).start()
        resultHandler.postDelayed(hideResultRunnable, RESULT_BANNER_MILLIS)
    }

    private fun hideResult() {
        resultHandler.removeCallbacks(hideResultRunnable)
        val banner = resultBanner ?: return
        banner.animate().alpha(0f).translationY(80 * resources.displayMetrics.density).setDuration(200)
            .withEndAction { banner.visibility = View.GONE }.start()
    }

    /**
     * show whether the current location was captured (the coordinates themselves are not shown)
     */
    private fun setLocationSaved(saved: Boolean) {
        ivLocationStatus?.setImageResource(if (saved) R.drawable.ic_location_saved else R.drawable.ic_location_unsaved)
        tvLocationStatus?.setText(if (saved) R.string.location_saved else R.string.location_not_saved)
        tvLocationStatus?.setTextColor(
            ContextCompat.getColor(requireContext(), if (saved) R.color.ww_turquoise_dark else R.color.ww_rust_dark)
        )
        if (saved) {
            ivLocationStatus?.scaleX = 0.6f
            ivLocationStatus?.scaleY = 0.6f
            ivLocationStatus?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(250)?.start()
        }
    }

    private fun initView(view: View?) {
        ciNumberOfCalf=view?.findViewById(R.id.ciNumberOfCalf)
        etNumberOfCalf =view?.findViewById(R.id.etNumberOfCalf)
        //etNumberOfCalf is the hidden value holder; the odometer shows / edits it (voice input writes to the EditText)
        bindOdometer(view?.findViewById(R.id.odoNumberOfCalf), etNumberOfCalf)
        spGenderCalf=view?.findViewById(R.id.spGenderCalf)
        //custom item layout: bold black text, larger font - same as spMoms
        val genderOptions = resources.getStringArray(R.array.gender_options).toList()
        val genderAdapter = ArrayAdapter(requireActivity(), R.layout.item_spinner_mom, genderOptions)
        genderAdapter.setDropDownViewResource(R.layout.item_spinner_mom)
        spGenderCalf?.adapter = genderAdapter
        ciGenderOfCalf =view?.findViewById(R.id.ciGenderOfCalf)
        ciNumberOfMom =view?.findViewById(R.id.ciNumberOfMom)
        ciTakePicture =view?.findViewById(R.id.ciTakePicture)
        ivTakePicture =view?.findViewById(R.id.ivTakePicture)
        tvDate=view?.findViewById(R.id.tvDate)
        ciSave=view?.findViewById(R.id.ciSave)
        etComments=view?.findViewById(R.id.etComments)
        //after the keyboard opened, scroll so the comments field and a bit below it stay visible
        etComments?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.postDelayed({
                    v.requestRectangleOnScreen(android.graphics.Rect(0, 0, v.width, v.height + 200), false)
                }, 350)
            }
        }
        ciComments=view?.findViewById(R.id.ciComments)
        loadingOverlay=view?.findViewById(R.id.loadingOverlay)
        ivLoadingSpinner=view?.findViewById(R.id.ivLoadingSpinner)
        resultBanner=view?.findViewById(R.id.resultBanner)
        ivResultIcon=view?.findViewById(R.id.ivResultIcon)
        tvResultText=view?.findViewById(R.id.tvResultText)
        resultBanner?.setOnClickListener { hideResult() }
        tvLocationLatitude=view?.findViewById(R.id.tvLocationLatitude)
        tvLocationLongitude=view?.findViewById(R.id.tvLocationLongitude)
        btnSaveLocation=view?.findViewById(R.id.btnSaveLocation)
        btnSaveLocation?.setOnClickListener {
            btnSaveLocation?.text=requireActivity().resources.getString(R.string.load)
            gotoMySingleLocation()
        }


        cbWithMomNew = view?.findViewById(R.id.cbWithMomNew)
        cbEarTag = view?.findViewById(R.id.cbEarTag)
        tvNumberOfMom = view?.findViewById(R.id.tvNumberOfMom)
        tvNumberOfCalvings = view?.findViewById(R.id.tvNumberOfCalvings)
        tvComments = view?.findViewById(R.id.tvComments)
        etNumberOfCalvings = view?.findViewById(R.id.etNumberOfCalvings)
        //voice input and saving keep using the hidden EditText, the odometer is kept in sync with it
        odoNumberOfCalvings = view?.findViewById(R.id.odoNumberOfCalvings)
        bindOdometer(odoNumberOfCalvings, etNumberOfCalvings)
        ciNumberOfCalvings = view?.findViewById(R.id.ciNumberOfCalvings)
        ivLocationStatus = view?.findViewById(R.id.ivLocationStatus)
        tvLocationStatus = view?.findViewById(R.id.tvLocationStatus)
        //no location captured yet
        setLocationSaved(false)

        spNewCowType = view?.findViewById(R.id.spNewCowType)
        spMoms= view?.findViewById(R.id.spMoms)

        tvDate?.text=getStringFromCalendar(Calendar.getInstance(), "dd/MM/yy", requireActivity())

        ciNumberOfCalf?.setOnClickListener {
            isMomNumberField=false
            isGenderField=false
            speakNow(getString(R.string.voice_prompt_cow_number),etNumberOfCalf,true)
        }
        ciGenderOfCalf?.setOnClickListener {
            isMomNumberField=false
            isGenderField=true
            speakNow(getString(R.string.voice_prompt_cow_gender),false)
        }
        ciNumberOfMom?.setOnClickListener {
            isMomNumberField=true
            isGenderField=false
            speakNow(getString(R.string.voice_prompt_mom_number),true)
        }
        ciNumberOfCalvings?.setOnClickListener {
            isMomNumberField=false
            isGenderField=false
            speakNow(getString(R.string.voice_prompt_calvings_number),etNumberOfCalvings,true)
        }
        ciComments?.setOnClickListener {
            isMomNumberField=false
            isGenderField=false
            speakNow(getString(R.string.voice_prompt_comment),etComments,false)
        }

        ciTakePicture?.setOnClickListener {
            takePicture()
        }

        ciSave?.setOnClickListener {
            //fixed loading overlay instead of the old ProgressBar
            showLoading()
            // delay to enable visible the progress bar
            Handler(Looper.getMainLooper()).postDelayed({
                    myViewModelSupbase?.uploadCowImage(uri,requireContext(),
                        object :
                        CowStorageRespose {
                            override fun onRequestResult(url: String) {
                                var myUrl=baseUrl+""+url
                                insertCowDetails(myUrl)
                            }

                            //upload failed: stop the loading overlay and tell the user
                            override fun onError() {
                                hideLoading()
                                showResult(false)
                            }

                            /**
                             * insert cow to database
                             */
                      private fun insertCowDetails(myUrl: String) {

                             val supabase=   myViewModelSupbase?.getSupabase()

                             //no connection object: nothing can be saved, do not leave the overlay on
                             if(supabase==null) {
                                 hideLoading()
                                 showResult(false)
                                 return
                             }

                             val lat: Double?= tvLocationLatitude?.text.toString().toDoubleOrNull()
                             val long: Double?= tvLocationLongitude?.text.toString().toDoubleOrNull()
                             val cowType = spNewCowType?.selectedItem.toString()

                             var with_mom: Long? = null
                             if(cowType.equals(cowTypeArray?.get(0))
                                 && cbWithMomNew?.isChecked==true){
                                 with_mom=Calendar.getInstance().timeInMillis
                             }



                             var cow:CowDetails?=null
                             if(lat!=null && long!=null) {
                                 cow=CowDetails(
                                     number=etNumberOfCalf?.text.toString().toIntOrNull(),
                                     number_mom=spMoms?.selectedItem.toString().toIntOrNull(),
                                     gender=spGenderCalf?.selectedItem.toString(),
                                     image_url=myUrl,
                                     user_id=supabase.auth.currentSessionOrNull()?.user?.email,
                                     comment=etComments?.text.toString(),
                                     lat,
                                     long,
                                     Calendar.getInstance().timeInMillis,
                                     Calendar.getInstance().timeInMillis,
                                     cowType,
                                     with_mom,
                                     cbEarTag?.isChecked == true
                                 )
                             }else{
                                 cow=CowDetails(
                                     number=etNumberOfCalf?.text.toString().toIntOrNull(),
                                     number_mom=spMoms?.selectedItem.toString().toIntOrNull(),
                                     gender=spGenderCalf?.selectedItem.toString(),
                                     image_url=myUrl,
                                     user_id=supabase.auth.currentSessionOrNull()?.user?.email,
                                     comment=etComments?.text.toString(),
                                     null,
                                     null,
                                     null,
                                     Calendar.getInstance().timeInMillis,
                                     cowType,
                                     with_mom,
                                     cbEarTag?.isChecked == true
                                 )
                             }

                             cow.isMarkedTag = cbEarTag?.isChecked == true

                             //mom number only for a calf, number of calvings only for a cow / first-cow
                             if(!cowType.equals(cowTypeArray?.get(0))){
                                 cow.number_mom=null
                             }
                             if(cowType.equals(cowTypeArray?.get(1)) || cowType.equals(cowTypeArray?.get(2))){
                                 cow.num_of_calvings=etNumberOfCalvings?.text.toString().toIntOrNull()
                             }

                             myViewModelSupbase?.dbInsertCowDetails(cow,object : CowRepositoryCB {

                                 override fun onRequestResult(result: Int) {
                                     //the insert finished: stop the loading overlay and show the success / failure banner
                                     hideLoading()
                                     showResult(result==1)
                                 }
                             })
                             }

                        })
            }, 500)
        }

    }

    /*
    Take a picture
     */
    private fun takePicture() {
//        val values=ContentValues()
//        values.put(MediaStore.Images.Media.TITLE, "New Picture")
//        values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
//        uri=requireContext().contentResolver.insert(
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values        )
//        val cameraIntent=Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
//
        uri = createImageUri()

        if(uri!=null) {
            takePictureLauncher.launch(uri!!)
        }
    }


    private fun createImageUri(): Uri {
        val contentValues=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        return requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ) ?: throw IllegalStateException("Failed to create image URI")

    }


        /**
     * open the in-app voice dialog (microphone permission and a recognition service are required,
     * otherwise fall back to the system speech dialog)
     */
    private fun startVoiceInput() {
        activity?.runOnUiThread {
            val ctx = context ?: return@runOnUiThread
            if (!android.speech.SpeechRecognizer.isRecognitionAvailable(ctx)) {
                openSystemVoiceDialog()
            } else if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                showVoiceDialog()
            }
        }
    }

    private fun showVoiceDialog() {
        val ctx = context ?: return
        val freeText = !hasToBeNumber && !isGenderField
        val bias = when {
            isGenderField -> (1 until (spGenderCalf?.adapter?.count ?: 0))
                .mapNotNull { spGenderCalf?.adapter?.getItem(it)?.toString() }
            hasToBeNumber -> (0..9).map { it.toString() }
            else -> emptyList()
        }
        com.israel.cowboyfriend.UI.widget.VoiceInputDialog(
            context = ctx,
            prompt = myTextOrder ?: "",
            freeText = freeText,
            biasStrings = bias,
            resolve = { resolveVoiceCandidate(it) },
            onResult = { applyVoiceValue(it) },
            onFailed = { onVoiceNotUnderstood(it) }
        ).show()
    }

        /**
     * open dialog to accept voice
     */
    private fun openSystemVoiceDialog(){

        val recognizerIntent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, myTextOrder)
        recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")//""en-US")
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,  Long(2000))
        // Set longer silence detection
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L) // 5 seconds
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L) // 5 seconds
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        try {
            resultSpeakLauncher.launch(recognizerIntent)
        } catch (a: ActivityNotFoundException) {
            Toast.makeText(requireActivity(), "Recogniser not present", Toast.LENGTH_SHORT).show()
        }
    }



    //convert text to speak
    fun speakNow(text: String, etCurrent: EditText?,hasToBeNumber:Boolean) {
        this.myTextOrder = text
        this.hasToBeNumber = hasToBeNumber
        this.etCurrent=etCurrent
        val params = Bundle()
        params.putString(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")//""en-US")
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    //convert text to speak
    fun speakNow(text: String,hasToBeNumber:Boolean) {
        this.myTextOrder = text
        this.hasToBeNumber = hasToBeNumber
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
        activity?.unregisterReceiver(brdReceiver)
        super.onDestroy()
    }

    /**
     * get current location from gps
     */
    private fun gotoMySingleLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.startForegroundService(Intent(context, ServiceFindSingleLocation::class.java))
        } else {
            activity?.startService(Intent(context, ServiceFindSingleLocation::class.java))
        }
    }

    /**
     * define broadcast receiver for getting current location
     */
    private val brdReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, inn: Intent) {
            //accept currentAlarm
            if (inn.action == GET_CURRENT_SINGLE_LOCATION_KEY) {

                val latitude = inn.extras?.getDouble(CURRENT_LOCATION_LATITUDE)
                val longitude = inn.extras?.getDouble(CURRENT_LOCATION_LONGITUDE)

                if (latitude != null && longitude != null) {
                    tvLocationLatitude?.text = latitude.toString()
                    tvLocationLongitude?.text = longitude.toString()
                    btnSaveLocation?.text=requireActivity().resources.getString(R.string.save_location)
                    //location arrived: switch the indicator to saved
                    setLocationSaved(true)

                } else {
                    Toast.makeText(activity, "error in location2", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setFilter() {
        val filter = IntentFilter(GET_CURRENT_SINGLE_LOCATION_KEY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.registerReceiver(brdReceiver, filter, AppCompatActivity.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                requireActivity(),
                brdReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

}
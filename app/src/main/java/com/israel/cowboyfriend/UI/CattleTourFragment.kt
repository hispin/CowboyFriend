package com.israel.cowboyfriend.UI

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.israel.cowboyfriend.UI.widget.VoiceInputDialog
import com.israel.cowboyfriend.global.AppLanguage
import com.israel.cowboyfriend.global.CowIssues
import com.israel.cowboyfriend.global.CowLabels
import com.israel.cowboyfriend.classes.TourState
import com.israel.cowboyfriend.global.DialogSizing
import com.israel.cowboyfriend.global.CowQuery
import com.israel.cowboyfriend.global.VoiceNumbers
import com.israel.cowboyfriend.global.getStringFromCalendar
import java.util.Calendar
import java.util.Locale
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.adapter.MyCowAdapter
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.OnFragmentListener
import com.israel.cowboyfriend.global.ACTION_PICTURE_KEY
import com.israel.cowboyfriend.global.ACTION_EDIT_COW_TYPE
import com.israel.cowboyfriend.global.ACTION_SHOW_COMMENT_TYPE
import com.israel.cowboyfriend.global.ALL_FIELDS_TYPE
import com.israel.cowboyfriend.global.CURRENT_LOCATION_LATITUDE
import com.israel.cowboyfriend.global.CURRENT_LOCATION_LONGITUDE
import com.israel.cowboyfriend.global.GET_CURRENT_SINGLE_LOCATION_KEY
import com.israel.cowboyfriend.services.ServiceFindSingleLocation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.israel.cowboyfriend.global.ACTION_SHOW_HEALTH_TYPE
import com.israel.cowboyfriend.classes.HealthIssues
import com.israel.cowboyfriend.global.ACTION_SHOW_LARGE_TYPE
import com.israel.cowboyfriend.global.ACTION_TYPE_KEY
import com.israel.cowboyfriend.global.ACTION_UPDATE_TYPE
import com.israel.cowboyfriend.global.IMAGE_PATH_KEY
import com.israel.cowboyfriend.global.IMAGE_TIME_KEY
import com.israel.cowboyfriend.global.shareVideo
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.interfaces.InterOnItemClickListener
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase

class CattleTourFragment : Fragment() ,OnFragmentListener{

    private var rcShowCows: RecyclerView? =null
    private var myViewModelSupbase: MyViewModelSupbase? = null
    private var myCowsAdapter: MyCowAdapter?=null
    private var fbRefreshCows: FloatingActionButton?=null

    //the list filters: all cows, only calves, only calves not seen today, only bulls
    private enum class CowFilter { ALL, ATTENTION, CALVES, CALVES_NOT_SEEN_TODAY, BULLS, HEALTH }
    private var currentFilter = CowFilter.ALL
    //all the cows from the database, the list on the screen is this list after the filter
    private var allCows = ArrayList<CowDetails>()
    //counter of the cows shown, the message for an empty result and the filter chips
    private var tvCowsCount: TextView? = null
    private var tvEmptyList: TextView? = null
    //fixed saving overlay (spinning horseshoe) and the result banner shown after a save
    private var loadingOverlay: View? = null
    private var ivLoadingSpinner: ImageView? = null
    private var resultBanner: View? = null
    private var ivResultIcon: ImageView? = null
    private var tvResultText: TextView? = null
    //how long the result banner stays on the screen
    private val RESULT_BANNER_MILLIS = 3200L
    //hides the result banner by itself after RESULT_BANNER_MILLIS
    private val resultHandler = Handler(Looper.getMainLooper())
    private val hideResultRunnable = Runnable { hideResult() }
    private var filterChips: Map<CowFilter, TextView> = emptyMap()
    //the tour bar: the state of the tour and its button (start / end), and the last state loaded from the database
    private var llTourPill: View? = null
    private var ivTourPill: ImageView? = null
    private var tvTourPill: TextView? = null
    private var tourState = TourState()
    //true while a tour is being started or ended (a second tap does nothing)
    private var tourBusy = false

    //voice search: the cows that were asked for (number and optional year of birth), in order (empty = no search),
    //and the chip that shows and cancels it
    private var searchQueries: List<CowQuery> = emptyList()
    private var chipSearch: TextView? = null
    //spoken instruction before listening; when Hebrew speech is not available the dialog opens directly
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    //what to do after the microphone permission was just granted (dictation of a comment); null = the voice search
    private var afterMicPermission: (() -> Unit)? = null

    //asks for the microphone once, then continues with the action that needed it
    private val micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterMicPermission
        afterMicPermission = null
        if (granted) { if (action != null) action() else startVoiceSearch() }
        else Toast.makeText(requireActivity(), R.string.voice_permission_denied, Toast.LENGTH_SHORT).show()
    }

    //keep a reference to the currently open large picture/video dialog so we can
    //stop its progress bar once a callback (e.g. share) it triggered completes
    private var largePictureVideoDialog: LargePictureVideoDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //text to speech, used to read a comment aloud
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                //the voice speaks in the language chosen in the app
                val result = textToSpeech?.setLanguage(AppLanguage.speechLocale())
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                //no progress listener is needed: the speech is only used to read a comment aloud
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view= inflater.inflate(R.layout.fragment_cattle_tour, container, false)

        initViews(view)

        myViewModelSupbase = ViewModelProvider(requireActivity())[MyViewModelSupbase::class.java]


        setObservers()

        getCowDetails()

        return view
    }

    /**
     * set observers
     */
    private fun setObservers() {
        myViewModelSupbase?._cowsDetails?.observe(viewLifecycleOwner) {
            //keep the full list and show it through the selected filter
            allCows = ArrayList(it)
            applyFilter()
            updateAttentionChip()
        }
        //the tour bar follows the state of the tour (no tour, or the progress of the open tour)
        myViewModelSupbase?._tourState?.observe(viewLifecycleOwner) {
            tourState = it
            updateTourBar()
        }
        //the summary of a tour asked to show the cows that need attention
        activity?.supportFragmentManager?.setFragmentResultListener(TourSummaryDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, result ->
            if (result.getString(TourSummaryDialogFragment.RESULT_ACTION) == TourSummaryDialogFragment.ACTION_SHOW_ATTENTION) {
                selectFilter(CowFilter.ATTENTION)
            }
        }
    }

    //the chip "needs attention" shows how many cows need attention
    private fun updateAttentionChip() {
        val count = allCows.count { CowIssues.needsAttention(it) }
        filterChips[CowFilter.ATTENTION]?.text = getString(R.string.filter_attention) + if (count > 0) " ($count)" else ""
    }

    /**
     * the tour pill (above the refresh button): only a flag while there is no tour, and turquoise with the progress
     * ("12/18") while a tour is open. everything else about the tour is in the tour window (see [showTourDialog])
     */
    private fun updateTourBar() {
        val open = tourState.openTourId != null
        llTourPill?.setBackgroundResource(if (open) R.drawable.shape_ww_tour_pill_active else R.drawable.shape_ww_tour_pill_idle)
        ivTourPill?.setColorFilter(ContextCompat.getColor(requireContext(), if (open) R.color.ww_parchment else R.color.ww_iron))
        tvTourPill?.apply {
            visibility = if (open) View.VISIBLE else View.GONE
            text = "${tourState.seen}/${tourState.alive}"
        }
    }

    /**
     * the tour window: the state of the tour (no tour, or the progress of the open one), the main button (start / end the
     * tour) and the tour history. ending the tour is done from this window, so it is a deliberate action: the window tells
     * how many cows were not seen yet right above the button
     */
    private fun showTourDialog() {
        val ctx = context ?: return
        val state = tourState
        val open = state.openTourId != null
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_tour, null, false)
        val dialog = newAppDialog(ctx, content, 0.92f, null)

        val headline = content.findViewById<TextView>(R.id.tvTourDialogHeadline)
        val info = content.findViewById<TextView>(R.id.tvTourDialogInfo)
        val main = content.findViewById<TextView>(R.id.btnTourDialogMain)
        if (open) {
            headline.text = getString(R.string.summary_seen_of, state.seen, state.alive)
            val notSeen = (state.alive - state.seen).coerceAtLeast(0)
            val lines = ArrayList<String>()
            state.startedAtMillis?.let {
                val time = getStringFromCalendar(Calendar.getInstance().apply { timeInMillis = it }, "HH:mm", ctx)
                lines.add(getString(R.string.tour_dialog_since, androidx.core.text.BidiFormatter.getInstance().unicodeWrap(time, androidx.core.text.TextDirectionHeuristicsCompat.LTR)))
            }
            lines.add(if (notSeen > 0) getString(R.string.tour_end_not_seen, notSeen) else getString(R.string.tour_end_all_seen))
            info.text = lines.joinToString("\n")
            //the progress bar
            content.findViewById<View>(R.id.llTourDialogTrack).visibility = View.VISIBLE
            val percent = if (state.alive > 0) (state.seen * 100f / state.alive).coerceIn(0f, 100f) else 0f
            content.findViewById<View>(R.id.vTourDialogFill).let {
                (it.layoutParams as android.widget.LinearLayout.LayoutParams).weight = percent
            }
            //ending is the heavier action: the sand button with rust text
            main.setText(R.string.tour_end)
            main.setBackgroundResource(R.drawable.shape_ww_btn_rect_sand)
            main.setTextColor(ContextCompat.getColor(ctx, R.color.ww_rust_dark))
        } else {
            headline.setText(R.string.tour_dialog_idle_headline)
            info.setText(R.string.tour_dialog_idle_info)
            main.setText(R.string.tour_start)
        }

        main.setOnClickListener {
            dialog.dismiss()
            if (tourBusy) return@setOnClickListener
            val tourId = state.openTourId
            if (tourId == null) startTour() else endTour(tourId)
        }
        content.findViewById<View>(R.id.btnTourDialogHistory).setOnClickListener {
            dialog.dismiss()
            activity?.supportFragmentManager?.let { TourHistoryDialogFragment().show(it, "TourHistoryDialogFragment") }
        }
        content.findViewById<View>(R.id.btnTourDialogDashboard).setOnClickListener {
            dialog.dismiss()
            activity?.supportFragmentManager?.let { RanchDashboardDialogFragment().show(it, "RanchDashboardDialogFragment") }
        }
        content.findViewById<View>(R.id.btnTourDialogClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun startTour() {
        tourBusy = true
        myViewModelSupbase?.dbStartTour { ok ->
            tourBusy = false
            if (!isAdded) return@dbStartTour
            if (ok) showTourMessage(R.string.tour_started, true) else showTourMessage(R.string.tour_start_failed, false)
        }
    }

    //ends the tour: the loading overlay, then the summary screen (or a failure message)
    private fun endTour(tourId: String) {
        tourBusy = true
        showLoading()
        myViewModelSupbase?.dbEndTour(tourId) { summary ->
            tourBusy = false
            if (!isAdded) return@dbEndTour
            hideLoading()
            if (summary == null) {
                showTourMessage(R.string.tour_end_failed, false)
            } else {
                activity?.supportFragmentManager?.let { TourSummaryDialogFragment.newInstance(summary).show(it, "TourSummaryDialogFragment") }
            }
        }
    }

    //a short message at the bottom (the same banner as the save result): success or failure
    private fun showTourMessage(messageRes: Int, success: Boolean) {
        val banner = resultBanner ?: return
        ivResultIcon?.setImageResource(if (success) R.drawable.ic_banner_success else R.drawable.ic_banner_fail)
        tvResultText?.setText(messageRes)
        resultHandler.removeCallbacks(hideResultRunnable)
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = 80 * resources.displayMetrics.density
        banner.animate().alpha(1f).translationY(0f).setDuration(220).start()
        resultHandler.postDelayed(hideResultRunnable, RESULT_BANNER_MILLIS)
    }


    private fun initViews(view: View) {
        rcShowCows=view.findViewById(R.id.rcShowCows)
        fbRefreshCows=view.findViewById(R.id.fbRefreshCows)
        tvCowsCount=view.findViewById(R.id.tvCowsCount)
        tvEmptyList=view.findViewById(R.id.tvEmptyList)
        //the tour pill opens the tour window
        llTourPill = view.findViewById(R.id.llTourPill)
        ivTourPill = view.findViewById(R.id.ivTourPill)
        tvTourPill = view.findViewById(R.id.tvTourPill)
        llTourPill?.setOnClickListener { showTourDialog() }
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        ivLoadingSpinner = view.findViewById(R.id.ivLoadingSpinner)
        resultBanner = view.findViewById(R.id.resultBanner)
        ivResultIcon = view.findViewById(R.id.ivResultIcon)
        tvResultText = view.findViewById(R.id.tvResultText)
        resultBanner?.setOnClickListener { hideResult() }
        chipSearch=view.findViewById(R.id.chipSearch)
        //tapping the search chip cancels the voice search and shows the list of the selected filter again
        chipSearch?.setOnClickListener { clearSearch() }
        view.findViewById<View>(R.id.ivVoiceSearch).setOnClickListener { startVoiceSearch() }
        //the small "?" next to the microphone opens the guide of the voice search
        view.findViewById<View>(R.id.btnSearchGuide).setOnClickListener { showSearchGuide() }
        //each chip selects its filter; tapping the selected chip again goes back to showing all the cows
        filterChips = mapOf(
            CowFilter.ALL to view.findViewById<TextView>(R.id.chipFilterAll),
            CowFilter.ATTENTION to view.findViewById<TextView>(R.id.chipFilterAttention),
            CowFilter.CALVES to view.findViewById<TextView>(R.id.chipFilterCalves),
            CowFilter.CALVES_NOT_SEEN_TODAY to view.findViewById<TextView>(R.id.chipFilterCalvesNotSeen),
            CowFilter.BULLS to view.findViewById<TextView>(R.id.chipFilterBulls),
            CowFilter.HEALTH to view.findViewById<TextView>(R.id.chipFilterHealth)
        )
        filterChips.forEach { (filter, chip) ->
            chip.setOnClickListener {
                selectFilter(if (currentFilter == filter && searchQueries.isEmpty()) CowFilter.ALL else filter)
            }
        }
        updateFilterChips()
        //in a right-to-left screen the chips start at the right edge: scroll there so the first chip (all) is visible
        view.findViewById<android.widget.HorizontalScrollView>(R.id.hsvFilters).let { hsv ->
            hsv.post { if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) hsv.fullScroll(View.FOCUS_RIGHT) }
        }
        fbRefreshCows?.setOnClickListener {
            myCowsAdapter=null
            getCowDetails()
        }
    }

    /**
     * get details of all cows
     */
    fun getCowDetails(){
        myViewModelSupbase?.dbGetCowsDetails()
        //the state of the tour (an open tour and its progress) is loaded together with the cows
        myViewModelSupbase?.dbLoadTourState()
    }

//    private fun getCowDetails1() {
//
//        myViewModelSupbase?.getCowsDetails(object :
//            CowRepositoryCBselect {
//
//
//            override fun onRequestResult(cowsDto: ArrayList<CowDto>?) {
//
//                val cowsDetails =ArrayList<CowDetails>()
//
//                val iterator = cowsDto?.iterator()
//
//                while (iterator?.hasNext() == true) {
//                    val item = iterator.next()
//                    val cow =CowDetails(
//                        number=item.number, number_mom=item.number_mom
//                        , gender=item.gender, image_url=item.image_url, user_id=item.user_id, comment = item.comment, latitude = item.latitude, longitude = item.longitude
//                    )
//                    cowsDetails.add(cow)
//                }
//                showCowsDetails(cowsDetails)
//            }
//        })
//    }


    //true when the cow was seen today (same day, month and year as now)
    private fun isSeenToday(cow: CowDetails): Boolean {
        val seen = cow.last_seen_at ?: return false
        val seenDate = java.util.Calendar.getInstance().apply { timeInMillis = seen }
        val today = java.util.Calendar.getInstance()
        return seenDate.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
                && seenDate.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    }

    //change the filter, show it on the chips and refresh the list; choosing a filter also cancels a voice search
    private fun selectFilter(filter: CowFilter) {
        currentFilter = filter
        searchQueries = emptyList()
        //the row positions change with the filter, so the last edited row is no longer valid
        lastPosition = null
        updateFilterChips()
        applyFilter()
    }

    //only the chip of the current filter is selected (dark), the others are sand colored;
    //during a voice search none of them is selected and the search chip shows the searched number
    private fun updateFilterChips() {
        filterChips.forEach { (filter, chip) -> chip.isSelected = searchQueries.isEmpty() && filter == currentFilter }
        chipSearch?.let { chip ->
            val queries = searchQueries
            chip.visibility = if (queries.isNotEmpty()) View.VISIBLE else View.GONE
            //the search chip is always shown selected (dark) while it is visible
            chip.isSelected = true
            if (queries.isNotEmpty()) chip.text = getString(R.string.search_number_chip, queries.joinToString(", ") { it.label() })
        }
        //in a right-to-left screen the chips start at the right edge: keep the first chip visible
        view?.findViewById<android.widget.HorizontalScrollView>(R.id.hsvFilters)?.let { hsv ->
            hsv.post { if (hsv.layoutDirection == View.LAYOUT_DIRECTION_RTL) hsv.fullScroll(View.FOCUS_RIGHT) }
        }
    }

    /**
     * show the cows that match the voice search (when there is one) or the current filter, with the counter
     * and the empty message. the cow types are the items of the cow_type array: the first one is the calf,
     * the last one is the bull
     */
    private fun applyFilter() {
        //the cow type is saved as Hebrew text whatever the language of the app is, so it is compared by its position
        val isCalf = { cow: CowDetails -> CowLabels.typeIndex(cow.cowType) == CowLabels.TYPE_CALF }
        val isBull = { cow: CowDetails -> CowLabels.typeIndex(cow.cowType) == CowLabels.TYPE_BULL }
        val queries = searchQueries
        val filtered = if (queries.isNotEmpty()) {
            //every cow that matches one of the asked cows is shown, in the order they were said: the number must be
            //the same, and when a year was said also the last two digits of the year of birth (two cows with the
            //same number both appear)
            val matches = LinkedHashSet<CowDetails>()
            for (query in queries) {
                allCows.filter { cow -> cow.number == query.number && (query.year == null || birthYearTwoDigits(cow) == query.year) }
                    .forEach { matches.add(it) }
            }
            matches.toList()
        } else when (currentFilter) {
            CowFilter.ALL -> allCows
            //the cows that need attention (see CowIssues: the same rules as the tour summary)
            CowFilter.ATTENTION -> allCows.filter { CowIssues.needsAttention(it) }
            CowFilter.CALVES -> allCows.filter { isCalf(it) }
            CowFilter.CALVES_NOT_SEEN_TODAY -> allCows.filter { isCalf(it) && !isSeenToday(it) }
            CowFilter.BULLS -> allCows.filter { isBull(it) }
            //the cows with a health problem (a corpse is not shown)
            CowFilter.HEALTH -> allCows.filter { it.healthIssues.isNotEmpty() && !it.isCorpse }
        }
        showCowsDetails(ArrayList(filtered))

        //the counter: "12 cows" for all of them, "showing 3 of 12" under a filter or a search
        tvCowsCount?.text = if (currentFilter == CowFilter.ALL && queries.isEmpty()) {
            getString(R.string.cows_count_all, filtered.size)
        } else {
            getString(R.string.cows_count_filtered, filtered.size, allCows.size)
        }
        //nothing matches: a message instead of an empty screen
        tvEmptyList?.setText(if (queries.isNotEmpty()) R.string.no_cow_with_number else R.string.no_cows_match_filter)
        tvEmptyList?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * voice search: opens the listening dialog at once (no spoken introduction).
     * needs the microphone permission and a speech recognizer
     */
    private fun startVoiceSearch() {
        val ctx = context ?: return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Toast.makeText(ctx, R.string.voice_error, Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        //no spoken introduction: the dialog already shows what to say, so it opens and listens at once
        showVoiceSearchDialog()
    }

    //the in-app voice dialog: only a number is accepted, noise and other words are ignored by the dialog
    private fun showVoiceSearchDialog() {
        val ctx = context ?: return
        VoiceInputDialog(
            context = ctx,
            prompt = getString(R.string.voice_prompt_search_cow),
            //dictation: the dialog keeps listening, pauses are allowed, and it closes only when the last word of a sentence
            //is "end" / "סוף" (the Hebrew recognizer may write "end" in Hebrew letters, so "אנד" counts too)
            freeText = true,
            biasStrings = (0..9).map { it.toString() },
            resolve = { it.trim().takeIf { text -> text.isNotEmpty() } },
            endWords = listOf("סוף", "end", "אנד"),
            onResult = { text ->
                //the whole sentence (without the end word) becomes the list of asked cows
                val queries = VoiceNumbers.parseCowQueries(text)
                if (queries.isEmpty()) {
                    Toast.makeText(ctx, R.string.voice_search_not_understood, Toast.LENGTH_SHORT).show()
                } else {
                    applySearch(queries)
                }
            },
            onFailed = { Toast.makeText(ctx, R.string.voice_search_not_understood, Toast.LENGTH_SHORT).show() }
        ).show()
    }

    //the last two digits of the year of birth of the cow (created_at), or null when the birth date is not known
    private fun birthYearTwoDigits(cow: CowDetails): Int? {
        val created = cow.created_at ?: return null
        val calendar = Calendar.getInstance().apply { timeInMillis = created }
        return calendar.get(Calendar.YEAR) % 100
    }

    //show only the asked cows
    private fun applySearch(queries: List<CowQuery>) {
        searchQueries = queries
        lastPosition = null
        updateFilterChips()
        applyFilter()
    }

    /**
     * fixed loading overlay (does not scroll with the list); the horseshoe is an animated vector, so it keeps
     * spinning even while the save blocks the main thread
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
     * fixed result message at the bottom of the screen: slides in and hides itself after a few seconds, or when tapped
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
     * saves the edited cow: the edit window closes, the loading overlay shows, and when the database answers the loading
     * stops and the success / failure message is shown. on failure the list is loaded again from the database, because
     * the cow in memory already holds the edited values
     */
    private fun saveEditedCow(cow: CowDetails, position: Int) {
        lastPosition = position
        showLoading()
        //a short delay so the overlay is drawn before the save blocks the main thread
        Handler(Looper.getMainLooper()).postDelayed({
            myViewModelSupbase?.dbUpdateCowDetails(cow, ALL_FIELDS_TYPE, object : CowRepositoryCB {
                override fun onRequestResult(result: Int) {
                    if (!isAdded) return
                    hideLoading()
                    showResult(result == 1)
                    if (result != 1) getCowDetails()
                    //the cow was changed: the progress of an open tour changed too
                    else myViewModelSupbase?.dbLoadTourState()
                }
            })
        }, 500)
    }

    //creates the window of a dialog in the style of the app: transparent background, dimmed screen, the width in percent
    private fun newAppDialog(ctx: android.content.Context, content: View, widthPercent: Float, heightPercent: Float?): android.app.Dialog {
        return android.app.Dialog(ctx).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            //the width is a part of the screen but never wider than the limit (so a wide landscape screen keeps a normal window)
            //the edit window (it has a height) has two columns in landscape, so it may be wider
            DialogSizing.apply(this, widthPercent, heightPercent, if (heightPercent != null) 760 else 560)
            //the keyboard pushes the window up instead of covering the field
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window?.setDimAmount(0.55f)
        }
    }

    /**
     * the comment window of a cow, VIEW only: shows the comment and reads it aloud (Hebrew) when it opens;
     * the speaker badge reads it again. closing the window stops the reading. the comment is edited in the edit window
     */
    private fun showCommentViewDialog(cow: CowDetails) {
        val ctx = context ?: return
        val comment = cow.comment?.trim()
        if (comment.isNullOrEmpty()) return

        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_comment, null, false)
        content.findViewById<TextView>(R.id.tvCommentTitle).text =
            getString(R.string.comment_title) + " " + getString(R.string.card_cow_number, cow.number?.toString() ?: "-")
        content.findViewById<TextView>(R.id.tvCommentText).text = comment
        //in landscape the screen is short: the comment plate gets a limited height and scrolls, the close button stays visible
        if (DialogSizing.isLandscape(ctx)) {
            content.findViewById<View>(R.id.svComment).layoutParams.height = (ctx.resources.displayMetrics.heightPixels * 0.4f).toInt()
        }

        val dialog = newAppDialog(ctx, content, 0.92f, null)
        //the reading must stop whenever the window goes away
        dialog.setOnDismissListener { textToSpeech?.stop() }
        content.findViewById<View>(R.id.btnCommentClose).setOnClickListener { dialog.dismiss() }

        //a separate utterance id so the reading does not start the voice search dialog
        val read = {
            if (ttsReady) {
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "read_comment")
                textToSpeech?.speak(comment, TextToSpeech.QUEUE_FLUSH, params, "read_comment")
            }
        }
        content.findViewById<View>(R.id.ivCommentSpeak).setOnClickListener { read() }
        dialog.show()
        read()
    }

    /**
     * the edit window of a cow: the only place where a cow is edited. seen today (with / without the mother),
     * corpse, ear tag, cow type, a new location and the comment (typed or dictated). one save button writes
     * all the fields to the database in one request; cancel (or back / outside tap) drops the changes
     */
    private fun showEditCowDialog(cow: CowDetails, position: Int) {
        val ctx = context ?: return
        //the names of the cow types in the current language (the list of the spinner); the saved type is by position
        val types = resources.getStringArray(R.array.cow_type)

        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_cow_edit, null, false)
        content.findViewById<TextView>(R.id.tvEditTitle).text =
            getString(R.string.edit_cow_title) + " " + getString(R.string.card_cow_number, cow.number?.toString() ?: "-")

        val cbSeen = content.findViewById<android.widget.CheckBox>(R.id.cbEditSeen)
        val cbWithMom = content.findViewById<android.widget.CheckBox>(R.id.cbEditWithMom)
        val llWithMom = content.findViewById<View>(R.id.llEditWithMom)
        val cbCorpse = content.findViewById<android.widget.CheckBox>(R.id.cbEditCorpse)
        val cbEarTag = content.findViewById<android.widget.CheckBox>(R.id.cbEditEarTag)
        val llEarTag = content.findViewById<View>(R.id.llEditEarTag)
        val spType = content.findViewById<Spinner>(R.id.spEditCowType)
        val etComment = content.findViewById<android.widget.EditText>(R.id.etEditComment)
        val ivLocationStatus = content.findViewById<android.widget.ImageView>(R.id.ivEditLocationStatus)
        val tvLocationStatus = content.findViewById<TextView>(R.id.tvEditLocationStatus)

        //the current values of the cow
        //"seen today" is locked once it was recorded today; but in an open tour a cow that was seen earlier today, before the
        //tour started, can be marked as seen again (it counts as seen in the tour)
        val tourStart = tourState.startedAtMillis
        val wasSeenToday = isSeenToday(cow) && (tourStart == null || (cow.last_seen_at ?: 0L) >= tourStart)
        cbSeen.isChecked = wasSeenToday
        //already recorded for today: it cannot be taken back, so the checkbox is only shown
        cbSeen.isEnabled = !wasSeenToday
        cbWithMom.isChecked = cow.with_mom != null && cow.with_mom == cow.last_seen_at
        cbCorpse.isChecked = cow.isCorpse
        cbEarTag.isChecked = cow.isMarkedTag == true
        etComment.setText(cow.comment?.trim().orEmpty())
        etComment.setSelection(etComment.text.length)

        //health problems: a chip for every problem of the catalog (several can be chosen, a chosen chip is rust colored) and
        //"other", which shows a text field to describe the problem
        val cgHealth = content.findViewById<com.google.android.material.chip.ChipGroup>(R.id.cgEditHealth)
        val etHealthOther = content.findViewById<android.widget.EditText>(R.id.etEditHealthOther)
        val selectedHealth = LinkedHashSet(HealthIssues.selectedCodes(cow.healthIssues))
        etHealthOther.setText(HealthIssues.otherText(cow.healthIssues).orEmpty())
        etHealthOther.visibility = if (HealthIssues.OTHER_CODE in selectedHealth) View.VISIBLE else View.GONE
        val healthChoices = HealthIssues.CATALOG.map { it.code to getString(it.labelRes) } +
                (HealthIssues.OTHER_CODE to getString(R.string.health_other))
        for ((code, label) in healthChoices) {
            val chip = TextView(ctx, null, 0, R.style.Ww_HealthChip)
            chip.text = label
            chip.isSelected = code in selectedHealth
            chip.setOnClickListener {
                if (!selectedHealth.remove(code)) selectedHealth.add(code)
                chip.isSelected = code in selectedHealth
                //the text field of "other" is shown only while "other" is chosen
                if (code == HealthIssues.OTHER_CODE) {
                    etHealthOther.visibility = if (chip.isSelected) View.VISIBLE else View.GONE
                    if (chip.isSelected) etHealthOther.requestFocus()
                }
            }
            cgHealth.addView(chip)
        }

        //"with mom" belongs to a visit: it can be chosen only while the cow is marked as seen today
        val updateWithMomEnabled = {
            val enabled = cbSeen.isChecked
            cbWithMom.isEnabled = enabled
            llWithMom.alpha = if (enabled) 1f else 0.5f
            if (!enabled) cbWithMom.isChecked = false
        }
        cbSeen.setOnCheckedChangeListener { _, _ -> updateWithMomEnabled() }
        updateWithMomEnabled()

        //the cow type list; the rows that exist only for calves follow the selected type
        val typeAdapter = ArrayAdapter(ctx, R.layout.item_spinner_mom, types)
        typeAdapter.setDropDownViewResource(R.layout.item_spinner_mom)
        spType.adapter = typeAdapter
        spType.setSelection(CowLabels.typeIndex(cow.cowType).coerceAtLeast(0))
        spType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val isCalf = pos == CowLabels.TYPE_CALF
                llWithMom.visibility = if (isCalf) View.VISIBLE else View.GONE
                llEarTag.visibility = if (isCalf) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        //location: the button asks for the current location, the result waits here until the save button
        var newLatitude: Double? = null
        var newLongitude: Double? = null
        var newLocationTime: Long? = null
        val locationReceiver = object : BroadcastReceiver() {
            override fun onReceive(arg0: Context, inn: Intent) {
                if (inn.action != GET_CURRENT_SINGLE_LOCATION_KEY) return
                val latitude = inn.extras?.getDouble(CURRENT_LOCATION_LATITUDE)
                val longitude = inn.extras?.getDouble(CURRENT_LOCATION_LONGITUDE)
                if (latitude != null && longitude != null) {
                    newLatitude = latitude
                    newLongitude = longitude
                    newLocationTime = System.currentTimeMillis()
                    ivLocationStatus.setImageResource(R.drawable.ic_location_saved)
                    tvLocationStatus.setText(R.string.edit_location_done)
                    tvLocationStatus.setTextColor(ContextCompat.getColor(ctx, R.color.ww_turquoise_dark))
                }
            }
        }
        var receiverRegistered = false
        content.findViewById<View>(R.id.btnEditLocation).setOnClickListener {
            val act = activity ?: return@setOnClickListener
            if (!receiverRegistered) {
                val filter = IntentFilter(GET_CURRENT_SINGLE_LOCATION_KEY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    act.registerReceiver(locationReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
                } else {
                    ContextCompat.registerReceiver(act, locationReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                }
                receiverRegistered = true
            }
            tvLocationStatus.setText(R.string.edit_location_waiting)
            tvLocationStatus.setTextColor(ContextCompat.getColor(ctx, R.color.ww_ink_soft))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                act.startForegroundService(Intent(ctx, ServiceFindSingleLocation::class.java))
            } else {
                act.startService(Intent(ctx, ServiceFindSingleLocation::class.java))
            }
        }

        val dialog = newAppDialog(ctx, content, 0.94f, if (DialogSizing.isLandscape(ctx)) 0.94f else 0.88f)

        //the keyboard must not cover the comment field: the window is not resized by the system (ADJUST_NOTHING),
        //instead the content gets a bottom padding as big as the part of the window the keyboard covers, so the
        //scrolling fields shrink above the keyboard and the buttons stay visible over it
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        //computed from the CURRENT keyboard height, so it stays right while the keyboard animates and after it opened
        val applyKeyboardPadding = {
            val imeBottom = androidx.core.view.ViewCompat.getRootWindowInsets(content)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
            //the keyboard inset of a dialog window is already the part of THIS window the keyboard covers
            val covered = imeBottom
            if (content.paddingBottom != covered) {
                content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, covered)
                if (covered > 0 && etComment.hasFocus()) {
                    //after the layout has changed, scroll the field into view
                    etComment.post {
                        etComment.requestRectangleOnScreen(android.graphics.Rect(0, 0, etComment.width, etComment.height), false)
                    }
                }
            }
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            applyKeyboardPadding()
            insets
        }
        //the keyboard height can settle after the last insets event, so every layout pass re-checks it
        content.viewTreeObserver.addOnGlobalLayoutListener { applyKeyboardPadding() }

        //the location receiver must not stay registered after the window is gone
        dialog.setOnDismissListener {
            if (receiverRegistered) {
                runCatching { activity?.unregisterReceiver(locationReceiver) }
                receiverRegistered = false
            }
        }

        //dictation of the comment: the recognized text is added at the end of the comment
        content.findViewById<View>(R.id.ivEditCommentMic).setOnClickListener { startCommentDictation(etComment) }
        //the small "?" opens the guide about dictating a comment
        content.findViewById<View>(R.id.btnDictationGuide).setOnClickListener { showDictationGuide() }
        content.findViewById<View>(R.id.btnEditCancel).setOnClickListener { dialog.dismiss() }

        content.findViewById<View>(R.id.btnEditSave).setOnClickListener {
            val now = System.currentTimeMillis()
            //saved in the Hebrew text of the type, not in the text of the language of the app
            val selectedType = CowLabels.storedType(spType.selectedItemPosition) ?: cow.cowType
            val isCalf = spType.selectedItemPosition == CowLabels.TYPE_CALF

            cow.cowType = selectedType
            cow.isCorpse = cbCorpse.isChecked
            //the ear tag is a calf field: it is kept as it is for the other types
            if (isCalf) cow.isMarkedTag = cbEarTag.isChecked

            //a new visit is recorded only when "seen today" was just checked
            if (cbSeen.isChecked && !wasSeenToday) cow.last_seen_at = now
            if (isCalf) {
                if (cbWithMom.isChecked) {
                    cow.with_mom = cow.last_seen_at ?: now
                } else if (cow.with_mom != null && cow.with_mom == cow.last_seen_at) {
                    //"with mom" was removed from today's visit
                    cow.with_mom = null
                }
            }

            //an empty comment is saved as null, so the card shows "no comment"
            cow.comment = etComment.text.toString().trim().ifEmpty { null }

            //the health problems are saved only when they were changed in the window
            val newHealth = HealthIssues.build(selectedHealth, etHealthOther.text.toString())
            if (newHealth != cow.healthIssues) {
                cow.healthIssues = newHealth
                cow.healthIssuesChanged = true
            }

            //the location is changed only when a new one was found in the window
            val lat = newLatitude
            val lon = newLongitude
            if (lat != null && lon != null) {
                cow.latitude = lat
                cow.longitude = lon
                cow.location_updated_at = newLocationTime
            }

            dialog.dismiss()
            //loading overlay and success / failure banner, like the new cow screen
            saveEditedCow(cow, position)
        }
        dialog.show()
    }

    /**
     * the health problems window of a cow, VIEW only: the problems as rust chips. "Edit" opens the edit window, where the
     * problems are changed
     */
    private fun showHealthDialog(cow: CowDetails, position: Int) {
        val ctx = context ?: return
        if (cow.healthIssues.isEmpty()) return
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_health, null, false)
        content.findViewById<TextView>(R.id.tvHealthTitle).text =
            getString(R.string.health_dialog_title) + " " + getString(R.string.card_cow_number, cow.number?.toString() ?: "-")

        val list = content.findViewById<com.google.android.material.chip.ChipGroup>(R.id.cgHealthList)
        for (label in HealthIssues.labels(ctx, cow.healthIssues)) {
            //the same chip as in the edit window, always shown as chosen (rust) and not clickable here
            val chip = TextView(ctx, null, 0, R.style.Ww_HealthChip)
            chip.text = label
            chip.isSelected = true
            chip.isClickable = false
            list.addView(chip)
        }

        val dialog = newAppDialog(ctx, content, 0.92f, null)
        content.findViewById<View>(R.id.btnHealthClose).setOnClickListener { dialog.dismiss() }
        content.findViewById<View>(R.id.btnHealthEdit).setOnClickListener {
            dialog.dismiss()
            showEditCowDialog(cow, position)
        }
        dialog.show()
    }

    //the guide window of the comment dictation (pauses are allowed, say "end" / "סוף" to finish)
    private fun showDictationGuide() = showGuideDialog(R.string.dictation_guide_title, R.string.dictation_guide_text, false)

    //the guide window of the voice search of cows (number, year, comma between cows, end word)
    private fun showSearchGuide() = showGuideDialog(R.string.search_guide_title, R.string.search_guide_text, true)

    //one guide window for every voice feature: a parchment card with a title, the numbered steps and a close button
    private fun showGuideDialog(titleRes: Int, textRes: Int, longText: Boolean) {
        val ctx = context ?: return
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_dictation_guide, null, false)
        content.findViewById<TextView>(R.id.tvGuideTitle).setText(titleRes)
        content.findViewById<TextView>(R.id.tvGuideText).setText(textRes)
        //a long guide gets a plate with a limited height that scrolls, so the close button always stays on the screen
        if (longText) {
            //in landscape the screen is short, so the plate is lower
            val plateFraction = if (DialogSizing.isLandscape(ctx)) 0.4f else 0.55f
            content.findViewById<View>(R.id.svGuide).layoutParams.height = (ctx.resources.displayMetrics.heightPixels * plateFraction).toInt()
        }
        val dialog = newAppDialog(ctx, content, 0.92f, null)
        content.findViewById<View>(R.id.btnGuideClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    //the in-app voice dialog for a free text comment; asks for the microphone permission first when needed
    private fun startCommentDictation(target: android.widget.EditText) {
        val ctx = context ?: return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Toast.makeText(ctx, R.string.voice_error, Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            afterMicPermission = { startCommentDictation(target) }
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        VoiceInputDialog(
            context = ctx,
            prompt = getString(R.string.voice_prompt_comment),
            freeText = true,
            biasStrings = emptyList(),
            resolve = { it.trim().takeIf { text -> text.isNotEmpty() } },
            //the dialog keeps listening (pauses are allowed) until the last word of a sentence is "end" / "סוף"; the Hebrew
            //recognizer often writes the English word "end" in Hebrew letters, so that spelling ends the dictation too
            endWords = listOf("סוף", "end", "אנד"),
            onResult = { text ->
                val old = target.text.toString().trimEnd()
                target.setText(if (old.isEmpty()) text else "$old $text")
                target.setSelection(target.text.length)
            },
            onFailed = { Toast.makeText(ctx, R.string.voice_search_not_understood, Toast.LENGTH_SHORT).show() }
        ).show()
    }

    //cancel the voice search and go back to the selected filter
    private fun clearSearch() {
        searchQueries = emptyList()
        lastPosition = null
        updateFilterChips()
        applyFilter()
    }

    //the banner timer must not fire after the view is gone
    override fun onDestroyView() {
        resultHandler.removeCallbacks(hideResultRunnable)
        super.onDestroyView()
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    var lastPosition: Int?=null
    /**
     * show details of all cows
     */
    private fun showCowsDetails(cows: ArrayList<CowDetails>?) {

        val activity = activity ?: return

        if(myCowsAdapter==null) {
            myCowsAdapter=MyCowAdapter(cows, activity, object : InterOnItemClickListener {


                override fun onItemClick(item: CowDetails, type: Int, position: Int,action:Int) {
                    if(action== ACTION_UPDATE_TYPE) {
                        lastPosition=position
                        // delay to enable visible the progress bar
                        Handler(Looper.getMainLooper()).postDelayed({
                            myViewModelSupbase?.dbUpdateCowDetails(item, type)
                        }, 500)
                    }else if(action == ACTION_SHOW_LARGE_TYPE){
                        openLargePictureVideoByType(ACTION_PICTURE_KEY, item.image_url, item.last_seen_at, 0)
                    } else if (action == ACTION_SHOW_COMMENT_TYPE) {
                        //view only: the comment is shown and read aloud
                        showCommentViewDialog(item)
                    } else if (action == ACTION_SHOW_HEALTH_TYPE) {
                        //the health problems chip of the card: the list of the problems
                        showHealthDialog(item, position)
                    } else if (action == ACTION_EDIT_COW_TYPE) {
                        //the card (or its pencil) was tapped: the edit window of the cow
                        showEditCowDialog(item, position)
                    }
                }
            })
            //one column in portrait, two in landscape (R.integer.cow_list_columns)
            val columns = resources.getInteger(R.integer.cow_list_columns)
            rcShowCows?.setLayoutManager(
                if (columns > 1) androidx.recyclerview.widget.GridLayoutManager(getActivity(), columns)
                else LinearLayoutManager(getActivity())
            )
            rcShowCows?.setAdapter(myCowsAdapter)
            rcShowCows?.setHasFixedSize(true)

            //no dividing line: every cow is its own card with margins around it
        }else{
            myCowsAdapter?.setCows(cows)
            //a single changed row is refreshed only when all the cows are shown; with a filter the rows can change place or disappear
            if(currentFilter == CowFilter.ALL && lastPosition!=null) {
                myCowsAdapter?.notifyItemChanged(lastPosition!!)
            }else{
                myCowsAdapter?.notifyDataSetChanged()
            }
        }
    }

    //open fragment dialog to see a large picture or video
    private fun openLargePictureVideoByType(
        type: Int,
        imgPath: String?,
        timeInMillis: Long?,
        requestCode: Int
    ) {


        if (imgPath == null) {
            Toast.makeText(activity, resources.getString(R.string.no_photo), Toast.LENGTH_LONG)
                .show()
            return
        }

        //the screen has no constructor arguments (it survives a rotation); a video share comes back as a fragment result
        val fr=LargePictureVideoDialogFragment()
        largePictureVideoDialog = fr

        val bdl = Bundle()
        bdl.putInt(ACTION_TYPE_KEY, type)
        bdl.putString(IMAGE_PATH_KEY, imgPath)
        bdl.putString(IMAGE_TIME_KEY, timeInMillis.toString())
        fr.arguments = bdl
        val fm = activity?.supportFragmentManager
        fm?.setFragmentResultListener(LargePictureVideoDialogFragment.RESULT_SHARE_VIDEO, viewLifecycleOwner) { _, result ->
            result.getString(IMAGE_PATH_KEY)?.let { onSaveForShareVideo(it) }
        }
        fm?.let { fr.show(it, "LargePictureVideoDialogFragment") }
    }

    //placeholder for future multi-language support (values-iw / default strings already exist,
    //but no language switcher is wired up yet) - nothing to do until that feature is built
    override fun updateLanguage() {
    }

    //not currently triggered from anywhere in the UI; kept as a safe no-op to satisfy the
    //shared OnFragmentListener contract without crashing if it's ever wired up
    override fun onSaveForShareVideo(cowDetails: CowDetails) {
    }

    //called by LargePictureVideoDialogFragment when the user taps "share" on a video
    override fun onSaveForShareVideo(imgPath: String) {
        context?.let { shareVideo(imgPath, it) }
        largePictureVideoDialog?.stopProgressBar()
    }

    //not currently triggered from anywhere in the UI (the dialog's close button dismisses
    //itself directly); kept as a safe no-op to satisfy the shared OnFragmentListener contract
    override fun onBack() {
    }

}
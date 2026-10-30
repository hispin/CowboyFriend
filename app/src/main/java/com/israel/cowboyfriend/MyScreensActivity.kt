package com.israel.cowboyfriend

import android.Manifest
import android.R.attr.fragment
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.israel.cowboyfriend.UI.CattleTourFragment
import com.israel.cowboyfriend.UI.LoginFragment
import com.israel.cowboyfriend.UI.MapmobFragment
import com.israel.cowboyfriend.UI.NewCalfFragment
import com.israel.cowboyfriend.classes.OnFragmentListener
import com.israel.cowboyfriend.global.AppLanguage
import com.israel.cowboyfriend.global.MAIN_MENU_NUM_ITEM
import com.israel.cowboyfriend.global.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import io.github.jan.supabase.SupabaseClient
import java.util.Locale
import kotlin.collections.set


//index of the login tab (the last one), opened automatically when there is no session
private const val LOGIN_TAB_INDEX = 3
//index of the map tab (selected by the ranch dashboard to show the heat map)
private const val MAP_TAB_INDEX = 2

class MyScreensActivity : AppCompatActivity(){

    private lateinit var supabase: SupabaseClient
    private var collectionPagerAdapter: CollectionPagerAdapter1?=null
    private lateinit var viewPager: ViewPager2
    private var llMain: LinearLayout?=null

    //var vPager: ViewPager? = null
    private var currentItemTopMenu = 0
    //private var myViewModelSupbase: MyViewModelSupbase=viewModel()
    private var myViewModelSupbase: MyViewModelSupbase? = null
    var tabs:TabLayout?=null

    //the session is checked once when the tabs are ready (see showLoginIfNoSession), no button is needed any more

    @Composable
    override fun onCreate(savedInstanceState: Bundle?) {

        //the status bar area is dark brown (tab bar colors), so its icons must always be light, also on a light system theme.
        //edge to edge is set before super.onCreate, as the library asks
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        //the language (and the direction) of the screens is handled by AppCompat, see AppLanguage
        super.onCreate(savedInstanceState)
        //the first start: the default language is chosen and the screen is recreated in it, so this one is not opened
        if (AppLanguage.ensureChosen()) return
        setContentView(R.layout.activity_my_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.llMain)) { v, insets ->
            //ime is included so the content is pushed above the keyboard (edge-to-edge does not resize the window by itself)
            val systemBars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initViews()
        setObserver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setLocationPermission()
        } else {
            configTabs()
        }

        myViewModelSupbase = ViewModelProvider(this)[MyViewModelSupbase::class.java]

        setSupabase()

        myViewModelSupbase?.setListenerRealtimeCowDetails()//



    }

    private fun setObserver() {
        myViewModelSupbase?.pageNum?.observe(this) {
            if(it!=null) {
                //tabs?.getTabAt(it)?.select()
            }
        }
    }

    /**
     * set location permission
     */
    private fun setLocationPermission() {
        /*
     * Request location permission, so that we can get the location of the
     * device. The result of the permission request is handled by a callback,
     * onRequestPermissionsResult.
     */
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            //setExternalPermission()
            configTabs()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * call back of location permission
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                //myViewModelSupbase?.isSessionGetSupabase()
                configTabs()
                //login()
                //setExternalPermission()
            }
        }

    }




    private  fun setSupabase() {

        myViewModelSupbase?.setSupabase()

    }


    /**
     * the language button (at the end of the tab bar) shows the short name of the language in use and opens the list of
     * the languages of the app
     */
    private fun initLanguageButton() {
        val button = findViewById<android.widget.TextView>(R.id.btnLanguage)
        button.text = AppLanguage.current().shortName
        button.setOnClickListener { showLanguageDialog() }
    }

    /**
     * the language window: a row for every language of the app. choosing another language changes the texts, the
     * direction of the screens and the language of the voice, and AppCompat recreates the screen in the new language
     */
    private fun showLanguageDialog() {
        val content = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_language, null, false)
        val list = content.findViewById<LinearLayout>(R.id.llLanguages)
        val current = AppLanguage.current()

        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            com.israel.cowboyfriend.global.DialogSizing.apply(this, 0.86f)
            window?.setDimAmount(0.55f)
        }

        for (language in AppLanguage.SUPPORTED) {
            val row = android.view.LayoutInflater.from(this).inflate(R.layout.item_language_option, list, false)
            row.findViewById<android.widget.TextView>(R.id.tvLanguageName).text = language.nativeName
            row.findViewById<View>(R.id.ivLanguageCheck).visibility =
                if (language.tag == current.tag) View.VISIBLE else View.INVISIBLE
            row.setOnClickListener {
                dialog.dismiss()
                if (language.tag != current.tag) AppLanguage.set(language)
            }
            list.addView(row)
        }
        content.findViewById<View>(R.id.btnLanguageCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun initViews() {
        initLanguageButton()
        //the ranch dashboard asked to see the map (with the heat layer): the map tab is selected, the map screen reads the heat mode
        supportFragmentManager.setFragmentResultListener(
            com.israel.cowboyfriend.UI.RanchDashboardDialogFragment.RESULT_DASHBOARD_ACTION, this
        ) { _, result ->
            if (result.getString(com.israel.cowboyfriend.UI.RanchDashboardDialogFragment.RESULT_ACTION_KEY) ==
                com.israel.cowboyfriend.UI.RanchDashboardDialogFragment.ACTION_SHOW_MAP
            ) {
                tabs?.getTabAt(MAP_TAB_INDEX)?.select()
            }
        }
        //vPager = findViewById(R.id.vPager)
        viewPager = findViewById(R.id.vPager)
        viewPager.offscreenPageLimit = 1
        viewPager.setUserInputEnabled(false)
        llMain = findViewById(R.id.llMain)
    }


    /**
     * on load: when there is no session with the database, the login screen (the last tab) is opened by itself,
     * so the user does not have to find the login tab. the check waits for the saved session to be loaded
     */
    private fun showLoginIfNoSession() {
        myViewModelSupbase?.checkSession { hasSession ->
            if (!hasSession) tabs?.getTabAt(LOGIN_TAB_INDEX)?.select()
        }
    }

    private fun configTabs() {

        tabs = findViewById<TabLayout>(R.id.tab_layout)

        collectionPagerAdapter =CollectionPagerAdapter1(this)
        viewPager.adapter = collectionPagerAdapter

        //relate the tab layout to viewpager because we need to add the icons
        //haggay tabs.setupWithViewPager(viewPager)

        if(tabs==null) return

        TabLayoutMediator(tabs!!, viewPager,
            TabLayoutMediator.TabConfigurationStrategy { tab, position ->
                //            //set the title text of top menu
            when (position) {
                0 -> tab.text=resources.getString(R.string.cattle_tour)
                1 -> tab.text=resources.getString(R.string.new_cow)
                //2 -> tab.text=resources.getString(R.string.settings)
                2 -> tab.text=resources.getString(R.string.map_title)
                3 -> tab.text= resources.getString(R.string.login)
                else -> "nothing"
            }
            }).attach()


        tabs?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // This fires when a tab is clicked or selected
                viewPager.currentItem = tab.position
                val position = tab.position

                //to enable every time load cows when selected Cattle Tour
                if(collectionPagerAdapter?.fragmentMap[position] is CattleTourFragment){
                     (collectionPagerAdapter?.fragmentMap[position] as CattleTourFragment).getCowDetails()
                }

            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                // Called when a tab exits the selected state
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                // Called when the already selected tab is clicked again
            }
        })

        //posted so the view model exists (on the first start it is created after configTabs) before the session is checked
        Handler(Looper.getMainLooper()).post { showLoginIfNoSession() }


    }



    // Since this is an object collection, use a FragmentStatePagerAdapter,
// and NOT a FragmentPagerAdapter.
    class CollectionPagerAdapter1(fm: FragmentActivity) : FragmentStateAdapter(
        fm
    ) {

        val fragmentMap = mutableMapOf<Int, Fragment>()

        override fun createFragment(position: Int): Fragment {

            var fragment: Fragment? = null
            //set event of click ic_on top menu
            when (position) {
                0 -> {
                    fragment = CattleTourFragment()//MapSensorsFragment()//MapmobFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
                1 -> {
                    fragment = NewCalfFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
//                2 -> {
//                    fragment = SettingsFragment()
//                    fragment.arguments = Bundle().apply {
//                        // Our object is just an integer :-P
//                        putInt("ARG_OBJECT", position + 1)
//                    }
//                }
                2 -> {
                    fragment =MapmobFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
                3 -> {
                    fragment =LoginFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }

            }
            if(fragment!=null) {
                fragmentMap[position]=fragment
            }
            return fragment!!

        }

        override fun getItemCount(): Int = MAIN_MENU_NUM_ITEM

    }

}
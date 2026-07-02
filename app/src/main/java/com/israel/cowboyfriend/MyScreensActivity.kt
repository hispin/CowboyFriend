package com.israel.cowboyfriend

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.israel.cowboyfriend.UI.CattleTourFragment
import com.israel.cowboyfriend.UI.MapmobFragment
import com.israel.cowboyfriend.UI.NewCalfFragment
import com.israel.cowboyfriend.UI.SettingsFragment
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.NonSwipeAbleViewPager
import com.israel.cowboyfriend.global.MAIN_MENU_NUM_ITEM
import com.israel.cowboyfriend.global.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.jvm.java

//import io.github.jan.supabase.auth.Auth




//val supabase = createSupabaseClient(
//    supabaseUrl = "https://ymgsasxfgfyagppltvdy.supabase.co",
//    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InltZ3Nhc3hmZ2Z5YWdwcGx0dmR5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQzNjg1MTIsImV4cCI6MjA4OTk0NDUxMn0.FcH0j_Ec83MzcE73rV_EfH5BA5xxpNQyGBY-4Wxm1yk"
//) {
//
//    HttpResponseCache.install(Postgrest) // For database interactions
//    HttpResponseCache.install(Auth)     // For authentication
//    install(SessionSource.Storage)   // For file storage
//}

class MyScreensActivity : AppCompatActivity() {

    private lateinit var supabase: SupabaseClient
    private var collectionPagerAdapter: CollectionPagerAdapter1?=null
    private lateinit var viewPager: ViewPager2
    //var vPager: ViewPager? = null
    private var currentItemTopMenu = 0
    //private var myViewModelSupbase: MyViewModelSupbase=viewModel()
    private var myViewModelSupbase: MyViewModelSupbase? = null



    @Composable
    override fun onCreate(savedInstanceState: Bundle?) {

        setAppAsHebrow()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initViews()
        myViewModelSupbase = ViewModelProvider(this)[MyViewModelSupbase::class.java]

        setSupabase()

        myViewModelSupbase?.setListenerRealtimeCowDetails()//

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setLocationPermission()
        } else {
            login()
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
            login()
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
                login()
                //setExternalPermission()
            }
        }

    }


    private fun selsect() {
        runBlocking {
            try {

               val cows=supabase.from("CowDetails").select().decodeList<CowDetails>()
//
//               //val users=supabase.postgrest["CowDetails"].select().decodeList<CowDetails>()
               val num=10

            }catch (ex: Exception){
                print(ex)
            }
        }
    }



    private fun login() {

        myViewModelSupbase?.login (object :
          CowRepositoryCB {
            override fun onRequestResult(result: Int) {
                if(result==1){
                    myViewModelSupbase?.setListenerRealtimeCowDetails()
                    configTabs()
                }
                else
                    print("error")
            }
          }
        )

    }


    private  fun setSupabase() {

        myViewModelSupbase?.setSupabase()

    }


    /**
     * set application as hebrew
     */
    private fun setAppAsHebrow() {
        changeLocale(this, "iw")


        val res=resources

        // Change locale settings in the app.
        val dm=res.displayMetrics
        val conf=res.configuration
        conf.setLocale(Locale("iw")) // API 17+ only.

        // Use conf.locale = new Locale(...) if targeting lower versions
        res.updateConfiguration(conf, dm)


        window.decorView.layoutDirection=View.LAYOUT_DIRECTION_RTL

        val locale=Locale("iw")
        Locale.setDefault(locale)
        val config=Configuration()
        config.locale=locale
        applicationContext.resources.updateConfiguration(
            config, applicationContext.resources.displayMetrics
        )

    }

    private fun initViews() {
        //vPager = findViewById(R.id.vPager)
        viewPager = findViewById(R.id.vPager)
        viewPager.setUserInputEnabled(false)
    }

    private fun configTabs() {

        val tabs = findViewById<TabLayout>(R.id.tab_layout)

        collectionPagerAdapter = CollectionPagerAdapter1(this)
        viewPager.adapter = collectionPagerAdapter

        //relate the tab layout to viewpager because we need to add the icons
        //haggay tabs.setupWithViewPager(viewPager)

        TabLayoutMediator(tabs, viewPager,
            TabLayoutMediator.TabConfigurationStrategy { tab, position ->
                //            //set the title text of top menu
            when (position) {
                0 -> tab.text=resources.getString(R.string.new_calf)
                1 -> tab.text=resources.getString(R.string.cattle_tour)
                2 -> tab.text=resources.getString(R.string.settings)
                3 -> tab.text=resources.getString(R.string.map_title)
                else -> "nothing"
            }
            }).attach()

//        TabLayoutMediator(tabs, viewPager) { tab, position ->
//            tab.text="OBJECT ${(position + 1)}"
//        }.attach()
    }

    /////////////////////////////
    inner class CollectionPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(
        fm,
        BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
    ) {

        override fun getCount(): Int = MAIN_MENU_NUM_ITEM

        override fun getItem(position: Int): Fragment {

            var fragment: Fragment? = null
            //set event of click ic_on top menu
            when (position) {
                0 -> {
                    fragment = NewCalfFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
                1 -> {
                    fragment = CattleTourFragment()//MapSensorsFragment()//MapmobFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
                2 -> {
                    fragment = SettingsFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
                3 -> {
                    fragment =MapmobFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }

            }
            return fragment!!

        }

        override fun getPageTitle(position: Int): CharSequence {

            //set the title text of top menu
            return when (position) {
                0 -> resources.getString(R.string.new_calf)
                1 -> resources.getString(R.string.cattle_tour)
                2 -> resources.getString(R.string.settings)
                3 -> resources.getString(R.string.map_title)
                else -> "nothing"
            }

        }

    }




    //////////////////////////////


    // Since this is an object collection, use a FragmentStatePagerAdapter,
// and NOT a FragmentPagerAdapter.
    inner class CollectionPagerAdapter1(fm: FragmentActivity) : FragmentStateAdapter(
        fm
    ) {

        //override fun getCount(): Int = MAIN_MENU_NUM_ITEM

//        override fun getItem(position: Int): Fragment {
//
//            var fragment: Fragment? = null
//            //set event of click ic_on top menu
//            when (position) {
//                0 -> {
//                    fragment = NewCalfFragment()
//                    fragment.arguments = Bundle().apply {
//                        // Our object is just an integer :-P
//                        putInt("ARG_OBJECT", position + 1)
//                    }
//                }
//                1 -> {
//                    fragment = CattleTourFragment()//MapSensorsFragment()//MapmobFragment()
//                    fragment.arguments = Bundle().apply {
//                        // Our object is just an integer :-P
//                        putInt("ARG_OBJECT", position + 1)
//                    }
//                }
//                2 -> {
//                    fragment = SettingsFragment()
//                    fragment.arguments = Bundle().apply {
//                        // Our object is just an integer :-P
//                        putInt("ARG_OBJECT", position + 1)
//                    }
//                }
//                3 -> {
//                    fragment =MapmobFragment()
//                    fragment.arguments = Bundle().apply {
//                        // Our object is just an integer :-P
//                        putInt("ARG_OBJECT", position + 1)
//                    }
//                }
//
//            }
//            return fragment!!
//
//        }

//        override fun getPageTitle(position: Int): CharSequence {
//
//            //set the title text of top menu
//            return when (position) {
//                0 -> resources.getString(R.string.new_calf)
//                1 -> resources.getString(R.string.cattle_tour)
//                2 -> resources.getString(R.string.settings)
//                3 -> resources.getString(R.string.map_title)
//                else -> "nothing"
//            }
//
//        }

        override fun createFragment(position: Int): Fragment {

            var fragment: Fragment? = null
            //set event of click ic_on top menu
            when (position) {
                0 -> {
                    fragment = NewCalfFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
                1 -> {
                    fragment = CattleTourFragment()//MapSensorsFragment()//MapmobFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
                2 -> {
                    fragment = SettingsFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }
                3 -> {
                    fragment =MapmobFragment()
                    fragment.arguments = Bundle().apply {
                        // Our object is just an integer :-P
                        putInt("ARG_OBJECT", position + 1)
                    }
                }

            }
            return fragment!!

        }

        override fun getItemCount(): Int = MAIN_MENU_NUM_ITEM

    }

    fun changeLocale(context: Context, locale: String) {
        val res=context.resources
        val conf: Configuration=res.configuration
        conf.locale=Locale(locale)
        res.updateConfiguration(conf, res.displayMetrics)
    }
}
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
import com.israel.cowboyfriend.global.MAIN_MENU_NUM_ITEM
import com.israel.cowboyfriend.global.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import io.github.jan.supabase.SupabaseClient
import java.util.Locale
import kotlin.collections.set


class MyScreensActivity : AppCompatActivity(){

    private lateinit var supabase: SupabaseClient
    private var collectionPagerAdapter: CollectionPagerAdapter1?=null
    private lateinit var viewPager: ViewPager2
    private var llMain: LinearLayout?=null
    private  var tvConnectAccount: Button?=null

    //var vPager: ViewPager? = null
    private var currentItemTopMenu = 0
    //private var myViewModelSupbase: MyViewModelSupbase=viewModel()
    private var myViewModelSupbase: MyViewModelSupbase? = null
    var tabs:TabLayout?=null

    override fun onStart() {
        super.onStart()

        //check if session is get
        Handler(Looper.getMainLooper()).postDelayed({
            if(myViewModelSupbase?.isSessionGetSupabase() == true) {
                llMain?.alpha = 1.0f
                tvConnectAccount?.visibility=View.GONE
            }else{
                llMain?.alpha = 0.2f
                tvConnectAccount?.visibility=View.VISIBLE
            }
        }, 100)




    }

    @Composable
    override fun onCreate(savedInstanceState: Bundle?) {

        setAppAsHebrow()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        viewPager.offscreenPageLimit = 1
        viewPager.setUserInputEnabled(false)
        llMain = findViewById(R.id.llMain)
        tvConnectAccount= findViewById(R.id.tvConnectAccount)
        tvConnectAccount?.setOnClickListener {
            llMain?.alpha = 1.0f
            tabs?.getTabAt(3)?.select()
            tvConnectAccount?.visibility=View.GONE
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

    fun changeLocale(context: Context, locale: String) {
        val res=context.resources
        val conf: Configuration=res.configuration
        conf.locale=Locale(locale)
        res.updateConfiguration(conf, res.displayMetrics)
    }
}
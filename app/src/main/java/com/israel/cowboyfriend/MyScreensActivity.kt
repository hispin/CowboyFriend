package com.israel.cowboyfriend

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.israel.cowboyfriend.UI.CattleTourFragment
import com.israel.cowboyfriend.UI.NewCalfFragment
import com.israel.cowboyfriend.UI.SettingsFragment
import com.israel.cowboyfriend.classes.MAIN_MENU_NUM_ITEM
import com.israel.cowboyfriend.classes.NonSwipeAbleViewPager
import java.util.Locale


class MyScreensActivity : AppCompatActivity() {

    private var collectionPagerAdapter: CollectionPagerAdapter?=null
    private lateinit var viewPager: ViewPager
    var vPager: NonSwipeAbleViewPager? = null
    private var currentItemTopMenu = 0

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
        configTabs()
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
        vPager = findViewById(R.id.vPager)
        viewPager = findViewById(R.id.vPager)
    }

    private fun configTabs() {

        val tabs = findViewById<TabLayout>(R.id.tab_layout)

        collectionPagerAdapter = CollectionPagerAdapter(supportFragmentManager)
        viewPager.adapter = collectionPagerAdapter
        viewPager.offscreenPageLimit = 0
        //prevent change screen by drag
        viewPager.setOnTouchListener(object : OnTouchListener {


            override fun onTouch(v: View, event: MotionEvent): Boolean {
                return true
            }
        })

        //relate the tab layout to viewpager because we need to add the icons
        tabs.setupWithViewPager(vPager)
        tabs.getTabAt(0)?.icon = ContextCompat.getDrawable(
            this@MyScreensActivity,
            R.drawable.selected_sensor_tab
        )
        tabs.getTabAt(1)?.icon = ContextCompat.getDrawable(
            this@MyScreensActivity,
            R.drawable.selected_sensor_tab
        )
        tabs.getTabAt(2)?.icon =
            ContextCompat.getDrawable(this@MyScreensActivity, R.drawable.selected_sensor_tab)


        viewPager.currentItem = currentItemTopMenu


    }

    // Since this is an object collection, use a FragmentStatePagerAdapter,
// and NOT a FragmentPagerAdapter.
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

            }
            return fragment!!

        }

        override fun getPageTitle(position: Int): CharSequence {

            //set the title text of top menu
            return when (position) {
                0 -> resources.getString(R.string.new_calf)
                1 -> resources.getString(R.string.cattle_tour)
                2 -> resources.getString(R.string.settings)
                else -> "nothing"
            }

        }

    }

    fun changeLocale(context: Context, locale: String) {
        val res=context.resources
        val conf: Configuration=res.configuration
        conf.locale=Locale(locale)
        res.updateConfiguration(conf, res.displayMetrics)
    }
}
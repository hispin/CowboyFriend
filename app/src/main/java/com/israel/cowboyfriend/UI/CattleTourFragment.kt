package com.israel.cowboyfriend.UI

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.adapter.MyCowAdapter
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.OnFragmentListener
import com.israel.cowboyfriend.global.ACTION_PICTURE_KEY
import com.israel.cowboyfriend.global.ACTION_SHOW_LARGE_TYPE
import com.israel.cowboyfriend.global.ACTION_TYPE_KEY
import com.israel.cowboyfriend.global.ACTION_UPDATE_TYPE
import com.israel.cowboyfriend.global.IMAGE_PATH_KEY
import com.israel.cowboyfriend.global.IMAGE_TIME_KEY
import com.israel.cowboyfriend.interfaces.InterOnItemClickListener
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase

class CattleTourFragment : Fragment() ,OnFragmentListener{

    private var rcShowCows: RecyclerView? =null
    private var myViewModelSupbase: MyViewModelSupbase? = null
    private var myCowsAdapter: MyCowAdapter?=null
    private var fbRefreshCows: FloatingActionButton?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val cows =ArrayList<CowDetails>()
            cows.addAll(it)
            showCowsDetails(cows)
        }
    }


    private fun initViews(view: View) {
        rcShowCows=view.findViewById(R.id.rcShowCows)
        fbRefreshCows=view.findViewById(R.id.fbRefreshCows)
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
                    }
                }
            })
            rcShowCows?.setLayoutManager(LinearLayoutManager(getActivity()))
            rcShowCows?.setAdapter(myCowsAdapter)
            rcShowCows?.setHasFixedSize(true)
            //add dividing line between items in list
            val dividerItemDecoration=DividerItemDecoration(
                activity, LinearLayoutManager(activity).orientation
            )
            rcShowCows?.addItemDecoration(dividerItemDecoration)
        }else{
            myCowsAdapter?.setCows(cows)
            if(lastPosition!=null) {
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

        val fr=LargePictureVideoDialogFragment(this)

        //deliver selected camera to continue add data
        //val cameraStr = convertToGson(camera)
        val bdl = Bundle()
        bdl.putInt(ACTION_TYPE_KEY, type)
        bdl.putString(IMAGE_PATH_KEY, imgPath)
        bdl.putString(IMAGE_TIME_KEY, timeInMillis.toString())
        fr.arguments = bdl
        fr.setTargetFragment(this, requestCode)
        val fm = activity?.supportFragmentManager
        fm?.let { fr.show(it, "LargePictureVideoDialogFragment") }
    }

    override fun updateLanguage() {
        TODO("Not yet implemented")
    }

    override fun onSaveForShareVideo(cowDetails: CowDetails) {
        TODO("Not yet implemented")
    }

    override fun onSaveForShareVideo(imgPath: String) {
        TODO("Not yet implemented")
    }

    override fun onBack() {
        TODO("Not yet implemented")
    }

}
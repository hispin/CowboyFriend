package com.israel.cowboyfriend.UI

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.israel.cowboyfriend.DB.CowDto
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.adapter.MyCowAdapter
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.interfaces.CowRepositoryCBselect
import com.israel.cowboyfriend.interfaces.InterOnItemClickListener
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase

class CattleTourFragment : Fragment() {

    private var rcShowCows: RecyclerView? =null
    private var myViewModelSupbase: MyViewModelSupbase? = null
    private var myCowsAdapter: MyCowAdapter?=null


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
        myViewModelSupbase?._cowsDetails?.observe(requireActivity()) {
            if(it!=null) {
                showCowsDetails(it as ArrayList<CowDetails>?)
            }
        }
    }


    private fun initViews(view: View) {
        rcShowCows=view.findViewById(R.id.rcShowCows)
    }

    /**
     * get details of all cows
     */
    private fun getCowDetails(){
        myViewModelSupbase?.getCowsDetails()
    }

    private fun getCowDetails1() {

        myViewModelSupbase?.getCowsDetails(object :
            CowRepositoryCBselect {


            override fun onRequestResult(cowsDto: ArrayList<CowDto>?) {

                val cowsDetails =ArrayList<CowDetails>()

                val iterator = cowsDto?.iterator()

                while (iterator?.hasNext() == true) {
                    val item = iterator.next()
                    val cow =CowDetails(
                        number=item.number, number_mom=item.number_mom
                        , gender=item.gender, image_url=item.image_url, user_id=item.user_id
                    )
                    cowsDetails.add(cow)
                }
                showCowsDetails(cowsDetails)
            }
        })
    }

    /**
     * show details of all cows
     */
    private fun showCowsDetails(cows: ArrayList<CowDetails>?) {
        if(myCowsAdapter==null) {
            myCowsAdapter=MyCowAdapter(cows, requireActivity(), object : InterOnItemClickListener {
                public override fun onItemClick(item: CowDetails) {

                }
            })
            rcShowCows?.setLayoutManager(LinearLayoutManager(getActivity()))
            rcShowCows?.setAdapter(myCowsAdapter)
            rcShowCows?.setHasFixedSize(true)
            //add dividing line between items in list
            val dividerItemDecoration=DividerItemDecoration(
                requireActivity(), LinearLayoutManager(activity).orientation
            )
            rcShowCows?.addItemDecoration(dividerItemDecoration)
        }else{
            myCowsAdapter?.setCows(cows)
            myCowsAdapter?.notifyDataSetChanged()
        }
    }

}
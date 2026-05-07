package com.israel.cowboyfriend.UI

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.israel.cowboyfriend.DB.CowDto
import com.israel.cowboyfriend.DB.DBService
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.adapter.MyCowAdapter
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.interfaces.CowRepositoryCBselect
import com.israel.cowboyfriend.interfaces.InterOnItemClickListener

class CattleTourFragment : Fragment() {

    private var rcShowCows: RecyclerView? =null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view= inflater.inflate(R.layout.fragment_cattle_tour, container, false)

        initViews(view)

        getCowDetails()

        return view
    }

    private fun initViews(view: View) {
        rcShowCows=view.findViewById(R.id.rcShowCows)
    }

    private fun getCowDetails() {
        DBService.getInstance().getCowsDetails(object :
            CowRepositoryCBselect {
            private lateinit var myCowsAdapter: MyCowAdapter

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

            private fun showCowsDetails(cows: ArrayList<CowDetails>?) {
                myCowsAdapter=MyCowAdapter(cows,requireActivity(), object : InterOnItemClickListener {
                    public override fun onItemClick(item: CowDetails) {

                    }
                })
                rcShowCows?.setLayoutManager(LinearLayoutManager(getActivity()))
                rcShowCows?.setAdapter(myCowsAdapter)
                rcShowCows?.setHasFixedSize(true)
                //add dividing line between items in list
                val dividerItemDecoration =DividerItemDecoration(
                    requireActivity(), LinearLayoutManager(activity).orientation
                )
                rcShowCows?.addItemDecoration(dividerItemDecoration)
            }
        })
    }

}
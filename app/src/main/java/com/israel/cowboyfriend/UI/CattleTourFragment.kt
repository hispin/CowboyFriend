package com.israel.cowboyfriend.UI

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.auth.User
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

            override fun onRequestResult(cows: ArrayList<CowDetails>?) {
                showCowsDetails(cows)
            }

            private fun showCowsDetails(cows: ArrayList<CowDetails>?) {
                myCowsAdapter=MyCowAdapter(cows, object : InterOnItemClickListener {
                    public override fun onItemClick(item: CowDetails) {

                    }
                })
                rcShowCows?.setLayoutManager(LinearLayoutManager(getActivity()))
                rcShowCows?.setAdapter(myCowsAdapter)
                rcShowCows?.setHasFixedSize(true)
            }
        })
    }

}
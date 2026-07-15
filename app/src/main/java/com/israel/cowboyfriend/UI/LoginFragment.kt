package com.israel.cowboyfriend.UI

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase

class LoginFragment : Fragment() {

    var etUsername: EditText?=null
    var etPassword: EditText?=null
    var ivLogin: ImageView?=null
    private var myViewModelSupbase: MyViewModelSupbase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        initViews(view)
        myViewModelSupbase = ViewModelProvider(requireActivity())[MyViewModelSupbase::class.java]
        return view
    }

    private fun initViews(view: View) {
        etUsername = view.findViewById(R.id.etUsername)
        etPassword = view.findViewById(R.id.etPassword)
        ivLogin = view.findViewById(R.id.ivLogin)
        ivLogin?.setOnClickListener {
            myViewModelSupbase?.login(etUsername?.text.toString(),etPassword?.text.toString(),object :
                CowRepositoryCB {
                override fun onRequestResult(result: Int) {
                    if (result == 1) {
                        myViewModelSupbase?.setPageNum(0)
                        myViewModelSupbase?.setListenerRealtimeCowDetails()
                    } else
                        print("error")
                }
            })
        }

    }


}
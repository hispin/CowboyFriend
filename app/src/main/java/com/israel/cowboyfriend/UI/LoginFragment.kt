package com.israel.cowboyfriend.UI

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase

class LoginFragment : Fragment() {

    var etUsername: EditText?=null
    var etPassword: EditText?=null
    var ivLogin: ImageView?=null
    private var myViewModelSupbase: MyViewModelSupbase? = null
    var pbLogin : ProgressBar?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        myViewModelSupbase = ViewModelProvider(requireActivity())[MyViewModelSupbase::class.java]
        initViews(view)
        return view
    }

    private fun initViews(view: View) {
        etUsername = view.findViewById(R.id.etUsername)
        val user=myViewModelSupbase?.getUserName()
        if(user!=null) {
            etUsername?.setText(user)
        }
        etPassword = view.findViewById(R.id.etPassword)
        ivLogin = view.findViewById(R.id.ivLogin)
        ivLogin?.setOnClickListener {
            pbLogin?.visibility=View.VISIBLE
            myViewModelSupbase?.login(etUsername?.text.toString(),etPassword?.text.toString(),object :
                CowRepositoryCB {
                override fun onRequestResult(result: Int) {
                    pbLogin?.visibility=View.GONE
                    if (result == 1) {
                        myViewModelSupbase?.setPageNum(0)
                        myViewModelSupbase?.setListenerRealtimeCowDetails()
                        Toast.makeText(requireActivity(),getString(R.string.success_login),Toast.LENGTH_LONG).show()
                    } else {
                        print("error")
                        Toast.makeText(requireActivity(),getString(R.string.login_failed),Toast.LENGTH_LONG).show()
                    }
                }
            })
        }

        pbLogin = view.findViewById(R.id.pbLogin)



    }


}
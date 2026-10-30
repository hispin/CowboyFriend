package com.israel.cowboyfriend.UI

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.UI.helper.FarmNameLabelBinder
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase

class LoginFragment : Fragment() {

    var etUsername: EditText?=null
    var etPassword: EditText?=null
    var ivLogin: ImageView?=null
    private var myViewModelSupbase: MyViewModelSupbase? = null
    var pbLogin : ProgressBar?=null
    //eye icon that reveals / hides the typed password
    private var ivTogglePassword: ImageView?=null
    private var passwordVisible = false
    //validation messages shown under the fields
    private var tvUserError: TextView?=null
    private var tvPasswordError: TextView?=null
    //farm name label above "Welcome": the shared binder shows the name read from the UsersInfo table after login
    private var farmNameBinder: FarmNameLabelBinder? = null
    //fixed result banner (login success / failure) instead of a Toast
    private var resultBanner: View?=null
    private var ivResultIcon: ImageView?=null
    private var tvResultText: TextView?=null
    private val resultHandler = Handler(Looper.getMainLooper())
    private val hideResultRunnable = Runnable { hideResult() }

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

    //the banner timer must not fire after the view is gone
    override fun onDestroyView() {
        resultHandler.removeCallbacks(hideResultRunnable)
        super.onDestroyView()
    }

    //show or hide the typed password and update the eye icon and its description to match
    private fun setPasswordVisible(visible: Boolean) {
        val et = etPassword ?: return
        passwordVisible = visible
        //changing the input type resets the font and the cursor, so both are restored afterwards
        val typeface = et.typeface
        val selection = et.selectionEnd
        et.inputType = InputType.TYPE_CLASS_TEXT or
            if (visible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
        et.typeface = typeface
        et.setSelection(selection.coerceAtLeast(0))
        ivTogglePassword?.setImageResource(if (visible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed)
        ivTogglePassword?.contentDescription = getString(if (visible) R.string.hide_password else R.string.show_password)
    }

    /**
     * show (messageRes != null) or clear the validation message of a field: rust border on the field,
     * message under it, and a short shake so the user notices which field is wrong
     */
    private fun showFieldError(field: EditText?, errorView: TextView?, messageRes: Int?) {
        if (field == null || errorView == null) return
        //setting a background resets the padding to the drawable's padding, so the field padding is restored
        val start = field.paddingStart
        val end = field.paddingEnd
        field.setBackgroundResource(
            if (messageRes != null) R.drawable.shape_ww_edittext_error_bg else R.drawable.shape_ww_edittext_bg
        )
        field.setPaddingRelative(start, field.paddingTop, end, field.paddingBottom)
        if (messageRes != null) {
            errorView.setText(messageRes)
            errorView.visibility = View.VISIBLE
            ObjectAnimator.ofFloat(field, View.TRANSLATION_X, 0f, -14f, 14f, -10f, 10f, -5f, 5f, 0f)
                .setDuration(350).start()
        } else {
            errorView.visibility = View.GONE
        }
    }

    /**
     * check the fields before logging in: both are required and the user name must be an email address.
     * returns true when the input can be sent to the server
     */
    private fun validateInput(): Boolean {
        val user = etUsername?.text?.toString()?.trim().orEmpty()
        val password = etPassword?.text?.toString().orEmpty()

        val userError = when {
            user.isEmpty() -> R.string.error_user_empty
            !Patterns.EMAIL_ADDRESS.matcher(user).matches() -> R.string.error_email_invalid
            else -> null
        }
        val passwordError = if (password.isEmpty()) R.string.error_password_empty else null

        showFieldError(etUsername, tvUserError, userError)
        showFieldError(etPassword, tvPasswordError, passwordError)

        //put the cursor in the first wrong field
        if (userError != null) etUsername?.requestFocus() else if (passwordError != null) etPassword?.requestFocus()
        return userError == null && passwordError == null
    }

    //fixed message at the bottom of the screen: slides in and hides itself after a few seconds
    private fun showResult(success: Boolean, messageRes: Int) {
        val banner = resultBanner ?: return
        ivResultIcon?.setImageResource(if (success) R.drawable.ic_banner_success else R.drawable.ic_banner_fail)
        tvResultText?.setText(messageRes)
        resultHandler.removeCallbacks(hideResultRunnable)
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = 80 * resources.displayMetrics.density
        banner.animate().alpha(1f).translationY(0f).setDuration(220).start()
        resultHandler.postDelayed(hideResultRunnable, RESULT_BANNER_MILLIS)
    }

    private fun hideResult() {
        resultHandler.removeCallbacks(hideResultRunnable)
        val banner = resultBanner ?: return
        banner.animate().alpha(0f).translationY(80 * resources.displayMetrics.density).setDuration(200)
            .withEndAction { banner.visibility = View.GONE }.start()
    }


    private fun initViews(view: View) {
        etUsername = view.findViewById(R.id.etUsername)
        val user=myViewModelSupbase?.getUserName()
        if(user!=null) {
            etUsername?.setText(user)
        }
        etPassword = view.findViewById(R.id.etPassword)
        //validation messages: cleared as soon as the user edits the field again
        tvUserError = view.findViewById(R.id.tvUserError)
        tvPasswordError = view.findViewById(R.id.tvPasswordError)
        etUsername?.doAfterTextChanged { if (tvUserError?.visibility == View.VISIBLE) showFieldError(etUsername, tvUserError, null) }
        etPassword?.doAfterTextChanged { if (tvPasswordError?.visibility == View.VISIBLE) showFieldError(etPassword, tvPasswordError, null) }
        resultBanner = view.findViewById(R.id.resultBanner)
        ivResultIcon = view.findViewById(R.id.ivResultIcon)
        tvResultText = view.findViewById(R.id.tvResultText)
        resultBanner?.setOnClickListener { hideResult() }
        //tapping the eye toggles between a hidden and a visible password
        ivTogglePassword = view.findViewById(R.id.ivTogglePassword)
        ivTogglePassword?.setOnClickListener { setPasswordVisible(!passwordVisible) }
        pbLogin = view.findViewById(R.id.pbLogin)
        //the farm name label is handled by the shared binder (the same class the new cow screen uses)
        val farmLabel = view.findViewById<TextView>(R.id.tvFarmName)
        farmNameBinder = myViewModelSupbase?.let { FarmNameLabelBinder(farmLabel, it) }
        farmNameBinder?.bind(viewLifecycleOwner)
        //already logged in (e.g. the app was reopened): show the farm name right away
        farmNameBinder?.load()
        ivLogin = view.findViewById(R.id.ivLogin)
        ivLogin?.setOnClickListener {
            //nothing is sent to the server while a field is empty or the user name is not an email
            if (!validateInput()) return@setOnClickListener
            //close the keyboard so the loading indicator and the result banner are fully visible
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
            pbLogin?.visibility=View.VISIBLE
            //block a second tap while the login request is running
            ivLogin?.isEnabled = false
            myViewModelSupbase?.login(etUsername?.text.toString().trim(),etPassword?.text.toString(),object :
                CowRepositoryCB {
                override fun onRequestResult(result: Int) {
                    if (!isAdded) return
                    pbLogin?.visibility=View.GONE
                    ivLogin?.isEnabled = true
                    if (result == 1) {
                        myViewModelSupbase?.setPageNum(0)
                        myViewModelSupbase?.setListenerRealtimeCowDetails()
                        //the farm name of this user replaces the placeholder above the welcome text
                        farmNameBinder?.load()
                        showResult(true, R.string.success_login)
                    } else {
                        print("error")
                        showResult(false, R.string.login_failed)
                    }
                }
            })
        }
    }

    companion object {
        //how long the login result banner stays on screen
        private const val RESULT_BANNER_MILLIS = 3200L
    }
}

package com.israel.cowboyfriend.UI

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.global.DialogSizing
import com.israel.cowboyfriend.global.shareImageFile
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import java.io.File

/**
 * The cow picture window (opened by the small camera of a cow card). it opens the camera right away; after the picture was
 * taken it shows the picture and the user decides: replace the picture of the cow (the new one is saved for the cow and the
 * previous one is deleted from the table and from the storage), share the picture, or take it again. a successful replace
 * closes the window and tells the opener with a fragment result ([RESULT_KEY]). it has no constructor arguments (the cow is
 * in the arguments), so it survives a rotation.
 */
class CowPhotoDialogFragment : DialogFragment() {

    private var viewModel: MyViewModelSupbase? = null
    private var cowId = -1

    //the file the camera is writing to right now, and the file of the picture that was taken (null = no picture yet)
    private var pendingFile: File? = null
    private var photoFile: File? = null
    //true while the new picture is uploaded: the buttons are disabled and the window cannot be closed
    private var busy = false

    private var ivPhoto: ImageView? = null
    private var llEmpty: View? = null
    private var ivBusy: ImageView? = null
    private var tvNotice: TextView? = null
    private var btnTake: View? = null
    private var llActions: View? = null
    private var btnReplace: View? = null
    private var btnShare: View? = null
    private var btnRetake: View? = null
    private var ibClose: View? = null

    //the camera app writes the picture to the file of the uri; the answer only tells if it did
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken -> onCameraResult(taken) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cowId = arguments?.getInt(ARG_COW_ID, -1) ?: -1
        //after a rotation (or while the camera app was open) the files are found again by their paths
        savedInstanceState?.getString(STATE_PENDING)?.let { pendingFile = File(it) }
        savedInstanceState?.getString(STATE_PHOTO)?.let { photoFile = File(it).takeIf { file -> file.exists() } }
        deleteOldPictures()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_cow_photo, container, false)
        viewModel = ViewModelProvider(requireActivity())[MyViewModelSupbase::class.java]

        ivPhoto = view.findViewById(R.id.ivCowPhoto)
        llEmpty = view.findViewById(R.id.llCowPhotoEmpty)
        ivBusy = view.findViewById(R.id.ivCowPhotoBusy)
        tvNotice = view.findViewById(R.id.tvCowPhotoNotice)
        btnTake = view.findViewById(R.id.btnCowPhotoTake)
        llActions = view.findViewById(R.id.llCowPhotoActions)
        btnReplace = view.findViewById(R.id.btnCowPhotoReplace)
        btnShare = view.findViewById(R.id.btnCowPhotoShare)
        btnRetake = view.findViewById(R.id.btnCowPhotoRetake)
        ibClose = view.findViewById(R.id.ibCowPhotoClose)

        val number = arguments?.getInt(ARG_COW_NUMBER, -1) ?: -1
        view.findViewById<TextView>(R.id.tvCowPhotoTitle).text =
            getString(R.string.cow_photo_title, if (number >= 0) number.toString() else "-")

        ibClose?.setOnClickListener { dismiss() }
        //the empty picture area and the take button open the camera; "retake" does the same when there is a picture
        view.findViewById<View>(R.id.flCowPhotoArea).setOnClickListener { if (!busy && photoFile == null) openCamera() }
        btnTake?.setOnClickListener { openCamera() }
        btnRetake?.setOnClickListener { openCamera() }
        btnShare?.setOnClickListener { photoFile?.let { shareImageFile(it, requireActivity()) } }
        btnReplace?.setOnClickListener { replacePhoto() }

        showPhoto()
        updateState()

        //first time: the camera opens by itself (after a rotation the window just shows what it had)
        if (savedInstanceState == null) {
            view.post { if (isAdded && photoFile == null) openCamera() }
        }
        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingFile?.let { outState.putString(STATE_PENDING, it.absolutePath) }
        photoFile?.let { outState.putString(STATE_PHOTO, it.absolutePath) }
    }

    //opens the camera app; the picture is written to a new file of the cache folder (nothing is added to the gallery)
    private fun openCamera() {
        if (busy) return
        val ctx = context ?: return
        hideNotice()
        val folder = File(ctx.cacheDir, PHOTOS_FOLDER).apply { mkdirs() }
        val file = File(folder, "cow_${cowId}_${System.currentTimeMillis()}.jpg")
        pendingFile = file
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.contentprovider", file)
        try {
            takePicture.launch(uri)
        } catch (ex: ActivityNotFoundException) {
            pendingFile = null
            showNotice(R.string.cow_photo_no_camera)
        }
    }

    //the camera closed: a taken picture replaces the previous one of this window, a cancel keeps what the window had
    private fun onCameraResult(taken: Boolean) {
        val file = pendingFile
        pendingFile = null
        if (taken && file != null && file.length() > 0) {
            photoFile?.delete()
            photoFile = file
            showPhoto()
        } else {
            file?.delete()
        }
        updateState()
    }

    //shows the taken picture (the camera app saves the rotation inside the file, Glide applies it)
    private fun showPhoto() {
        val file = photoFile
        val image = ivPhoto ?: return
        if (file == null) {
            image.setImageDrawable(null)
        } else {
            Glide.with(this).load(file).signature(ObjectKey(file.absolutePath)).into(image)
        }
    }

    //picture or empty camera, the buttons that fit the state, and the busy horseshoe
    private fun updateState() {
        val hasPhoto = photoFile != null
        ivPhoto?.visibility = if (hasPhoto) View.VISIBLE else View.GONE
        llEmpty?.visibility = if (!hasPhoto && !busy) View.VISIBLE else View.GONE
        btnTake?.visibility = if (hasPhoto) View.GONE else View.VISIBLE
        llActions?.visibility = if (hasPhoto) View.VISIBLE else View.GONE

        ivBusy?.visibility = if (busy) View.VISIBLE else View.GONE
        (ivBusy?.drawable as? Animatable)?.let { if (busy) it.start() else it.stop() }
        //nothing can be pressed (and the window cannot be closed) while the picture is uploaded
        val enabled = !busy
        for (button in listOf(btnTake, btnReplace, btnShare, btnRetake, ibClose)) {
            button?.isEnabled = enabled
        }
        llActions?.alpha = if (enabled) 1f else 0.5f
        isCancelable = enabled
    }

    /**
     * saves the taken picture as the picture of the cow. the old picture is deleted from the table and from the storage by
     * the view model. success = the window closes and the list tells the user; a failure stays here, so it can be tried again
     */
    private fun replacePhoto() {
        val file = photoFile ?: return
        if (busy || cowId < 0) return
        hideNotice()
        busy = true
        updateState()
        viewModel?.dbReplaceCowImage(cowId, file) { result ->
            if (!isAdded) return@dbReplaceCowImage
            busy = false
            if (result.ok) {
                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                    putBoolean(RESULT_OLD_DELETED, result.oldDeleted)
                    //the opener needs the cow to count the picture as a visit of an open tour
                    putInt(RESULT_COW_ID, cowId)
                })
                dismiss()
            } else {
                updateState()
                showNotice(R.string.cow_photo_replace_failed)
            }
        }
    }

    private fun showNotice(messageRes: Int) {
        tvNotice?.setText(messageRes)
        tvNotice?.visibility = View.VISIBLE
    }

    private fun hideNotice() {
        tvNotice?.visibility = View.GONE
    }

    //pictures that were taken more than a day ago are not needed any more (a shared picture may be read by the other app for a while)
    private fun deleteOldPictures() {
        val folder = File(requireContext().cacheDir, PHOTOS_FOLDER)
        val limit = System.currentTimeMillis() - ONE_DAY_MILLIS
        folder.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        //a card on a dimmed screen; in landscape the window is wider (the picture is beside the buttons)
        dialog?.let { dialog ->
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setDimAmount(0.55f)
            if (DialogSizing.isLandscape(dialog.context)) {
                DialogSizing.apply(dialog, 0.86f, maxWidthDp = 700)
            } else {
                DialogSizing.apply(dialog, 0.94f)
            }
        }
    }

    override fun onDestroyView() {
        ivPhoto = null
        llEmpty = null
        ivBusy = null
        tvNotice = null
        btnTake = null
        llActions = null
        btnReplace = null
        btnShare = null
        btnRetake = null
        ibClose = null
        super.onDestroyView()
    }

    companion object {
        //fragment result: the cow picture was replaced (RESULT_OLD_DELETED tells if the previous one was deleted from the storage)
        const val RESULT_KEY = "cow_photo_result"
        const val RESULT_OLD_DELETED = "cow_photo_old_deleted"
        //the id of the cow whose picture was replaced
        const val RESULT_COW_ID = "cow_photo_cow_id_result"

        private const val ARG_COW_ID = "cow_photo_cow_id"
        private const val ARG_COW_NUMBER = "cow_photo_cow_number"
        private const val STATE_PENDING = "cow_photo_pending"
        private const val STATE_PHOTO = "cow_photo_photo"
        private const val PHOTOS_FOLDER = "cow_photos"
        private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

        //the window for [cow]: only its id and number travel in the arguments
        fun newInstance(cow: CowDetails) = CowPhotoDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_COW_ID, cow.id ?: -1)
                putInt(ARG_COW_NUMBER, cow.number ?: -1)
            }
        }
    }
}

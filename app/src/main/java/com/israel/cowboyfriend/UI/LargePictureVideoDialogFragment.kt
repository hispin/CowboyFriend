package com.israel.cowboyfriend.UI

//import com.sensoguard.hunter.fragments.LargePictureVideoDialogFragment.CheckDownloadComplete.Factory.isComplete
import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.DialogFragment
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.OnFragmentListener
import com.israel.cowboyfriend.classes.TouchImageView
import com.israel.cowboyfriend.classes.VideoManager
import com.israel.cowboyfriend.global.ACTION_PICTURE_KEY
import com.israel.cowboyfriend.global.ACTION_TYPE_KEY
import com.israel.cowboyfriend.global.ACTION_VIDEO_KEY
import com.israel.cowboyfriend.global.IMAGE_PATH_KEY
import com.israel.cowboyfriend.global.IMAGE_TIME_KEY
import com.israel.cowboyfriend.global.UIHelper.Companion.showToast
import com.israel.cowboyfriend.global.saveImageInGallery
import com.israel.cowboyfriend.global.saveVideoInGallery
import com.israel.cowboyfriend.global.shareImage
import java.lang.Exception
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class LargePictureVideoDialogFragment(var listener1: OnFragmentListener?) : DialogFragment(),
    VideoManager.Callback {

    private var timeImage: String? = null
    private var imgPath: String? = null
    private var actionType: Int? = null
    private var ibClose: AppCompatImageButton? = null
    private var ivMyVideo: PlayerView? = null
    private var ivMyCaptureImage: TouchImageView? = null
    private var ibLargeImgShare: AppCompatImageButton? = null
    private var ibSaveLargeImgShare: AppCompatImageButton? = null
    private var pbLoadPhoto: ProgressBar? = null
    var videoManager: VideoManager? = null
    var videoFileId: Long? = null


//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        // Force landscape when the fragment is opened
//        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//    }
//
//    override fun onDetach() {
//        super.onDetach()
//        // Reset back to sensor/default when leaving the fragment
//        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(
            R.layout.fragment_large_picture_video,
            container,
            false
        )


        initViews(view)

        //set listener to download complete for sharing
//        isComplete.observe(requireActivity()) {
//            stopProgressBar()
//        }


        val bundle = arguments
        actionType = bundle?.getInt(ACTION_TYPE_KEY, -1)
        imgPath = bundle?.getString(IMAGE_PATH_KEY, null)
        timeImage = bundle?.getString(IMAGE_TIME_KEY, null)
        if (actionType == ACTION_PICTURE_KEY) {
            showPicture(imgPath)
        } else if (actionType == ACTION_VIDEO_KEY) {
            ibSaveLargeImgShare?.visibility = View.VISIBLE
            ibLargeImgShare?.visibility = View.VISIBLE
        }
        // Inflate the layout for this fragment
        return view
    }


    override fun onPause() {
        super.onPause()
        if (actionType == ACTION_VIDEO_KEY) {
            videoManager?.releasePlayer()
        }
    }

    //show static picture
    private fun showPicture(path: String?) {
        ivMyVideo?.visibility = View.GONE
        ivMyCaptureImage?.visibility = View.VISIBLE

        if (path == null)
            return

        showPicture(path, ivMyCaptureImage)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setLayoutLandscape()
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setLayoutPortrait()
        }
    }

    private fun setLayoutPortrait() {
    }

    private fun setLayoutLandscape() {
    }

    //show the picture by glide
    private fun showPicture(imgPath: String?, ivMyCaptureImage: AppCompatImageView?) {
        ivMyCaptureImage?.let {
            Glide.with(requireActivity()).load(imgPath).listener(object :
                RequestListener<Drawable> {

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Toast.makeText(context, "error loading image", Toast.LENGTH_LONG).show()
                    return false
                }

            }).into(it)
        }
    }


    private fun initViews(view: View?) {
        ibClose = view?.findViewById(R.id.ibClose)
        ibClose?.setOnClickListener {
            dismiss()
        }

        ivMyVideo = view?.findViewById(R.id.ivMyVideo)
        ivMyCaptureImage = view?.findViewById(R.id.ivMyCaptureImage)
        ibLargeImgShare = view?.findViewById(R.id.ibLargeImgShare)
        ibLargeImgShare?.setOnClickListener {
            if (actionType == ACTION_PICTURE_KEY) {
                val bitmap = (ivMyCaptureImage?.drawable as BitmapDrawable).bitmap
                try {
                    bitmap?.let { shareImage(it, requireActivity()) }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    showToast(activity, resources.getString(R.string.error))
                }
            } else if (actionType == ACTION_VIDEO_KEY) {

                if (imgPath != null) {
                    pbLoadPhoto?.visibility = View.VISIBLE
                    listener1?.onSaveForShareVideo(imgPath!!)
                }
            }
        }

        ibSaveLargeImgShare = view?.findViewById(R.id.ibSaveLargeImgShare)
        ibSaveLargeImgShare?.setOnClickListener {
            if (actionType == ACTION_PICTURE_KEY) {
                val bitmap = (ivMyCaptureImage?.drawable as BitmapDrawable).bitmap

                //save image in gallery
                val executor: ExecutorService=Executors.newSingleThreadExecutor()
                val handler=Handler(Looper.getMainLooper())

                executor.execute {
                    //Background work here
                    val result:Boolean? = timeImage?.let { it1 ->
                        saveImageInGallery(bitmap,"$it1.jpg")
                    }


                    handler.post {
                        if (result != null && result) {
                            context?.resources?.getString(R.string.save_file_success)
                                ?.let { it1 ->
                                    showToast(
                                        context, it1
                                    )
                                }
                        } else {
                            context?.resources?.getString(R.string.save_file_failed)
                                ?.let { it1 ->
                                    showToast(
                                        context, it1
                                    )
                                }
                        }

                    }
                }

            } else if (actionType == ACTION_VIDEO_KEY) {
                Thread {
                    imgPath?.let { it1 ->
                        val result = saveVideoInGallery(requireActivity(), it1)
                        var msg = ""
                        activity?.runOnUiThread {
                            if (result) {
                                msg = context?.resources?.getString(R.string.save_file_success)!!
                                showToast(context, msg)
                            } else {
                                msg = context?.resources?.getString(R.string.save_file_failed)!!
                                showToast(context, msg)
                            }
                        }
                    }
                }.start()
            }

        }
        pbLoadPhoto = view?.findViewById(R.id.pbLoadPhoto)
    }

    private fun disableOrientation() {
        if (activity == null) {
            return
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setLayoutPortrait()
    }

    private fun enableOrientation() {
        if (activity == null) {
            return
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    override fun onStop() {
        disableOrientation()
        super.onStop()
    }

    override fun onStart() {
        enableOrientation()

        //configuration the fragment dialog as full screen
        val dialog: Dialog? = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.window?.setLayout(width, height)
        }

        super.onStart()

        if (activity != null && actionType == ACTION_VIDEO_KEY) {
            //pbLoadPhoto?.visibility = View.VISIBLE
            ivMyVideo?.visibility = View.VISIBLE
            ivMyCaptureImage?.visibility = View.GONE
            imgPath?.let {
                videoManager =VideoManager(this)
                videoManager?.initializePlayer(ivMyVideo, requireActivity(), it)
            }
        }
    }

    override fun stopProgressBar() {
        pbLoadPhoto?.visibility = View.GONE
    }


}
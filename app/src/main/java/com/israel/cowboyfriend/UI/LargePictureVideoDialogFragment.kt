package com.israel.cowboyfriend.UI

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.TouchImageView
import com.israel.cowboyfriend.classes.VideoManager
import com.israel.cowboyfriend.global.ACTION_PICTURE_KEY
import com.israel.cowboyfriend.global.ACTION_TYPE_KEY
import com.israel.cowboyfriend.global.ACTION_VIDEO_KEY
import com.israel.cowboyfriend.global.IMAGE_PATH_KEY
import com.israel.cowboyfriend.global.shareImage
import com.israel.cowboyfriend.global.shareToWhatsApp

/**
 * The large picture (or video) screen, in the style of the app: a dark background, the zoomable picture, a brass close
 * button and a brass share button (there is no download button). the screen follows the rotation of the phone.
 * it has no constructor arguments, so Android can recreate it after a rotation; the video share is reported to the
 * opener with a fragment result ([RESULT_SHARE_VIDEO])
 */
class LargePictureVideoDialogFragment : DialogFragment(), VideoManager.Callback {

    private var imgPath: String? = null
    private var actionType: Int? = null
    private var ibClose: View? = null
    private var ivMyVideo: PlayerView? = null
    private var ivMyCaptureImage: TouchImageView? = null
    private var ibLargeImgShare: View? = null
    private var pbLoadPhoto: ImageView? = null
    private var tvPhotoError: TextView? = null
    private var tvNotice: TextView? = null
    private var ibWhatsApp: View? = null
    var videoManager: VideoManager? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_large_picture_video, container, false)
        initViews(view)
        keepButtonsInsideTheScreen(view)

        actionType = arguments?.getInt(ACTION_TYPE_KEY, -1)
        imgPath = arguments?.getString(IMAGE_PATH_KEY, null)
        if (actionType == ACTION_PICTURE_KEY) {
            showPicture(imgPath)
        }
        return view
    }

    override fun onPause() {
        super.onPause()
        if (actionType == ACTION_VIDEO_KEY) {
            videoManager?.releasePlayer()
        }
    }

    //show the static picture: the horseshoe spins until the picture is loaded
    private fun showPicture(path: String?) {
        ivMyVideo?.visibility = View.GONE
        ivMyCaptureImage?.visibility = View.VISIBLE
        if (path == null) return
        val image = ivMyCaptureImage ?: return

        setLoading(true)
        Glide.with(requireActivity()).load(path).listener(object : RequestListener<Drawable> {
            override fun onResourceReady(
                resource: Drawable, model: Any, target: Target<Drawable>?,
                dataSource: DataSource, isFirstResource: Boolean
            ): Boolean {
                setLoading(false)
                return false
            }

            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
            ): Boolean {
                setLoading(false)
                tvPhotoError?.visibility = View.VISIBLE
                return false
            }
        }).into(image)
    }

    //the spinning horseshoe in the middle of the screen
    private fun setLoading(loading: Boolean) {
        val spinner = pbLoadPhoto ?: return
        spinner.visibility = if (loading) View.VISIBLE else View.GONE
        val animation = spinner.drawable as? Animatable
        if (loading) animation?.start() else animation?.stop()
    }

    private fun initViews(view: View) {
        ibClose = view.findViewById(R.id.ibClose)
        ibClose?.setOnClickListener { dismiss() }

        ivMyVideo = view.findViewById(R.id.ivMyVideo)
        ivMyCaptureImage = view.findViewById(R.id.ivMyCaptureImage)
        pbLoadPhoto = view.findViewById(R.id.pbLoadPhoto)
        tvPhotoError = view.findViewById(R.id.tvPhotoError)
        tvNotice = view.findViewById(R.id.tvNotice)
        ibWhatsApp = view.findViewById(R.id.ibLargeImgWhatsApp)
        ibWhatsApp?.setOnClickListener { onWhatsAppClicked() }

        //share: a picture goes to the share sheet of the phone; a video link is shared by the opener of this screen
        ibLargeImgShare = view.findViewById(R.id.ibLargeImgShare)
        ibLargeImgShare?.setOnClickListener {
            if (actionType == ACTION_PICTURE_KEY) {
                sharePicture()
            } else if (actionType == ACTION_VIDEO_KEY) {
                imgPath?.let {
                    setLoading(true)
                    parentFragmentManager.setFragmentResult(RESULT_SHARE_VIDEO, Bundle().apply { putString(IMAGE_PATH_KEY, it) })
                }
            }
        }
    }

    /**
     * the screen is drawn under the status bar, the navigation bar and the camera cutout (in landscape the cutout and the
     * navigation bar are on the sides), so the two buttons move away from them by the size of those bars
     */
    private fun keepButtonsInsideTheScreen(root: View) {
        val density = resources.displayMetrics.density
        val close = ibClose ?: return
        val share = ibLargeImgShare ?: return
        val whatsApp = ibWhatsApp ?: return
        val baseSide = (18 * density).toInt()
        val baseTop = (14 * density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            //the close button is at the start of the screen and the share button at its end: the left / right side depends on the direction
            val rtl = root.layoutDirection == View.LAYOUT_DIRECTION_RTL
            (close.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = baseTop + bars.top
                marginStart = baseSide + if (rtl) bars.right else bars.left
            }
            (share.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = baseTop + bars.top
                marginEnd = baseSide + if (rtl) bars.left else bars.right
            }
            //the WhatsApp button is next to the share button (54dp wide + 12dp gap)
            (whatsApp.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = baseTop + bars.top
                marginEnd = baseSide + (66 * density).toInt() + if (rtl) bars.left else bars.right
            }
            close.requestLayout()
            share.requestLayout()
            whatsApp.requestLayout()
            insets
        }
    }

    //the shown picture as a bitmap (null while it is still loading or when it failed to load)
    private fun shownBitmap(): Bitmap? {
        val drawable = ivMyCaptureImage?.drawable ?: return null
        return try {
            drawable.toBitmap()
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    //shares the shown picture with the share sheet of the phone
    private fun sharePicture() {
        val bitmap = shownBitmap() ?: return
        shareImage(bitmap, requireActivity())
    }

    //shares the picture (or the link of the video) straight to WhatsApp; tells the user when WhatsApp is not installed
    private fun onWhatsAppClicked() {
        val activity = requireActivity()
        val shared = if (actionType == ACTION_PICTURE_KEY) {
            val bitmap = shownBitmap() ?: return
            shareToWhatsApp(activity, bitmap = bitmap)
        } else {
            val link = imgPath ?: return
            shareToWhatsApp(activity, text = link)
        }
        if (!shared) showNotice(R.string.whatsapp_not_installed)
    }

    //a short message at the bottom of the screen that hides itself
    private fun showNotice(messageRes: Int) {
        val notice = tvNotice ?: return
        notice.setText(messageRes)
        notice.visibility = View.VISIBLE
        notice.removeCallbacks(hideNotice)
        notice.postDelayed(hideNotice, 2800)
    }

    private val hideNotice = Runnable { tvNotice?.visibility = View.GONE }

    override fun onStart() {
        super.onStart()
        //the dialog covers the whole screen, in any orientation, on a dark background
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#1B130D")))
            //draw also under the camera cutout (in landscape it is on the side), the buttons are moved away from it
            attributes = attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        if (activity != null && actionType == ACTION_VIDEO_KEY) {
            ivMyVideo?.visibility = View.VISIBLE
            ivMyCaptureImage?.visibility = View.GONE
            imgPath?.let {
                videoManager = VideoManager(this)
                videoManager?.initializePlayer(ivMyVideo, requireActivity(), it)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            //the picture is the content: the back button closes it
            setCanceledOnTouchOutside(false)
        }
    }

    //the video is ready / the share finished: the spinner stops
    override fun stopProgressBar() {
        setLoading(false)
    }

    companion object {
        //fragment result: the user wants to share a video (the path of the video is in IMAGE_PATH_KEY)
        const val RESULT_SHARE_VIDEO = "large_media_share_video"
    }
}

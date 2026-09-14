package com.israel.cowboyfriend.classes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ImageStorageManager {
    companion object {
        fun saveToInternalStorage(
            context: Context,
            bitmapImage: Bitmap,
            imageFileName: String
        ): String {
            context.openFileOutput(imageFileName, Context.MODE_PRIVATE).use { fos ->
                bitmapImage.compress(Bitmap.CompressFormat.PNG, 25, fos)
            }
            return context.filesDir.absolutePath
        }

        fun getImageFromInternalStorage(context: Context, imageFileName: String): Bitmap? {
            val directory = context.filesDir
            val file = File(directory, imageFileName)
            return BitmapFactory.decodeStream(FileInputStream(file))
        }

        fun deleteImageFromInternalStorage(context: Context, imageFileName: String): Boolean {
            val dir = context.filesDir
            val file = File(dir, imageFileName)
            return file.delete()
        }

        //save the image in external and then you can share it
        fun getImageUriByBitmap(bmp: Bitmap, context: Context?): Uri? {
            var bmpUri: Uri? = null
            try {
                val file = File(
                    context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "share_image_" + System.currentTimeMillis() + ".png"
                )
                val out = FileOutputStream(file)
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.close()
                //bmpUri = Uri.fromFile(file)

//              For sdk 24 and up, if you need to get the Uri of a file outside your app storage you have this error.
//              @eranda.del solutions let you change the policy to allow this and it works fine.
                bmpUri = context?.let {
                    FileProvider.getUriForFile(
                        it,
                        "${it.packageName}.contentprovider", //(use your app signature + ".provider" )
                        file
                    )
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return bmpUri
        }
    }
}
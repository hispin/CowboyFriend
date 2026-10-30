package com.israel.cowboyfriend.global

import android.app.Activity
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.view.View
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Makes a PDF of a view that is already shown (for example the content of the ranch dashboard) and shares it. the whole
 * view is drawn on one page as tall as the view, so nothing is cut. the file is saved in the cache folder of the app and given
 * to the other app through the FileProvider (see res/xml/file_paths.xml).
 */
object PdfExporter {

    /**
     * draws [content] into a PDF file named [fileName] (without the extension); returns the file, or null when the view
     * has no size yet or the file could not be written
     */
    fun createPdf(activity: Activity, content: View, fileName: String): File? {
        if (content.width <= 0 || content.height <= 0) return null
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(content.width, content.height, 1).create()
            val page = document.startPage(pageInfo)
            content.draw(page.canvas)
            document.finishPage(page)

            val folder = File(activity.cacheDir, "shared").apply { mkdirs() }
            val file = File(folder, "$fileName.pdf")
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    //opens the share sheet of the phone for a PDF file
    fun sharePdf(activity: Activity, file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.contentprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}

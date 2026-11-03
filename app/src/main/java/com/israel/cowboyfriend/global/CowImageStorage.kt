package com.israel.cowboyfriend.global

/**
 * The pictures of the cows live in the storage bucket [COW_IMAGES_BUCKET]; the table CowDetails keeps the public url of the
 * picture (column image_url). this turns such a url back into the path of the file inside the bucket, which is what the
 * storage needs to delete it
 */
object CowImageStorage {

    /**
     * the path of the picture inside the bucket ("<uuid>/image.jpg"), or null when [url] is empty or is not a picture of
     * the bucket of the cows (a picture from another place is never deleted)
     */
    fun pathInBucket(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val prefix = "$baseUrl$COW_IMAGES_BUCKET/"
        if (!url.startsWith(prefix)) return null
        //a query part (for example a cache token) is not a part of the file path
        return url.removePrefix(prefix).substringBefore('?').takeIf { it.isNotBlank() }
    }

    //the public url that is saved in the table for the file [path] of the bucket
    fun publicUrl(path: String): String = "$baseUrl$COW_IMAGES_BUCKET/$path"
}

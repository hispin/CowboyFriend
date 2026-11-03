package com.israel.cowboyfriend

import com.israel.cowboyfriend.global.COW_IMAGES_BUCKET
import com.israel.cowboyfriend.global.CowImageStorage
import com.israel.cowboyfriend.global.baseUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

//the path that is deleted from the storage is taken from the url saved in the table: it must be exactly the file of the cow picture
class CowImageStorageTest {

    @Test
    fun pathOfAStorageUrlIsTheFileInsideTheBucket() {
        val url = "$baseUrl$COW_IMAGES_BUCKET/3f2c-uuid/image.jpg"
        assertEquals("3f2c-uuid/image.jpg", CowImageStorage.pathInBucket(url))
    }

    @Test
    fun publicUrlAndPathAreInverse() {
        assertEquals("a/b.jpg", CowImageStorage.pathInBucket(CowImageStorage.publicUrl("a/b.jpg")))
    }

    @Test
    fun queryPartIsNotPartOfThePath() {
        val url = "$baseUrl$COW_IMAGES_BUCKET/abc/image.jpg?t=123"
        assertEquals("abc/image.jpg", CowImageStorage.pathInBucket(url))
    }

    @Test
    fun otherPlacesAreNeverDeleted() {
        assertNull(CowImageStorage.pathInBucket(null))
        assertNull(CowImageStorage.pathInBucket(""))
        assertNull(CowImageStorage.pathInBucket("https://example.com/cow.jpg"))
        //another bucket of the same storage
        assertNull(CowImageStorage.pathInBucket("${baseUrl}OtherBucket/abc/image.jpg"))
        //only the bucket, no file
        assertNull(CowImageStorage.pathInBucket("$baseUrl$COW_IMAGES_BUCKET/"))
    }
}

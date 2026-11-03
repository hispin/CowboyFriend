package com.israel.cowboyfriend.classes

/**
 * The answer of replacing the picture of a cow: [ok] = the new picture is saved for the cow, and [oldDeleted] = the previous
 * picture is gone from the storage (false when it could not be deleted, for example because the storage does not allow it)
 */
class CowImageReplaceResult(val ok: Boolean, val oldDeleted: Boolean)

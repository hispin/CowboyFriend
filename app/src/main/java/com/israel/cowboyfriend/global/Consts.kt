package com.israel.cowboyfriend.global


   const val baseUrl= "https://ymgsasxfgfyagppltvdy.supabase.co/storage/v1/object/public/"
   const val GET_CURRENT_SINGLE_LOCATION_KEY = "handle.get.current.single.location"
   const val CURRENT_LOCATION_LATITUDE = "currentLocationLatitude"
   const val CURRENT_LOCATION_LONGITUDE = "currentLocationLongitude"
   const val SHARED_PREF_FILE_NAME = "CowboyPref"
   const val CURRENT_LATITUDE_PREF = "currentLatitudePref"
   const val CURRENT_LONGTUDE_PREF = "currentLongtudePref"
   const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0
   const val MAIN_MENU_NUM_ITEM = 4
   const val NONE_TYPE= -1
   const val CORPSE_TYPE= 0
   const val LAST_SEEN_AT_TYPE= 1
   const val TYPE_COW = 2
   //update of the ear tag mark of a cow (checkbox + save on the cow card)
   const val MARKED_TAG_TYPE = 3
   //update of the comment of a cow (editing window of the cow card)
   const val COMMENT_TYPE = 4
   //update of all the editable fields of the edit window in one request
   const val ALL_FIELDS_TYPE = 5
   const val ACTION_UPDATE_TYPE = 0
   const val ACTION_SHOW_LARGE_TYPE = 1
   //tap on the comment mark of a cow card: open the comment window and read it aloud
   const val ACTION_SHOW_COMMENT_TYPE = 2
   //tap on a cow card (or its pencil badge): open the edit window of the cow
   const val ACTION_EDIT_COW_TYPE = 3
   //tap on the health problems chip of a cow card: open the window with the list of the problems
   const val ACTION_SHOW_HEALTH_TYPE = 4
   const val ACTION_TYPE_KEY = "actionType"
   const val ACTION_PICTURE_KEY = 1
   const val ACTION_VIDEO_KEY = 2
const val IMAGE_PATH_KEY = "imagePathKey"
const val IMAGE_TIME_KEY = "imageTimeKey"


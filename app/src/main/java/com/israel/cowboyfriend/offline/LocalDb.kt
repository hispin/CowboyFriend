package com.israel.cowboyfriend.offline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.israel.cowboyfriend.classes.CowDetails
import org.json.JSONObject

/**
 * The database on the phone (offline work). it keeps four things:
 * - the cows (the last list that was read from the server, with the changes that were made on the phone),
 * - the outbox: the changes that were made without a network and wait to be sent, in the order they were made,
 * - small values (the logged in user, the open tour, the farm name, the map elements, ...),
 * - the numbers that a new cow got on the server in place of its number on the phone (a new cow has a negative number
 *   until it is sent).
 * it is plain SQLite (no annotation processing), the cows are kept as json text (see [CowJson])
 */
class LocalDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "cowboy_offline.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT)")
        db.execSQL("CREATE TABLE cows (id INTEGER PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE outbox (seq INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, payload TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, failed INTEGER NOT NULL DEFAULT 0, last_error TEXT)"
        )
        db.execSQL("CREATE TABLE id_map (local_id INTEGER PRIMARY KEY, server_id INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        //version 1 only
    }

    // ----- small values -----

    fun kvGet(key: String): String? = readableDatabase.rawQuery("SELECT v FROM kv WHERE k = ?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    fun kvSet(key: String, value: String?) {
        if (value == null) {
            writableDatabase.delete("kv", "k = ?", arrayOf(key))
        } else {
            writableDatabase.insertWithOnConflict("kv", null, ContentValues().apply {
                put("k", key)
                put("v", value)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ----- cows -----

    fun allCows(): List<CowDetails> = readableDatabase.rawQuery("SELECT json FROM cows", null).use { cursor ->
        val list = ArrayList<CowDetails>()
        while (cursor.moveToNext()) {
            runCatching { CowJson.fromJson(JSONObject(cursor.getString(0))) }.onSuccess { list.add(it) }
        }
        list
    }

    fun getCow(id: Int): CowDetails? = readableDatabase.rawQuery("SELECT json FROM cows WHERE id = ?", arrayOf(id.toString())).use {
        if (it.moveToFirst()) runCatching { CowJson.fromJson(JSONObject(it.getString(0))) }.getOrNull() else null
    }

    /**
     * saves one cow. a cow that still has the number it got on the phone but was already sent is saved under its number on the
     * server (the number of the cow object is changed too), so no second copy of it appears
     */
    fun saveCow(cow: CowDetails) {
        val id = cow.id ?: return
        val serverId = if (id < 0) serverIdOf(id) else null
        if (serverId != null) cow.id = serverId
        val finalId = cow.id ?: return
        writableDatabase.insertWithOnConflict("cows", null, ContentValues().apply {
            put("id", finalId)
            put("json", CowJson.toJson(cow).toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** replaces all the cows with the list that was just read from the server (only when no change waits to be sent) */
    fun replaceAllCows(cows: List<CowDetails>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cows", null, null)
            for (cow in cows) {
                val id = cow.id ?: continue
                db.insert("cows", null, ContentValues().apply {
                    put("id", id)
                    put("json", CowJson.toJson(cow).toString())
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** the cow that was added on the phone was sent: its row moves to the number it got on the server */
    fun replaceCowId(localId: Int, serverId: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("id_map", null, ContentValues().apply {
                put("local_id", localId)
                put("server_id", serverId)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            val json = readableDatabase.rawQuery("SELECT json FROM cows WHERE id = ?", arrayOf(localId.toString())).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            db.delete("cows", "id = ?", arrayOf(localId.toString()))
            if (json != null) {
                val obj = JSONObject(json)
                obj.put("id", serverId)
                db.insertWithOnConflict("cows", null, ContentValues().apply {
                    put("id", serverId)
                    put("json", obj.toString())
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** the number on the server of a cow that was added on the phone (null while it was not sent yet) */
    fun serverIdOf(localId: Int): Int? = readableDatabase.rawQuery(
        "SELECT server_id FROM id_map WHERE local_id = ?", arrayOf(localId.toString())
    ).use { if (it.moveToFirst()) it.getInt(0) else null }

    /** the number to use on the server: a number from the server stays as it is, a number of the phone is mapped (null = not sent yet) */
    fun resolveId(id: Int): Int? = if (id >= 0) id else serverIdOf(id)

    /** a new number for a cow that is added on the phone: negative, so it never meets a number of the server */
    @Synchronized
    fun nextLocalId(): Int {
        val next = (kvGet("local_id_counter")?.toIntOrNull() ?: 0) - 1
        kvSet("local_id_counter", next.toString())
        return next
    }

    // ----- the outbox -----

    class Op(val seq: Long, val type: String, val payload: JSONObject, val attempts: Int, val failed: Boolean)

    /** puts a change at the end of the list of the changes to send; returns its number */
    fun enqueue(type: String, payload: JSONObject): Long = writableDatabase.insert("outbox", null, ContentValues().apply {
        put("type", type)
        put("payload", payload.toString())
        put("created_at", System.currentTimeMillis())
    })

    /** the changes to send, in the order they were made (the ones that gave up are included only when [withFailed]) */
    fun ops(withFailed: Boolean = false): List<Op> = readableDatabase.rawQuery(
        "SELECT seq, type, payload, attempts, failed FROM outbox" + (if (withFailed) "" else " WHERE failed = 0") + " ORDER BY seq", null
    ).use { cursor ->
        val list = ArrayList<Op>()
        while (cursor.moveToNext()) {
            list.add(Op(cursor.getLong(0), cursor.getString(1), JSONObject(cursor.getString(2)), cursor.getInt(3), cursor.getInt(4) != 0))
        }
        list
    }

    fun updatePayload(seq: Long, payload: JSONObject) {
        writableDatabase.update("outbox", ContentValues().apply { put("payload", payload.toString()) }, "seq = ?", arrayOf(seq.toString()))
    }

    fun deleteOp(seq: Long) {
        writableDatabase.delete("outbox", "seq = ?", arrayOf(seq.toString()))
    }

    /** an attempt to send the change failed: it is counted, and after [maxAttempts] the change gives up (it stays in the list as failed) */
    fun markAttempt(seq: Long, error: String?, maxAttempts: Int) {
        writableDatabase.execSQL(
            "UPDATE outbox SET attempts = attempts + 1, last_error = ?, failed = CASE WHEN attempts + 1 >= ? THEN 1 ELSE 0 END WHERE seq = ?",
            arrayOf<Any?>(error?.take(300), maxAttempts, seq)
        )
    }

    /** the changes that gave up are tried again (the user asked for it) */
    fun retryFailed() {
        writableDatabase.execSQL("UPDATE outbox SET failed = 0, attempts = 0 WHERE failed = 1")
    }

    /** removes the pending changes of one type (a newer change replaces them) */
    fun deleteOpsOfType(type: String) {
        writableDatabase.delete("outbox", "type = ? AND failed = 0", arrayOf(type))
    }

    /** how many changes wait to be sent */
    fun pendingCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM outbox WHERE failed = 0", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    /** how many changes gave up */
    fun failedCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM outbox WHERE failed = 1", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    /** another user logged in on this phone: nothing of the previous user stays (not the cows, not the changes that wait) */
    fun wipe() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("kv", null, null)
            db.delete("cows", null, null)
            db.delete("outbox", null, null)
            db.delete("id_map", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

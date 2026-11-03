package com.israel.cowboyfriend.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * What the screens know about the work without a network: [online], how many changes wait to be sent, whether they are being
 * sent now, and how many gave up. it also owns the database of the phone, the network monitor and the sync, so the screens
 * (through the view model) and the work manager use the same ones.
 */
data class SyncStatus(val online: Boolean = true, val pending: Int = 0, val failed: Int = 0, val syncing: Boolean = false)

object OfflineHub {

    private lateinit var appContext: Context
    private var started = false

    lateinit var db: LocalDb
        private set
    lateinit var network: NetworkMonitor
        private set
    lateinit var engine: SyncEngine
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    //the sync changed the data of the phone (a new cow got its number, a picture was sent): the screens read the cows again
    val dataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    //everything that waited was sent (true): the screens read the server for the fresh data
    val syncFinished = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /** starts the offline work once (safe to call again) */
    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        db = LocalDb(appContext)
        network = NetworkMonitor(appContext)
        engine = SyncEngine(db)
        publishStatus()
        //a network that comes back: the waiting changes are sent
        scope.launch {
            network.online.collect { online ->
                publishStatus()
                if (online && db.pendingCount() > 0) engine.sync()
            }
        }
        //changes that waited since the last time the app ran
        if (db.pendingCount() > 0) requestSync()
    }

    /** updates what the screens see; [syncing] is given by the sync while it works */
    fun publishStatus(syncing: Boolean? = null) {
        val old = _status.value
        _status.value = SyncStatus(
            online = network.online.value,
            pending = db.pendingCount(),
            failed = db.failedCount(),
            syncing = syncing ?: old.syncing
        )
    }

    /**
     * a change was made: it is sent now when there is a network, and the work manager is asked to send it later (also when the
     * app is closed) when there is none or when the sending fails
     */
    fun requestSync() {
        publishStatus()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork("offline-sync", ExistingWorkPolicy.KEEP, request)
        if (network.online.value) scope.launch { engine.sync() }
    }
}

/** the work manager job that sends the waiting changes when the phone has a network */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        OfflineHub.init(applicationContext)
        val allSent = OfflineHub.engine.sync()
        //changes that gave up are not tried again by the job; the others are tried again with a delay
        return if (allSent || OfflineHub.db.pendingCount() == 0) Result.success() else Result.retry()
    }
}

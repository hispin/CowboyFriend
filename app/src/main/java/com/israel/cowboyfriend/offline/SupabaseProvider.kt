package com.israel.cowboyfriend.offline

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlin.time.Duration.Companion.seconds

/**
 * The one connection to the server of the app. the screens (through the view model) and the sync that runs in the background
 * use the same one, so the saved login is refreshed by one client only
 */
object SupabaseProvider {

    @Volatile
    private var client: SupabaseClient? = null

    /**
     * waits until the user is logged in on the server (after a long time without a network the saved login is refreshed first);
     * false when that does not happen in [timeoutMillis] (no network)
     */
    suspend fun awaitLogin(timeoutMillis: Long = 25_000L): Boolean = withTimeoutOrNull(timeoutMillis) {
        get().auth.sessionStatus.first { it is SessionStatus.Authenticated }
    } != null

    fun get(): SupabaseClient = client ?: synchronized(this) {
        client ?: createSupabaseClient(
            supabaseUrl = "https://ymgsasxfgfyagppltvdy.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InltZ3Nhc3hmZ2Z5YWdwcGx0dmR5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQzNjg1MTIsImV4cCI6MjA4OTk0NDUxMn0.FcH0j_Ec83MzcE73rV_EfH5BA5xxpNQyGBY-4Wxm1yk"
        ) {
            install(Postgrest) // For database interactions
            install(Auth)
            install(Storage)
            install(Realtime) {
                heartbeatInterval = 30.seconds
                reconnectDelay = 5.seconds
            }
        }.also { client = it }
    }
}

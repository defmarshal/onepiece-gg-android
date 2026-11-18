package gg.opgg.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

/**
 * Whether the device has a usable network.
 *
 * The app has no way of telling "you are offline" from "the server is down" from
 * "your session expired", because onepiece.gg answers a dead token with HTTP
 * 200 and an empty body. But a genuine network failure is unambiguous, and
 * saying so in plain words beats printing a DNS exception at the user.
 *
 * `ACCESS_NETWORK_STATE` is already in the manifest, and this is what it is for.
 */
object NetworkStatus {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * True when [this] was a network failure rather than a server complaint.
     *
     * Walks the cause chain, because the exception the caller sees is Ktor's
     * wrapper and the useful one - `UnknownHostException` - is underneath it.
     */
    fun Throwable.isNetworkFailure(): Boolean {
        var e: Throwable? = this
        var depth = 0
        while (e != null && depth < 8) {
            if (e is IOException) return true
            e = e.cause
            depth++
        }
        return false
    }

    /**
     * True when the request ran out of time rather than being refused.
     *
     * Checked before [isNetworkFailure] because socket timeouts are IOExceptions
     * too; "you're offline" would be wrong for a server that accepted the
     * connection and then stalled.
     */
    fun Throwable.isTimeout(): Boolean {
        var e: Throwable? = this
        var depth = 0
        while (e != null && depth < 8) {
            if (e is java.net.SocketTimeoutException) return true
            if (e::class.simpleName?.contains("Timeout", ignoreCase = true) == true) return true
            if (e.message?.contains("timeout", ignoreCase = true) == true) return true
            e = e.cause
            depth++
        }
        return false
    }

    /**
     * Plain words for a network failure.
     *
     * Returns null for anything that is not a network problem, so the caller
     * falls back to the real message - a server that says "Binder not found"
     * needs to say exactly that.
     */
    fun friendlyMessage(e: Throwable): String? = when {
        e.isTimeout() -> "onepiece.gg took too long to respond. Try again."
        e.isNetworkFailure() -> "You're offline. Connect to the internet and try again."
        else -> null
    }

    /** True only for a genuine connectivity failure, not a stalled server. */
    fun isOfflineFailure(e: Throwable): Boolean = e.isNetworkFailure() && !e.isTimeout()
}

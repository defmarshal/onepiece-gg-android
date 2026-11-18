package gg.opgg.android.data

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.Dns
import okhttp3.HttpUrl
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS for a device that advertises AAAA records but has no global IPv6 route.
 *
 * `InetAddress.getAllByName` queries A and AAAA together, and on such a device
 * the AAAA failure propagates as UnknownHostException instead of falling back to
 * the A record - so every request failed while `curl` (which uses
 * AI_ADDRCONFIG, and therefore suppresses AAAA) worked fine.
 *
 * So: prefer IPv4, and retry, because a resolver SERVFAIL is often transient.
 */
object PreferIpv4Dns : Dns {

    private const val TAG = "OpggDns"

    override fun lookup(hostname: String): List<InetAddress> {
        var lastError: Exception? = null
        var attempt = 0
        while (attempt < 3) {
            try {
                val all = InetAddress.getAllByName(hostname)
                val v4 = all.filterNot { it is Inet6Address }
                val ordered = v4 + all.filter { it is Inet6Address }
                if (ordered.isNotEmpty()) return ordered
                lastError = UnknownHostException("$hostname resolved to no addresses")
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "lookup($hostname) attempt ${attempt + 1} failed: ${e.message}")
            }
            attempt++
        }
        throw UnknownHostException("Could not resolve $hostname: ${lastError?.message}")
    }
}

/**
 * Retries a request a few times on transient network faults.
 *
 * A single DNS blip used to leave the card database uncached for the whole
 * session, because the background refresh runs once at startup and gives up.
 */
suspend fun <T> retryingNetwork(
    attempts: Int = 3,
    initialDelayMs: Long = 400,
    block: suspend () -> T,
): T {
    var delayMs = initialDelayMs
    repeat(attempts - 1) { i ->
        try {
            return block()
        } catch (e: Exception) {
            val status = (e as? io.ktor.client.plugins.HttpRequestTimeoutException) != null
            Log.w("OpggNet", "attempt ${i + 1}/$attempts failed (${e::class.simpleName}: ${e.message}), retrying in ${delayMs}ms")
            delay(delayMs)
            delayMs *= 2
            if (status) return@repeat
        }
    }
    return block()
}

/** Exposed for tests/diagnostics. */
fun hostOf(url: String): String? = runCatching { HttpUrl.Companion.run { url.toHttpUrlOrNull() }?.host }.getOrNull()

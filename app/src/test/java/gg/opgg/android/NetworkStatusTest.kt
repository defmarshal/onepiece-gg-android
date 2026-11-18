package gg.opgg.android

import gg.opgg.android.data.NetworkStatus
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling "offline" apart from "the server stalled". Both arrive as exceptions,
 * and a socket timeout is an IOException too, so the order of the checks is the
 * whole point: a stuck server must not be reported as no connectivity.
 */
class NetworkStatusTest {

    @Test
    fun aSocketTimeoutIsNotReportedAsOffline() {
        val e = SocketTimeoutException("timeout")
        assertTrue(NetworkStatus.run { e.isTimeout() })
        assertFalse(NetworkStatus.isOfflineFailure(e))
        assertEquals(
            "onepiece.gg took too long to respond. Try again.",
            NetworkStatus.friendlyMessage(e),
        )
    }

    @Test
    fun anUnresolvableHostIsOffline() {
        val e = IOException("wrapper", UnknownHostException("api.dotgg.gg"))
        assertFalse(NetworkStatus.run { e.isTimeout() })
        assertTrue(NetworkStatus.isOfflineFailure(e))
        assertTrue(NetworkStatus.friendlyMessage(e)!!.startsWith("You're offline"))
    }

    @Test
    fun aTimeoutWrappedInAnotherExceptionStillCounts() {
        val e = IOException("wrapper", SocketTimeoutException())
        assertTrue(NetworkStatus.run { e.isTimeout() })
    }

    @Test
    fun aServerComplaintKeepsItsOwnMessage() {
        assertNull(NetworkStatus.friendlyMessage(IllegalStateException("Binder not found")))
    }
}

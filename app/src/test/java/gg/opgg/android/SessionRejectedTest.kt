package gg.opgg.android

import gg.opgg.android.data.AppJson
import gg.opgg.android.data.BinderListResponse
import gg.opgg.android.data.isSessionRejected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rejected sessions.
 *
 * onepiece.gg answers a dead token with **HTTP 200** and a plausible empty
 * result, not a 401. So the app has to recognise the rejection from the
 * payload, and the failure mode when it does not is bad: the collection came
 * back empty, and the app told the user to go and create a binder they already
 * had.
 */
class SessionRejectedTest {

    @Test
    fun theBinderListErrorBodyIsNotDecodedAsAnEmptyList() {
        // This exact body is what the server returns for a rejected session.
        val json = """{"error":true,"error_text":"Not authorized!"}"""
        val parsed = AppJson.decodeFromString(BinderListResponse.serializer(), json)
        assertTrue("the error must be visible, not dropped", parsed.error)
        assertEquals("Not authorized!", parsed.error_text)
        assertTrue("and there must be no binder list", parsed.binders.isNullOrEmpty())
    }

    @Test
    fun aNormalBinderListStillDecodes() {
        val json = """
            {"binders":[{"id":7,"name":"Binder","type":"user","sort":1,"count":42,"color":"#fff"}]}
        """.trimIndent()
        val parsed = AppJson.decodeFromString(BinderListResponse.serializer(), json)
        assertFalse(parsed.error)
        val binder = parsed.binders!!.single()
        assertEquals(7L, binder.id)
        assertEquals("Binder", binder.name)
        assertEquals(42, binder.count)
    }

    @Test
    fun anEmptyBinderListIsNotAnError() {
        val parsed = AppJson.decodeFromString(
            BinderListResponse.serializer(),
            """{"binders":[]}""",
        )
        assertFalse(parsed.error)
        assertTrue(parsed.binders!!.isEmpty())
    }

    @Test
    fun aZeroServerUserIdWithARealStoredOneMeansRejected() {
        assertTrue(isSessionRejected(storedUserId = 158557252, serverUserId = 0L))
    }

    @Test
    fun aMissingServerUserIdAlsoMeansRejected() {
        assertTrue(isSessionRejected(storedUserId = 158557252, serverUserId = null))
    }

    @Test
    fun aMatchingServerUserIdIsFine() {
        assertFalse(isSessionRejected(storedUserId = 158557252, serverUserId = 158557252))
    }

    @Test
    fun withoutAStoredUserIdThereIsNothingToReject() {
        // Signed out: the server reporting 0 is correct, not a rejection.
        assertFalse(isSessionRejected(storedUserId = 0L, serverUserId = 0L))
        assertFalse(isSessionRejected(storedUserId = 0L, serverUserId = null))
    }
}

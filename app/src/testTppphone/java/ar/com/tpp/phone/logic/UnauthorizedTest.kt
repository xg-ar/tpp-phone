package ar.com.tpp.phone.logic

import ar.com.tpp.phone.prov.LineStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UnauthorizedTest {
    @Test
    fun replacedShowsLineMoved() =
        assertEquals(UnauthorizedAction.SHOW_LINE_MOVED, onUnauthorized(LineStatus.REPLACED, 0))

    @Test
    fun activeRetriesWhileFlexisipCacheExpires() {
        assertEquals(UnauthorizedAction.RETRY_LATER, onUnauthorized(LineStatus.ACTIVE, 0))
        assertEquals(UnauthorizedAction.RETRY_LATER, onUnauthorized(LineStatus.ACTIVE, 1))
        assertEquals(UnauthorizedAction.NOTHING, onUnauthorized(LineStatus.ACTIVE, 2))
    }

    @Test
    fun unknownOrUnauthorizedStaysRed() {
        assertEquals(UnauthorizedAction.NOTHING, onUnauthorized(LineStatus.UNAUTHORIZED, 0))
        assertEquals(UnauthorizedAction.NOTHING, onUnauthorized(LineStatus.UNKNOWN, 0))
    }
}

package ar.com.tpp.phone.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckRowsTest {
    private val allGood = Readiness(
        registered = RegState.OK,
        internet = true,
        notifications = true,
        microphone = true,
        fullScreenIntent = true,
        batteryUnrestricted = true
    )

    @Test
    fun allChecksInScreenOrder() {
        assertEquals(
            listOf(Check.INTERNET, Check.ACCOUNT, Check.NOTIFICATIONS, Check.MICROPHONE, Check.FULL_SCREEN, Check.BATTERY),
            allGood.checkRows().map { it.check }
        )
        assertEquals(setOf(CheckState.OK), allGood.checkRows().map { it.state }.toSet())
    }

    @Test
    fun problemsAreMarked() {
        val rows = allGood.copy(notifications = false, batteryUnrestricted = false).checkRows().associate {
            it.check to it.state
        }
        assertEquals(CheckState.PROBLEM, rows[Check.NOTIFICATIONS])
        assertEquals(CheckState.RECOMMENDED, rows[Check.BATTERY])
        assertEquals(CheckState.OK, rows[Check.MICROPHONE])
    }

    @Test
    fun accountWaitsForInternet() {
        val rows = allGood.copy(internet = false, registered = RegState.FAILED).checkRows().associate { it.check to it.state }
        assertEquals(CheckState.PROBLEM, rows[Check.INTERNET])
        assertEquals(CheckState.PENDING, rows[Check.ACCOUNT])
    }

    @Test
    fun connectingAccountIsPending() {
        val rows = allGood.copy(registered = RegState.CONNECTING).checkRows().associate { it.check to it.state }
        assertEquals(CheckState.PENDING, rows[Check.ACCOUNT])
    }

    @Test
    fun problemRowsMatchProblemCount() {
        val r = allGood.copy(internet = false, registered = RegState.NONE, microphone = false, batteryUnrestricted = false)
        assertEquals(r.problemCount(), r.checkRows().count { it.state == CheckState.PROBLEM })
    }
}

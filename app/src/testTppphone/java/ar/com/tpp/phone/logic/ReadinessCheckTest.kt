package ar.com.tpp.phone.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessCheckTest {
    private val allGood = Readiness(
        registered = RegState.OK,
        internet = true,
        notifications = true,
        microphone = true,
        fullScreenIntent = true,
        batteryUnrestricted = true
    )

    @Test
    fun allGoodIsReady() {
        assertEquals(Status.READY, allGood.status())
        assertEquals(0, allGood.problemCount())
    }

    @Test
    fun connectingWithEverythingElseOk() {
        assertEquals(Status.CONNECTING, allGood.copy(registered = RegState.CONNECTING).status())
    }

    @Test
    fun batteryOptimizationIsOnlyARecommendation() {
        val r = allGood.copy(batteryUnrestricted = false)
        assertEquals(Status.READY, r.status())
        assertEquals(0, r.problemCount())
    }

    @Test
    fun twoThingsToFix() {
        val r = allGood.copy(notifications = false, microphone = false)
        assertEquals(Status.PROBLEM, r.status())
        assertEquals(2, r.problemCount())
    }

    @Test
    fun missingMicrophoneIsAProblem() {
        val r = allGood.copy(microphone = false)
        assertEquals(Status.PROBLEM, r.status())
        assertEquals(1, r.problemCount())
    }

    @Test
    fun failedRegistrationIsAProblem() {
        val r = allGood.copy(registered = RegState.FAILED)
        assertEquals(Status.PROBLEM, r.status())
        assertEquals(1, r.problemCount())
    }

    @Test
    fun noInternetCountsOnceEvenIfNotRegistered() {
        val r = allGood.copy(internet = false, registered = RegState.CONNECTING)
        assertEquals(Status.PROBLEM, r.status())
        assertEquals(1, r.problemCount())
    }
}

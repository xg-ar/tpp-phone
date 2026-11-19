package ar.com.tpp.phone.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeToAnswerTest {
    @Test
    fun swipeUpMostOfTheWayAnswers() {
        assertEquals(SwipeOutcome.ANSWER, swipeOutcome(startY = 500f, currentY = 360f, trackHeight = 200f))
    }

    @Test
    fun swipeUpHalfWayDoesNothing() {
        assertEquals(SwipeOutcome.NONE, swipeOutcome(startY = 500f, currentY = 400f, trackHeight = 200f))
    }

    @Test
    fun swipeDownMostOfTheWayDeclines() {
        assertEquals(SwipeOutcome.DECLINE, swipeOutcome(startY = 500f, currentY = 640f, trackHeight = 200f))
    }

    @Test
    fun emptyTrackDoesNothing() {
        assertEquals(SwipeOutcome.NONE, swipeOutcome(startY = 500f, currentY = 100f, trackHeight = 0f))
    }
}

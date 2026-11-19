package ar.com.tpp.phone.logic

import ar.com.tpp.phone.prov.ActivationResult
import ar.com.tpp.phone.prov.StartResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupFlowTest {
    @Test
    fun gateLetsOneRequestThrough() {
        val gate = RequestGate()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
    }

    @Test
    fun helpOfferedWhenTheUserCannotFixItAlone() {
        assertTrue(offersHelp(StartResult.Failed))
        assertTrue(offersHelp(StartResult.RateLimited))
        assertTrue(offersHelp(ActivationResult.Failed))
        assertTrue(offersHelp(ActivationResult.ActivationFailed))
        assertTrue(offersHelp(ActivationResult.Expired))
        assertTrue(offersHelp(ActivationResult.RateLimited))
    }

    @Test
    fun noHelpForATypo() {
        assertFalse(offersHelp(StartResult.InvalidNumber))
        assertFalse(offersHelp(ActivationResult.InvalidNumber))
        assertFalse(offersHelp(ActivationResult.WrongCode(3)))
    }
}

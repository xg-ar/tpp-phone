package ar.com.tpp.phone

import org.junit.Assert.assertTrue
import org.junit.Test
import org.linphone.BuildConfig

class SmokeTest {
    @Test
    fun tppFlavorEnablesTppUi() {
        assertTrue(BuildConfig.TPP_UI)
    }
}

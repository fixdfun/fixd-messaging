package app.fixd.messaging

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun trueIsTrue() = assertEquals(true, true)
    @Test fun appPackage() = assertEquals("app.fixd.messaging", BuildConfig.APPLICATION_ID.removeSuffix(".debug"))
}

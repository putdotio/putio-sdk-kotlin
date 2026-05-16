package io.putdotio.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class OkResponseTest {
    @Test
    fun `preserves one argument Java constructor`() {
        val constructor = OkResponse::class.java.getConstructor(String::class.java)

        assertEquals(OkResponse("OK"), constructor.newInstance("OK"))
    }
}

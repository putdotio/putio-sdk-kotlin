package io.putdotio.sdk

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkResponseTest {
    @Test
    fun `preserves one argument Java constructor`() {
        val constructor = OkResponse::class.java.getConstructor(String::class.java)

        assertEquals(OkResponse("OK"), constructor.newInstance("OK"))
    }

    @Test
    fun `a bare OK acknowledgement decodes with no cursor or skipped count`() {
        assertEquals(OkResponse("OK"), Json.decodeFromString(OkResponse.serializer(), """{"status":"OK"}"""))
    }

    @Test
    fun `non-OK acknowledgement cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { OkResponse("ERROR") }
    }
}

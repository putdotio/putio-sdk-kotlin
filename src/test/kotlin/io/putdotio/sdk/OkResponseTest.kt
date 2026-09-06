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
    fun `valid acknowledgement roundtrip preserves optional fields`() {
        val response = OkResponse("OK", cursor = "cursor", skipped = 2)

        val encoded = Json.encodeToString(OkResponse.serializer(), response)

        assertEquals(response, Json.decodeFromString(OkResponse.serializer(), encoded))
    }

    @Test
    fun `non-OK acknowledgement cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { OkResponse("ERROR") }
    }
}

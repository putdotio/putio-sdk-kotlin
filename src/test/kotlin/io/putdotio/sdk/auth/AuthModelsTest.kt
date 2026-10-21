package io.putdotio.sdk.auth

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `token validation keeps nullable backend fields nullable`() {
        val nullFields =
            json.decodeFromString(
                ValidateTokenResult.serializer(),
                """{"result":false,"token_id":null,"token_scope":null,"user_id":null}""",
            )
        val missingFields =
            json.decodeFromString(
                ValidateTokenResult.serializer(),
                """{"result":false}""",
            )

        assertEquals(false, nullFields.result)
        assertNull(nullFields.tokenId)
        assertNull(nullFields.tokenScope)
        assertNull(nullFields.userId)
        assertEquals(false, missingFields.result)
        assertNull(missingFields.tokenId)
        assertNull(missingFields.tokenScope)
        assertNull(missingFields.userId)
    }
}

package io.putdotio.sdk.account

import io.putdotio.sdk.OkResponse
import io.putdotio.sdk.core.PutioTransport

class AccountApi internal constructor(
    private val transport: PutioTransport,
) {
    suspend fun getInfo(query: AccountInfoQuery = AccountInfoQuery()): AccountInfo =
        transport.get(
            path = "/account/info",
            serializer = AccountInfoEnvelope.serializer(),
            query = query.toQueryMap(),
        ).info

    suspend fun getSettings(): AccountSettings =
        transport.get(
            path = "/account/settings",
            serializer = AccountSettingsEnvelope.serializer(),
        ).settings

    suspend fun saveSettings(update: AccountSettingsUpdate): OkResponse =
        transport.postJson(
            path = "/account/settings",
            serializer = OkResponse.serializer(),
            body = transport.encodeJson(AccountSettingsUpdate.serializer(), update),
        )
}

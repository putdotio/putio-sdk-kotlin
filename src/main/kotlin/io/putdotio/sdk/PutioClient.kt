package io.putdotio.sdk

import io.putdotio.sdk.account.AccountApi
import io.putdotio.sdk.auth.AuthApi
import io.putdotio.sdk.config.ConfigApi
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.files.FilesApi
import io.putdotio.sdk.grants.GrantsApi
import io.putdotio.sdk.history.HistoryApi
import io.putdotio.sdk.ifttt.IftttApi
import io.putdotio.sdk.routes.RoutesApi
import io.putdotio.sdk.transfers.TransfersApi
import io.putdotio.sdk.trash.TrashApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.Closeable

class PutioClient(
    val config: PutioConfig = PutioConfig(),
    okHttpClient: OkHttpClient? = null,
    json: Json = PutioTransport.defaultJson,
) : Closeable {
    private val ownsHttpClient = okHttpClient == null
    private val httpClient = okHttpClient ?: OkHttpClient.Builder().build()
    private val transport = PutioTransport(config = config, httpClient = httpClient, json = json)

    val account = AccountApi(transport)
    val auth = AuthApi(transport)
    val userConfig = ConfigApi(transport)
    val files = FilesApi(transport)
    val grants = GrantsApi(transport)
    val history = HistoryApi(transport)
    val ifttt = IftttApi(transport)
    val routes = RoutesApi(transport)
    val trash = TrashApi(transport)
    val transfers = TransfersApi(transport)

    fun setAccessToken(token: String) {
        config.accessToken = token
    }

    fun clearAccessToken() {
        config.accessToken = null
    }

    override fun close() {
        if (!ownsHttpClient) {
            return
        }

        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        httpClient.cache?.close()
    }
}

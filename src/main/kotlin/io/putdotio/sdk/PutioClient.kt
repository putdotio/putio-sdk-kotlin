package io.putdotio.sdk

import io.putdotio.sdk.account.AccountApi
import io.putdotio.sdk.auth.AuthApi
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.files.FilesApi
import io.putdotio.sdk.history.HistoryApi
import io.putdotio.sdk.trash.TrashApi
import java.io.Closeable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

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
    val files = FilesApi(transport)
    val history = HistoryApi(transport)
    val trash = TrashApi(transport)

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

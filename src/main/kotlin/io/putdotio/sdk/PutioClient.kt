package io.putdotio.sdk

import io.putdotio.sdk.account.AccountApi
import io.putdotio.sdk.auth.AuthApi
import io.putdotio.sdk.auth.DeviceCodeAuth
import io.putdotio.sdk.config.ConfigApi
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.files.FilesApi
import io.putdotio.sdk.grants.GrantsApi
import io.putdotio.sdk.history.HistoryApi
import io.putdotio.sdk.ifttt.IftttApi
import io.putdotio.sdk.routes.RoutesApi
import io.putdotio.sdk.sharing.SharingApi
import io.putdotio.sdk.transfers.TransfersApi
import io.putdotio.sdk.trash.TrashApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.TimeUnit

public class PutioClient internal constructor(
    public val config: PutioConfig,
    okHttpClient: OkHttpClient?,
    json: Json,
    defaultHttpClientFactory: () -> OkHttpClient,
) : Closeable {
    public constructor(
        config: PutioConfig = PutioConfig(),
        okHttpClient: OkHttpClient? = null,
        json: Json = PutioTransport.defaultJson,
    ) : this(
        config = config,
        okHttpClient = okHttpClient,
        json = json,
        defaultHttpClientFactory = ::defaultOkHttpClient,
    )

    private val ownsHttpClient = okHttpClient == null
    private val httpClientDelegate: Lazy<OkHttpClient> =
        if (okHttpClient != null) lazyOf(okHttpClient) else lazy(defaultHttpClientFactory)
    private val transport =
        PutioTransport(
            config = config,
            httpClientProvider = httpClientDelegate::value,
            json = json,
            exemptMultipartCallTimeout = ownsHttpClient,
        )

    internal val defaultCallTimeoutMillis: Int
        get() = httpClientDelegate.value.callTimeoutMillis

    public val account: AccountApi = AccountApi(transport)
    public val auth: AuthApi = AuthApi(transport)
    public val deviceCodeAuth: DeviceCodeAuth = DeviceCodeAuth(auth, account)
    public val appConfig: ConfigApi = ConfigApi(transport)
    public val files: FilesApi = FilesApi(transport)
    public val grants: GrantsApi = GrantsApi(transport)
    public val history: HistoryApi = HistoryApi(transport)
    public val ifttt: IftttApi = IftttApi(transport)
    public val routes: RoutesApi = RoutesApi(transport)
    public val sharing: SharingApi = SharingApi(transport)
    public val trash: TrashApi = TrashApi(transport)
    public val transfers: TransfersApi = TransfersApi(transport)

    public fun setAccessToken(token: String) {
        config.accessToken = token
    }

    public fun clearAccessToken() {
        config.accessToken = null
    }

    /**
     * Replaces the access token with [token] only while it still equals [expected], in one
     * atomic step, and returns whether it did. Pass a `null` [token] to clear, so a sign-out
     * for a rejected token cannot wipe the one a concurrent sign-in just set.
     */
    public fun compareAndSetAccessToken(
        expected: String?,
        token: String?,
    ): Boolean = config.compareAndSetAccessToken(expected, token)

    public override fun close() {
        if (!ownsHttpClient || !httpClientDelegate.isInitialized()) {
            return
        }

        val httpClient = httpClientDelegate.value
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        httpClient.cache?.close()
    }
}

internal fun defaultOkHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .callTimeout(DEFAULT_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

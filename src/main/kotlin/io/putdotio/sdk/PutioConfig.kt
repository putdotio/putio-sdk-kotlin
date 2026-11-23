package io.putdotio.sdk

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

public data class PutioConfig(
    @Volatile public var accessToken: String? = null,
    @Volatile public var clientId: String? = null,
    @Volatile public var clientName: String? = null,
    @Volatile public var baseUrl: String = DEFAULT_API_BASE_URL,
    @Volatile public var webAppUrl: String = DEFAULT_WEB_APP_URL,
    @Volatile public var userAgent: String = DEFAULT_USER_AGENT,
    @Volatile public var uploadBaseUrl: String = DEFAULT_UPLOAD_BASE_URL,
) {
    public override fun toString(): String =
        "PutioConfig(accessToken=${if (accessToken == null) "null" else "<redacted>"}, " +
            "clientId=$clientId, clientName=$clientName, baseUrl=$baseUrl, " +
            "webAppUrl=$webAppUrl, userAgent=$userAgent, uploadBaseUrl=$uploadBaseUrl)"

    internal fun compareAndSetAccessToken(
        expected: String?,
        token: String?,
    ): Boolean {
        while (true) {
            val current = ACCESS_TOKEN.get(this)
            if (current != expected) return false
            if (ACCESS_TOKEN.compareAndSet(this, current, token)) return true
        }
    }

    private companion object {
        val ACCESS_TOKEN: AtomicReferenceFieldUpdater<PutioConfig, String?> =
            AtomicReferenceFieldUpdater.newUpdater(PutioConfig::class.java, String::class.java, "accessToken")
    }
}

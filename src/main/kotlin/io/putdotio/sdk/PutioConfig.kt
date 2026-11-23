package io.putdotio.sdk

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

data class PutioConfig(
    @Volatile var accessToken: String? = null,
    var clientId: String? = null,
    var clientName: String? = null,
    var baseUrl: String = DEFAULT_API_BASE_URL,
    var webAppUrl: String = DEFAULT_WEB_APP_URL,
    var userAgent: String = DEFAULT_USER_AGENT,
    var uploadBaseUrl: String = DEFAULT_UPLOAD_BASE_URL,
) {
    override fun toString(): String =
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

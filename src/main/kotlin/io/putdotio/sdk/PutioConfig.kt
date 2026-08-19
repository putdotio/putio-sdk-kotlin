package io.putdotio.sdk

data class PutioConfig(
    var accessToken: String? = null,
    var clientId: String? = null,
    var clientName: String? = null,
    var baseUrl: String = DEFAULT_API_BASE_URL,
    var webAppUrl: String = DEFAULT_WEB_APP_URL,
    var userAgent: String = DEFAULT_USER_AGENT,
)

package io.putdotio.sdk.errors

data class PutioRequestData(
    val method: String,
    val url: String,
)

sealed class PutioException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class PutioConfigurationException(
    message: String,
) : PutioException(message)

class PutioTransportException(
    val request: PutioRequestData,
    cause: Throwable,
) : PutioException("Transport failure for ${request.method} ${request.url}", cause)

class PutioSerializationException(
    val request: PutioRequestData,
    val responseBody: String,
    cause: Throwable,
) : PutioException("Failed to parse response for ${request.method} ${request.url}", cause)

class PutioApiException(
    val request: PutioRequestData,
    val statusCode: Int,
    val errorType: String?,
    val responseBody: String,
    message: String,
) : PutioException(message)


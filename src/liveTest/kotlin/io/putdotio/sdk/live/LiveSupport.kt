package io.putdotio.sdk.live

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import java.io.File
import java.util.UUID
import org.junit.jupiter.api.Assumptions.assumeTrue

internal object LiveSupport {
    private val envFileValues: Map<String, String> by lazy {
        listOf(".env.local", ".env")
            .fold(emptyMap()) { values, path -> values + loadEnvFile(path) }
    }

    private fun env(name: String): String? =
        (System.getenv(name) ?: envFileValues[name])
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun loadEnvFile(path: String): Map<String, String> {
        val file = File(path)
        if (!file.isFile) return emptyMap()

        return file
            .readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null

                val separator = trimmed.indexOf("=")
                if (separator <= 0) return@mapNotNull null

                val key = trimmed.substring(0, separator).trim()
                val value = trimmed.substring(separator + 1).trim().unquote().takeIf { it.isNotEmpty() }
                if (key.isEmpty() || value == null) null else key to value
            }
            .toMap()
    }

    private fun String.unquote(): String {
        if (length < 2) return this

        return when {
            first() == '"' && last() == '"' -> drop(1).dropLast(1)
            first() == '\'' && last() == '\'' -> drop(1).dropLast(1)
            else -> this
        }
    }

    private fun runtimeValue(
        primary: String,
        vararg aliases: String,
    ): String? {
        sequenceOf(primary, *aliases)
            .mapNotNull(::env)
            .firstOrNull()
            ?.let { return it }

        return null
    }

    private fun requiredEnv(primary: String, vararg aliases: String): String {
        val value = runtimeValue(primary, *aliases)

        assumeTrue(value != null, "Missing live-test credential env: $primary")
        return value ?: error("unreachable")
    }

    fun newAuthedClient(
        clientId: String? = runtimeValue("PUTIO_CLIENT_ID"),
    ): PutioClient =
        PutioClient(
            PutioConfig(
                accessToken = requiredEnv("PUTIO_TOKEN_FIRST_PARTY", "PUTIO_ACCESS_TOKEN", "PUTIO_TOKEN"),
                clientId = clientId,
                clientName = "putio-sdk-kotlin live",
                baseUrl = env("PUTIO_BASE_URL") ?: "https://api.put.io/v2/",
            ),
        )

    fun requireClientId(): String = requiredEnv("PUTIO_CLIENT_ID")

    fun uniqueName(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(12)}"
}

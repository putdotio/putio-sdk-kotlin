package io.putdotio.sdk.live

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue

internal object LiveSupport {
    private val json = Json { ignoreUnknownKeys = true }

    private fun env(name: String): String? =
        System.getenv(name)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun runtimeItemVault(): String {
        val vault = env("PUTIO_1PASSWORD_RUNTIME_VAULT")
        check(vault != null) {
            "Missing PUTIO_1PASSWORD_RUNTIME_VAULT. Set it explicitly when reading runtime tokens from 1Password."
        }
        return vault
    }

    private fun loadRuntimeItem(): JsonObject? {
        val runtimeItemId = env("PUTIO_1PASSWORD_RUNTIME_ITEM_ID") ?: return null
        if (env("OP_SERVICE_ACCOUNT_TOKEN") == null) return null

        val process = ProcessBuilder(
            "op",
            "item",
            "get",
            runtimeItemId,
            "--vault",
            runtimeItemVault(),
            "--format",
            "json",
            "--reveal",
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        check(exitCode == 0) { "Failed to read runtime-token item $runtimeItemId: $output" }

        return json.parseToJsonElement(output).jsonObject
    }

    private val runtimeItem: JsonObject? by lazy(::loadRuntimeItem)

    private fun findRuntimeField(
        label: String,
        sectionLabel: String? = null,
    ): String? =
        runtimeItem
            ?.get("fields")
            ?.jsonArray
            ?.firstOrNull { field ->
                val jsonField = field.jsonObject
                val fieldLabel = jsonField["label"]?.jsonPrimitive?.contentOrNull
                val runtimeSection = jsonField["section"]?.jsonObject?.get("label")?.jsonPrimitive?.contentOrNull
                fieldLabel == label && runtimeSection == sectionLabel
            }
            ?.jsonObject
            ?.get("value")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun legacyRuntimeNotes(): JsonObject? {
        val notes = findRuntimeField(label = "notesPlain") ?: return null
        return runCatching { json.parseToJsonElement(notes).jsonObject }.getOrNull()
    }

    private fun runtimeValue(
        primary: String,
        vararg aliases: String,
    ): String? {
        sequenceOf(primary, *aliases)
            .mapNotNull(::env)
            .firstOrNull()
            ?.let { return it }

        return when (primary) {
            "PUTIO_TOKEN_FIRST_PARTY" ->
                findRuntimeField(label = "access_token", sectionLabel = "first_party")
                    ?: legacyRuntimeNotes()
                        ?.get("first_party")
                        ?.jsonObject
                        ?.get("accessToken")
                        ?.jsonPrimitive
                        ?.contentOrNull
            "PUTIO_CLIENT_ID" ->
                findRuntimeField(label = "app_id", sectionLabel = "third_party")
                    ?: findRuntimeField(label = "third_party_app_id")
                    ?: legacyRuntimeNotes()
                        ?.get("third_party_app_id")
                        ?.jsonPrimitive
                        ?.contentOrNull
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
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

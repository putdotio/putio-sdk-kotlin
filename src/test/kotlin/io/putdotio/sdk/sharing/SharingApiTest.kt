package io.putdotio.sdk.sharing

import io.putdotio.sdk.PutioClient
import io.putdotio.sdk.PutioConfig
import io.putdotio.sdk.core.PutioTransport
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioOperationErrorReason
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.errors.PutioSerializationException
import io.putdotio.sdk.files.PutioFileType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharingApiTest {
    @Test
    fun `shareFiles posts file ids to everyone`() =
        withServer { server ->
            server.enqueue(ok())

            runBlocking {
                client(server).use {
                    it.sharing.shareFiles(ShareFilesInput(target = ShareTarget.Everyone, ids = listOf(1, 2)))
                }
            }

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v2/files/share", request.target)
            assertEquals("file_ids=1%2C2&friends=everyone", request.body!!.utf8())
        }

    @Test
    fun `shareFiles posts a cursor selection to named friends`() =
        withServer { server ->
            server.enqueue(ok())

            runBlocking {
                client(server).use {
                    it.sharing.shareFiles(
                        ShareFilesInput(
                            target = ShareTarget.Friends(listOf("alice", "bob")),
                            cursor = "selection",
                            excludeIds = listOf(3, 4),
                        ),
                    )
                }
            }

            assertEquals(
                "cursor=selection&exclude_ids=3%2C4&friends=alice%2Cbob",
                server.takeRequest().body!!.utf8(),
            )
        }

    @Test
    fun `share inputs reject empty selections and friend lists`() {
        assertFailsWith<IllegalArgumentException> { ShareFilesInput(target = ShareTarget.Everyone) }
        assertFailsWith<IllegalArgumentException> { ShareFilesInput(target = ShareTarget.Everyone, cursor = " ") }
        assertFailsWith<IllegalArgumentException> { ShareTarget.Friends(emptyList()) }
        assertFailsWith<IllegalArgumentException> { ShareTarget.Friends(listOf("alice", "")) }
    }

    @Test
    fun `listSharedFiles decodes everyone, friend-count, and unknown audiences`() =
        withServer { server ->
            server.enqueue(
                json(
                    """
                    {
                      "status": "OK",
                      "shared": [
                        ${sharedFileJson(id = 1, sharedWith = "\"everyone\"")},
                        ${sharedFileJson(id = 2, sharedWith = "3")},
                        ${sharedFileJson(id = 3, sharedWith = "\"team\"")}
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val shared = runBlocking { client(server).use { it.sharing.listSharedFiles() } }

            assertEquals(listOf(1L, 2L, 3L), shared.map { it.file.id })
            assertEquals(PutioFileType.FOLDER, shared.first().file.fileType)
            assertEquals(
                listOf(
                    SharedFileAudience.Everyone,
                    SharedFileAudience.Friends(3),
                    SharedFileAudience.Unknown("team"),
                ),
                shared.map { it.sharedWith },
            )
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v2/files/shared", request.target)
        }

    @Test
    fun `listSharedFiles rejects entries without a valid shared_with`() {
        for (entry in listOf(sharedFileJson(id = 1, sharedWith = null), sharedFileJson(id = 1, sharedWith = "-1"))) {
            withServer { server ->
                server.enqueue(json("""{"status":"OK","shared":[$entry]}"""))

                val error =
                    assertFailsWith<PutioOperationException> {
                        runBlocking { client(server).use { it.sharing.listSharedFiles() } }
                    }

                assertEquals("listSharedFiles", error.operation)
                assertIs<PutioSerializationException>(error.underlyingError)
            }
        }
    }

    @Test
    fun `shared file roundtrips through its serializer`() {
        val json = PutioTransport.defaultJson
        val decoded = json.decodeFromString(SharedFile.serializer(), sharedFileJson(id = 9, sharedWith = "2"))

        for (audience in listOf(SharedFileAudience.Everyone, SharedFileAudience.Friends(2), SharedFileAudience.Unknown("team"))) {
            val file = decoded.copy(sharedWith = audience)
            assertEquals(file, json.decodeFromString(SharedFile.serializer(), json.encodeToString(SharedFile.serializer(), file)))
        }
    }

    @Test
    fun `getSharedWith decodes everyone, friends, and unknown share types`() =
        withServer { server ->
            server.enqueue(json("""{"status":"OK","share_type":"everyone"}"""))
            server.enqueue(
                json(
                    """
                    {
                      "share_type": "friends",
                      "shares": [
                        {"share_id": 11, "user_name": "alice", "user_avatar_url": "https://put.io/avatar/alice"}
                      ]
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(json("""{"share_type":"team"}"""))

            runBlocking {
                client(server).use {
                    assertEquals(SharedWith.Everyone, it.sharing.getSharedWith(5))
                    assertEquals(
                        SharedWith.Friends(listOf(SharedFileShare(11, "alice", "https://put.io/avatar/alice"))),
                        it.sharing.getSharedWith(5),
                    )
                    assertEquals(SharedWith.Unknown("team"), it.sharing.getSharedWith(5))
                }
            }

            assertEquals("/v2/files/5/shared-with-v2", server.takeRequest().target)
        }

    @Test
    fun `getSharedWith rejects friend shares without a share list`() =
        withServer { server ->
            server.enqueue(json("""{"share_type":"friends"}"""))

            val error =
                assertFailsWith<PutioOperationException> {
                    runBlocking { client(server).use { it.sharing.getSharedWith(5) } }
                }

            assertIs<PutioSerializationException>(error.underlyingError)
        }

    @Test
    fun `unshare removes every share by default`() =
        withServer { server ->
            server.enqueue(ok())

            runBlocking { client(server).use { it.sharing.unshare(fileId = 5) } }

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v2/files/5/unshare", request.target)
            assertEquals("shares=everyone", request.body!!.utf8())
        }

    @Test
    fun `unshare removes the given share ids`() =
        withServer { server ->
            server.enqueue(ok())

            runBlocking { client(server).use { it.sharing.unshare(fileId = 5, shareIds = listOf(11, 12)) } }

            assertEquals("shares=11%2C12", server.takeRequest().body!!.utf8())
        }

    @Test
    fun `publicShares create posts the file id and redacts its tokens`() =
        withServer { server ->
            server.enqueue(json("""{"status":"OK","public_share":${publicShareJson(id = 21)}}"""))

            val share = runBlocking { client(server).use { it.sharing.publicShares.create(fileId = 5) } }

            assertEquals(21L, share.id)
            assertEquals("secret-token", share.token.value)
            assertEquals("secret-push-token", share.pushToken.value)
            assertEquals(PublicShareFile(id = 5, name = "Movie.mkv", fileType = PutioFileType.VIDEO), share.userFile)
            assertEquals("altay", share.owner.name)
            assertFalse(share.toString().contains("secret"))
            assertTrue(share.toString().contains("<redacted public share token>"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v2/public_share/5", request.target)
        }

    @Test
    fun `publicShares list decodes every public share`() =
        withServer { server ->
            server.enqueue(json("""{"public_shares":[${publicShareJson(id = 21)},${publicShareJson(id = 22)}]}"""))

            val shares = runBlocking { client(server).use { it.sharing.publicShares.list() } }

            assertEquals(listOf(21L, 22L), shares.map { it.id })
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v2/public_share/list", request.target)
        }

    @Test
    fun `publicShares delete sends a DELETE for the share id`() =
        withServer { server ->
            server.enqueue(ok())

            runBlocking { client(server).use { it.sharing.publicShares.delete(id = 21) } }

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/v2/public_share/21", request.target)
        }

    @Test
    fun `sharing responses reject non-OK statuses`() {
        val cases: List<Pair<String, suspend (PutioClient) -> Unit>> =
            listOf(
                """{"status":"ERROR","shared":[]}""" to { it.sharing.listSharedFiles() },
                """{"status":"ERROR","share_type":"everyone"}""" to { it.sharing.getSharedWith(5) },
                """{"status":"ERROR","public_share":${publicShareJson(id = 21)}}""" to { it.sharing.publicShares.create(5) },
                """{"status":"ERROR","public_shares":[]}""" to { it.sharing.publicShares.list() },
            )

        for ((body, call) in cases) {
            withServer { server ->
                server.enqueue(json(body))

                val error =
                    assertFailsWith<PutioOperationException> {
                        runBlocking { client(server).use { call(it) } }
                    }

                assertIs<PutioSerializationException>(error.underlyingError)
            }
        }
    }

    @Test
    fun `operations map every known error with sharing context`() {
        val cases =
            listOf(
                KnownErrorCase("shareFiles", 400, "ALREADY_SHARED") {
                    it.sharing.shareFiles(ShareFilesInput(target = ShareTarget.Everyone, ids = listOf(1)))
                },
                KnownErrorCase("shareFiles", 401, "invalid_scope") {
                    it.sharing.shareFiles(ShareFilesInput(target = ShareTarget.Everyone, ids = listOf(1)))
                },
                KnownErrorCase("shareFiles", 400, null) {
                    it.sharing.shareFiles(ShareFilesInput(target = ShareTarget.Everyone, ids = listOf(1)))
                },
                KnownErrorCase("listSharedFiles", 401, "invalid_scope") { it.sharing.listSharedFiles() },
                KnownErrorCase("getSharedWith", 401, "invalid_scope") { it.sharing.getSharedWith(5) },
                KnownErrorCase("getSharedWith", 404, null) { it.sharing.getSharedWith(5) },
                KnownErrorCase("unshare", 401, "invalid_scope") { it.sharing.unshare(5) },
                KnownErrorCase("unshare", 400, null) { it.sharing.unshare(5) },
                KnownErrorCase("unshare", 404, null) { it.sharing.unshare(5) },
                KnownErrorCase("listPublicShares", 401, "invalid_scope") { it.sharing.publicShares.list() },
                KnownErrorCase("deletePublicShare", 401, "invalid_scope") { it.sharing.publicShares.delete(21) },
                KnownErrorCase("deletePublicShare", 404, null) { it.sharing.publicShares.delete(21) },
                KnownErrorCase("createPublicShare", 401, "invalid_scope") { it.sharing.publicShares.create(5) },
                KnownErrorCase("createPublicShare", 404, null) { it.sharing.publicShares.create(5) },
                KnownErrorCase("createPublicShare", 400, "PUBLIC_SHARE_FOLDER_ROOT_NOT_ALLOWED") {
                    it.sharing.publicShares.create(0)
                },
            ) +
                listOf(
                    "PUBLIC_SHARE_NOT_ALLOWED_PLAN",
                    "PUBLIC_SHARE_SINGLE_FILE_LIMIT_EXCEEDED",
                    "PUBLIC_SHARE_FOLDER_LINK_COUNT_LIMIT_EXCEEDED",
                    "PUBLIC_SHARE_FOLDER_MAX_SIZE_LIMIT_EXCEEDED",
                    "PUBLIC_SHARE_FOLDER_MAX_CHILDREN_LIMIT_EXCEEDED",
                    "PUBLIC_SHARE_DAILY_TOTAL_LINK_COUNT_EXCEEDED",
                    "PUBLIC_SHARE_WEEKLY_TOTAL_LINK_COUNT_EXCEEDED",
                ).map { errorType ->
                    KnownErrorCase("createPublicShare", 403, errorType) { it.sharing.publicShares.create(5) }
                }

        for (case in cases) {
            withServer { server ->
                val errorTypeField = case.errorType?.let { ""","error_type":"$it"""" }.orEmpty()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(case.statusCode)
                        .body("""{"status":"ERROR","status_code":${case.statusCode}$errorTypeField,"message":"nope"}""")
                        .build(),
                )

                val error =
                    assertFailsWith<PutioOperationException>(case.toString()) {
                        runBlocking { client(server).use { case.call(it) } }
                    }

                assertEquals("sharing", error.domain, case.toString())
                assertEquals(case.operation, error.operation, case.toString())
                val expectedReason =
                    case.errorType?.let(PutioOperationErrorReason::ErrorType)
                        ?: PutioOperationErrorReason.StatusCode(case.statusCode)
                assertEquals(expectedReason, error.reason, case.toString())
                assertEquals(case.statusCode, assertIs<PutioApiException>(error.underlyingError).statusCode)
            }
        }
    }

    private data class KnownErrorCase(
        val operation: String,
        val statusCode: Int,
        val errorType: String?,
        val call: suspend (PutioClient) -> Unit,
    ) {
        override fun toString(): String = "$operation $statusCode ${errorType ?: "-"}"
    }

    private fun sharedFileJson(
        id: Long,
        sharedWith: String?,
    ): String {
        val sharedWithField = sharedWith?.let { ""","shared_with":$it""" }.orEmpty()
        return """{"id":$id,"name":"Shared $id","created_at":"2026-09-01T10:00:00","file_type":"FOLDER","is_shared":true$sharedWithField}"""
    }

    private fun publicShareJson(id: Long): String =
        """
        {
          "id": $id,
          "token": "secret-token",
          "push_token": "secret-push-token",
          "created_at": "2026-09-01T10:00:00",
          "expiration_date": "2026-10-01T10:00:00",
          "owner": {"name": "altay"},
          "user_file": {"id": 5, "name": "Movie.mkv", "file_type": "VIDEO"}
        }
        """.trimIndent()

    private fun ok(): MockResponse = json("""{"status":"OK"}""")

    private fun json(body: String): MockResponse = MockResponse.Builder().body(body).build()

    private fun client(server: MockWebServer): PutioClient =
        PutioClient(
            PutioConfig(
                accessToken = "token",
                baseUrl = server.url("/v2/").toString(),
            ),
        )

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}

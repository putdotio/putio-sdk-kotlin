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
        assertFailsWith<IllegalArgumentException> { ShareTarget.Friends(listOf("alice", "everyone")) }
        assertFailsWith<IllegalArgumentException> { ShareTarget.Friends(listOf("alice,everyone")) }
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
    fun `cloneSharedFiles posts the ids and destination and returns the copy id`() =
        withServer { server ->
            server.enqueue(json("""{"status":"OK","id":42}"""))

            val id =
                runBlocking {
                    client(server).use { it.sharing.cloneSharedFiles(CloneSharedFilesInput(ids = listOf(1, 2), parentId = 7)) }
                }

            assertEquals(42L, id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v2/sharing/clone", request.target)
            assertEquals("file_ids=1%2C2&parent_id=7", request.body!!.utf8())
        }

    @Test
    fun `cloneSharedFiles posts a cursor selection into root by default`() =
        withServer { server ->
            server.enqueue(json("""{"id":43}"""))

            runBlocking {
                client(server).use {
                    it.sharing.cloneSharedFiles(CloneSharedFilesInput(cursor = "selection", excludeIds = listOf(3, 4)))
                }
            }

            assertEquals("cursor=selection&exclude_ids=3%2C4&parent_id=0", server.takeRequest().body!!.utf8())
        }

    @Test
    fun `clone inputs reject empty selections, nonpositive ids and a negative parent`() {
        assertFailsWith<IllegalArgumentException> { CloneSharedFilesInput() }
        assertFailsWith<IllegalArgumentException> { CloneSharedFilesInput(cursor = " ") }
        assertFailsWith<IllegalArgumentException> { CloneSharedFilesInput(ids = listOf(0)) }
        assertFailsWith<IllegalArgumentException> { CloneSharedFilesInput(cursor = "selection", excludeIds = listOf(-1)) }
        assertFailsWith<IllegalArgumentException> { CloneSharedFilesInput(ids = listOf(1), parentId = -1) }
    }

    @Test
    fun `cloneSharedFiles rejects a response without a positive id`() {
        for (body in listOf("""{"status":"OK"}""", """{"status":"OK","id":0}""")) {
            withServer { server ->
                server.enqueue(json(body))

                val error =
                    assertFailsWith<PutioOperationException> {
                        runBlocking { client(server).use { it.sharing.cloneSharedFiles(CloneSharedFilesInput(ids = listOf(1))) } }
                    }

                assertEquals("cloneSharedFiles", error.operation)
                assertIs<PutioSerializationException>(error.underlyingError)
            }
        }
    }

    @Test
    fun `getCloneInfo decodes every status, the error message and unknown statuses`() =
        withServer { server ->
            server.enqueue(json("""{"status":"OK","shared_file_clone_status":"NEW"}"""))
            server.enqueue(json("""{"status":"OK","shared_file_clone_status":"PROCESSING"}"""))
            server.enqueue(json("""{"status":"OK","shared_file_clone_status":"DONE"}"""))
            server.enqueue(
                json("""{"status":"OK","shared_file_clone_status":"ERROR","error_msg":"File(s) size exceed disk limit."}"""),
            )
            server.enqueue(json("""{"shared_file_clone_status":"PAUSED"}"""))

            val infos = runBlocking { client(server).use { sdk -> List(5) { sdk.sharing.getCloneInfo(9) } } }

            assertEquals(
                listOf(
                    SharedFileCloneInfo(SharedFileCloneStatus.NEW),
                    SharedFileCloneInfo(SharedFileCloneStatus.PROCESSING),
                    SharedFileCloneInfo(SharedFileCloneStatus.DONE),
                    SharedFileCloneInfo(SharedFileCloneStatus.ERROR, "File(s) size exceed disk limit."),
                    SharedFileCloneInfo(SharedFileCloneStatus("PAUSED")),
                ),
                infos,
            )
            assertEquals(listOf(false, false, true, true, false), infos.map { it.status.isFinished })
            assertEquals(listOf(true, true, true, true, false), infos.map { it.status.isKnown })
            assertEquals("PAUSED", infos.last().status.toString())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v2/sharing/clone/9", request.target)
        }

    @Test
    fun `getCloneInfo redacts sensitive urls in the error message`() =
        withServer { server ->
            server.enqueue(
                json(
                    """{"shared_file_clone_status":"ERROR","error_msg":"Failed https://example.test/cb?oauth_token=leak"}""",
                ),
            )

            val info = runBlocking { client(server).use { it.sharing.getCloneInfo(9) } }

            assertEquals("Failed https://example.test/cb?oauth_token=REDACTED", info.errorMessage)
        }

    @Test
    fun `getCloneInfo rejects a nonpositive id without a request`() =
        withServer { server ->
            assertFailsWith<IllegalArgumentException> {
                runBlocking { client(server).use { it.sharing.getCloneInfo(0) } }
            }

            assertEquals(0, server.requestCount)
        }

    @Test
    fun `unshareAll removes every share`() =
        withServer { server ->
            server.enqueue(ok())

            runBlocking { client(server).use { it.sharing.unshareAll(fileId = 5) } }

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v2/files/5/unshare", request.target)
            assertEquals("shares=everyone", request.body!!.utf8())
        }

    @Test
    fun `unshare rejects an empty share id list without a request`() =
        withServer { server ->
            assertFailsWith<IllegalArgumentException> {
                runBlocking { client(server).use { it.sharing.unshare(fileId = 5, shareIds = emptyList()) } }
            }

            assertEquals(0, server.requestCount)
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
                """{"status":"ERROR","id":42}""" to { it.sharing.cloneSharedFiles(CloneSharedFilesInput(ids = listOf(1))) },
                """{"status":"ERROR","shared_file_clone_status":"DONE"}""" to { it.sharing.getCloneInfo(9) },
            )

        for ((body, call) in cases) {
            withServer { server ->
                server.enqueue(json(body))

                val error =
                    assertFailsWith<PutioOperationException> {
                        runBlocking { client(server).use { call(it) } }
                    }

                val serialization = assertIs<PutioSerializationException>(error.underlyingError)
                assertFalse(serialization.responseBody.contains("secret-"))
                assertFalse(error.stackTraceToString().contains("secret-"))
            }
        }
    }

    @Test
    fun `operations map every known error with sharing context`() {
        val cloneInput = CloneSharedFilesInput(ids = listOf(1), parentId = 7)
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
                KnownErrorCase("unshare", 401, "invalid_scope") { it.sharing.unshareAll(5) },
                KnownErrorCase("unshare", 400, null) { it.sharing.unshare(5, listOf(11)) },
                KnownErrorCase("unshare", 404, null) { it.sharing.unshare(5, listOf(11)) },
                KnownErrorCase("listPublicShares", 401, "invalid_scope") { it.sharing.publicShares.list() },
                KnownErrorCase("deletePublicShare", 401, "invalid_scope") { it.sharing.publicShares.delete(21) },
                KnownErrorCase("deletePublicShare", 404, null) { it.sharing.publicShares.delete(21) },
                KnownErrorCase("createPublicShare", 401, "invalid_scope") { it.sharing.publicShares.create(5) },
                KnownErrorCase("createPublicShare", 404, null) { it.sharing.publicShares.create(5) },
                KnownErrorCase("createPublicShare", 400, "PUBLIC_SHARE_FOLDER_ROOT_NOT_ALLOWED") {
                    it.sharing.publicShares.create(0)
                },
                KnownErrorCase("cloneSharedFiles", 401, "invalid_scope") { it.sharing.cloneSharedFiles(cloneInput) },
                KnownErrorCase("cloneSharedFiles", 400, null) { it.sharing.cloneSharedFiles(cloneInput) },
                KnownErrorCase("cloneSharedFiles", 404, null) { it.sharing.cloneSharedFiles(cloneInput) },
                KnownErrorCase("getCloneInfo", 401, "invalid_scope") { it.sharing.getCloneInfo(9) },
                KnownErrorCase("getCloneInfo", 404, "SHARED_FILE_CLONE_NOT_FOUND") { it.sharing.getCloneInfo(9) },
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
                } +
                listOf(
                    "SharedFileCloneConcurrentLimit",
                    "SharedFileCloneTooManyFiles",
                    "SharedFileCloneTooManyChildren",
                ).map { errorType ->
                    KnownErrorCase("cloneSharedFiles", 400, errorType) { it.sharing.cloneSharedFiles(cloneInput) }
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

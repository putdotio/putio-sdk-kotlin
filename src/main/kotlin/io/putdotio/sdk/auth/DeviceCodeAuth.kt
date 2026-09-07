package io.putdotio.sdk.auth

import io.putdotio.sdk.account.AccountApi
import io.putdotio.sdk.account.AccountInfo
import io.putdotio.sdk.errors.PutioApiException
import io.putdotio.sdk.errors.PutioException
import io.putdotio.sdk.errors.PutioOperationException
import io.putdotio.sdk.errors.PutioTransportException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Observable states of one device-code linking attempt. Terminal states are
 * [Linked], [Expired], and [Failed]; the flow completes after emitting one.
 */
sealed interface DeviceCodeAuthState {
    /** Requesting a fresh code from put.io. */
    data object Requesting : DeviceCodeAuthState

    /** Show [code] and point the user to put.io/link; polling runs until [budget] elapses. */
    data class AwaitingLink(
        val code: String,
        val qrCodeUrl: String,
        val budget: Duration,
    ) : DeviceCodeAuthState

    /** put.io issued a token; validating it and loading the account. */
    data object Validating : DeviceCodeAuthState

    /** Linked. The token is unredacted here and only here; store it, do not log it. */
    data class Linked(
        val accessToken: String,
        val account: AccountInfo,
    ) : DeviceCodeAuthState {
        override fun toString(): String = "Linked(account=${account.username})"
    }

    /** The code was never approved within the budget, or put.io rejected it as unknown. Start again. */
    data class Expired(
        val reason: Reason,
    ) : DeviceCodeAuthState {
        enum class Reason { BUDGET_ELAPSED, CODE_REJECTED }
    }

    /** A non-recoverable error. [error] is a typed SDK exception; localize it for the user. */
    data class Failed(
        val error: PutioException,
    ) : DeviceCodeAuthState
}

data class DeviceCodeAuthOptions(
    /** Interval between code-match polls. put.io's own TV app polls every three seconds. */
    val pollInterval: Duration = 3.seconds,
    /** How long one code is polled before the attempt reports [DeviceCodeAuthState.Expired]. */
    val budget: Duration = 5.minutes,
) {
    init {
        require(pollInterval.isPositive()) { "pollInterval must be positive" }
        require(budget >= pollInterval) { "budget must cover at least one poll" }
    }
}

/**
 * Orchestrates the device-code flow from the auth-and-device-linking contract:
 * request a code, poll the match endpoint, then validate the token and load the
 * account. Each collection is one attempt; collect again to get a new code.
 * Cancelling the collector stops polling immediately.
 */
class DeviceCodeAuth internal constructor(
    private val auth: AuthApi,
    private val account: AccountApi,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) {
    fun link(options: DeviceCodeAuthOptions = DeviceCodeAuthOptions()): Flow<DeviceCodeAuthState> =
        flow {
            emit(DeviceCodeAuthState.Requesting)
            val issued =
                try {
                    auth.getCode()
                } catch (error: PutioException) {
                    emit(DeviceCodeAuthState.Failed(error))
                    return@flow
                }
            emit(DeviceCodeAuthState.AwaitingLink(issued.code, issued.qrCodeUrl, options.budget))

            val token = pollForToken(issued.code, options) ?: return@flow
            emit(DeviceCodeAuthState.Validating)
            try {
                val validation = auth.validateToken(token)
                if (!validation.result) {
                    emit(DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.CODE_REJECTED))
                    return@flow
                }
                val info = account.getInfoWith(token)
                emit(DeviceCodeAuthState.Linked(token, info))
            } catch (error: PutioException) {
                emit(DeviceCodeAuthState.Failed(error))
            }
        }

    // Returns the token, or null after emitting a terminal state.
    private suspend fun kotlinx.coroutines.flow.FlowCollector<DeviceCodeAuthState>.pollForToken(
        code: String,
        options: DeviceCodeAuthOptions,
    ): String? {
        val started = timeSource.markNow()
        while (true) {
            sleep(options.pollInterval)
            if (started.elapsedNow() >= options.budget) {
                emit(DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.BUDGET_ELAPSED))
                return null
            }
            try {
                auth.checkCodeMatch(code)?.let { return it }
            } catch (error: PutioOperationException) {
                when {
                    error.isCodeRejection() -> {
                        emit(DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.CODE_REJECTED))
                        return null
                    }

                    // Flaky TV networks: keep polling until the budget runs out.
                    error.underlyingError is PutioTransportException -> {
                        Unit
                    }

                    else -> {
                        emit(DeviceCodeAuthState.Failed(error))
                        return null
                    }
                }
            }
        }
    }
}

private fun PutioOperationException.isCodeRejection(): Boolean = (underlyingError as? PutioApiException)?.statusCode == HTTP_NOT_FOUND

private const val HTTP_NOT_FOUND = 404

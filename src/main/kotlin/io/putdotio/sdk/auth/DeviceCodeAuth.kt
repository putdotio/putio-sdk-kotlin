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
import kotlinx.coroutines.withTimeoutOrNull
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
        require(budget > pollInterval) { "budget must exceed pollInterval so at least one poll runs" }
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
                when (val call = sdkCall { auth.getCode() }) {
                    is SdkCall.Ok -> {
                        call.value
                    }

                    is SdkCall.Error -> {
                        emit(DeviceCodeAuthState.Failed(call.error))
                        return@flow
                    }
                }
            emit(DeviceCodeAuthState.AwaitingLink(issued.code, issued.qrCodeUrl, options.budget))

            val token =
                when (val outcome = pollForToken(issued.code, options)) {
                    is PollOutcome.Token -> {
                        outcome.value
                    }

                    is PollOutcome.Terminal -> {
                        emit(outcome.state)
                        return@flow
                    }
                }
            emit(DeviceCodeAuthState.Validating)
            emit(validateAndLoad(token))
        }

    // Only SDK calls sit inside try blocks; collector exceptions propagate untouched.
    private suspend fun validateAndLoad(token: String): DeviceCodeAuthState {
        val validation =
            when (val call = sdkCall { auth.validateToken(token) }) {
                is SdkCall.Ok -> call.value
                is SdkCall.Error -> return DeviceCodeAuthState.Failed(call.error)
            }
        if (!validation.result) {
            return DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.CODE_REJECTED)
        }
        return when (val call = sdkCall { account.getInfoWith(token) }) {
            is SdkCall.Ok -> DeviceCodeAuthState.Linked(token, call.value)
            is SdkCall.Error -> DeviceCodeAuthState.Failed(call.error)
        }
    }

    private sealed interface PollOutcome {
        data class Token(
            val value: String,
        ) : PollOutcome

        data class Terminal(
            val state: DeviceCodeAuthState,
        ) : PollOutcome
    }

    // The budget bounds both the sleep and the time a response may arrive; a token that
    // lands after the deadline is treated as expired so the caller re-requests a code.
    private suspend fun pollForToken(
        code: String,
        options: DeviceCodeAuthOptions,
    ): PollOutcome {
        val deadline = timeSource.markNow() + options.budget
        while (true) {
            val remaining = -deadline.elapsedNow()
            if (remaining <= Duration.ZERO) return PollOutcome.Terminal(budgetElapsed())
            sleep(minOf(options.pollInterval, remaining))
            val left = -deadline.elapsedNow()
            if (left <= Duration.ZERO) return PollOutcome.Terminal(budgetElapsed())
            // A stalled request must not hold AwaitingLink past the budget; the timeout
            // cancels only this call, so collector cancellation still propagates.
            val call =
                withTimeoutOrNull(left) { sdkCall { auth.checkCodeMatch(code) } }
                    ?: return PollOutcome.Terminal(budgetElapsed())
            val token =
                when (call) {
                    is SdkCall.Ok -> {
                        call.value ?: continue
                    }

                    is SdkCall.Error -> {
                        pollFailure(call.error)?.let { return PollOutcome.Terminal(it) }
                        continue
                    }
                }
            if (deadline.hasPassedNow()) return PollOutcome.Terminal(budgetElapsed())
            return PollOutcome.Token(token)
        }
    }

    // Null means keep polling: flaky TV networks should not end the attempt before the budget does.
    private fun pollFailure(error: PutioException): DeviceCodeAuthState? =
        when {
            error is PutioOperationException && error.isCodeRejection() -> {
                DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.CODE_REJECTED)
            }

            error is PutioOperationException && error.underlyingError is PutioTransportException -> {
                null
            }

            else -> {
                DeviceCodeAuthState.Failed(error)
            }
        }

    private fun budgetElapsed(): DeviceCodeAuthState = DeviceCodeAuthState.Expired(DeviceCodeAuthState.Expired.Reason.BUDGET_ELAPSED)
}

private sealed interface SdkCall<out T> {
    data class Ok<T>(
        val value: T,
    ) : SdkCall<T>

    data class Error(
        val error: PutioException,
    ) : SdkCall<Nothing>
}

// Only SDK exceptions are caught; cancellation and collector failures pass through.
private inline fun <T> sdkCall(block: () -> T): SdkCall<T> =
    try {
        SdkCall.Ok(block())
    } catch (error: PutioException) {
        SdkCall.Error(error)
    }

private fun PutioOperationException.isCodeRejection(): Boolean = (underlyingError as? PutioApiException)?.statusCode == HTTP_NOT_FOUND

private const val HTTP_NOT_FOUND = 404

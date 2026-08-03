package com.arny.habrrss.data.remote.habr.error

import com.arny.habrrss.data.remote.habr.dto.HabrErrorDto
import io.ktor.http.HttpStatusCode

sealed class HabrRemoteException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class BadRequest(
        val responseSample: String,
    ) : HabrRemoteException("Unsupported Habr API request")

    class NotFound(
        val responseSample: String,
    ) : HabrRemoteException("Habr resource was not found")

    class Validation(
        val responseSample: String,
    ) : HabrRemoteException("Invalid Habr API parameters")

    class RateLimited(
        val retryAfterSeconds: Long?,
    ) : HabrRemoteException("Habr API rate limit")

    class Server(
        val status: Int,
    ) : HabrRemoteException("Habr server error: $status")

    class ContractChanged(
        val status: Int,
        val responseSample: String,
        cause: Throwable,
    ) : HabrRemoteException("Habr API contract changed", cause)

    class UnexpectedStatus(
        val status: Int,
        val responseSample: String,
    ) : HabrRemoteException("Unexpected Habr API status: $status")
}

internal fun HttpStatusCode.toHabrException(
    errorDto: HabrErrorDto?,
    responseSample: String,
    retryAfterSeconds: Long? = null,
): HabrRemoteException = when (value) {
    400 -> HabrRemoteException.BadRequest(responseSample)
    404 -> HabrRemoteException.NotFound(responseSample)
    422 -> HabrRemoteException.Validation(responseSample)
    429 -> HabrRemoteException.RateLimited(retryAfterSeconds)
    in 500..599 -> HabrRemoteException.Server(value)
    else -> HabrRemoteException.UnexpectedStatus(
        status = value,
        responseSample = errorDto?.message?.let { "$it\n$responseSample" } ?: responseSample,
    )
}

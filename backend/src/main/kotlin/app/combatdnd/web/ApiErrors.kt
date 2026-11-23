package app.combatdnd.web

import app.combatdnd.auth.NotSignedIn
import app.combatdnd.encounter.api.ErrorResponse
import app.combatdnd.encounter.app.CommandRejected
import app.combatdnd.encounter.app.EncounterNotFound
import app.combatdnd.encounter.app.StaleVersion
import app.combatdnd.encounter.domain.Reason
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Every API error is `{"message": "..."}` with a status the frontend can act on. */
@RestControllerAdvice
class ApiErrors {
    private fun error(status: HttpStatus, message: String?) =
        ResponseEntity.status(status).body(ErrorResponse(message ?: status.reasonPhrase))

    @ExceptionHandler
    fun notFound(e: EncounterNotFound) = error(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler
    fun stale(e: StaleVersion) = error(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler
    fun rejected(e: CommandRejected) =
        error(if (e.reason == Reason.FORBIDDEN) HttpStatus.FORBIDDEN else HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler
    fun notSignedIn(e: NotSignedIn) = error(HttpStatus.UNAUTHORIZED, e.message)

    @ExceptionHandler
    fun api(e: ApiException) = error(e.status, e.message)

    @ExceptionHandler
    fun tooBig(e: org.springframework.web.multipart.MaxUploadSizeExceededException) =
        error(HttpStatus.PAYLOAD_TOO_LARGE, "That picture is over 5 MB. Try a smaller one.")

    @ExceptionHandler
    fun unreadable(e: HttpMessageNotReadableException) = error(HttpStatus.BAD_REQUEST, "That request isn't valid JSON for this command.")
}

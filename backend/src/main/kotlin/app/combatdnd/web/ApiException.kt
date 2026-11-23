package app.combatdnd.web

import org.springframework.http.HttpStatus

/** An error the client should see as-is: `{"message": ...}` with this status. */
open class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)

class NotFound(message: String) : ApiException(HttpStatus.NOT_FOUND, message)
class Forbidden(message: String) : ApiException(HttpStatus.FORBIDDEN, message)
class Conflict(message: String) : ApiException(HttpStatus.CONFLICT, message)
class BadRequest(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)
class TooManyRequests(message: String) : ApiException(HttpStatus.TOO_MANY_REQUESTS, message)

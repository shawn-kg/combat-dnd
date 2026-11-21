package app.combatdnd.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * The React app owns these routes. A browser refresh on /combat/abc must still get index.html,
 * so Spring forwards them instead of returning 404. API and static files are untouched.
 */
@Controller
class SpaForwardController {
    @GetMapping("/join/**", "/c/**", "/combat/**")
    fun forward(): String = "forward:/index.html"
}

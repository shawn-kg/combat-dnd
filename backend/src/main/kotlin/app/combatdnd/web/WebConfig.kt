package app.combatdnd.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableScheduling
class WebConfig : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(RequireRequestedWith).addPathPatterns("/api/**")
    }
}

/**
 * CSRF guard: every state-changing API call must carry X-Requested-With. Browsers won't add a custom header
 * to a cross-site form post, so another site can't trigger commands with our cookies (on top of SameSite=Lax).
 */
object RequireRequestedWith : HandlerInterceptor {
    private val safe = setOf("GET", "HEAD", "OPTIONS")

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method in safe || !request.getHeader("X-Requested-With").isNullOrBlank()) return true
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json"
        response.writer.write("""{"message":"Missing the X-Requested-With header."}""")
        return false
    }
}

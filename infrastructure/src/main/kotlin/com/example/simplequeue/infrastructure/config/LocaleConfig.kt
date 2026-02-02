package com.example.simplequeue.infrastructure.config

import com.example.simplequeue.domain.port.UserPreferenceRepository
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor
import java.util.Locale

/**
 * Language/locale configuration with priority:
 * 1. URL parameter ?lang=xx (temporary override, stored in session)
 * 2. User's saved preference (if logged in)
 * 3. Cookie PREFERRED_LANGUAGE (for non-logged-in users)
 * 4. Browser Accept-Language header
 * 5. Default: English
 */
@Configuration
open class LocaleConfig : WebMvcConfigurer {

    @Bean
    open fun localeResolver(userPreferenceRepository: UserPreferenceRepository): LocaleResolver {
        return UserPreferenceLocaleResolver(userPreferenceRepository)
    }

    @Bean
    open fun localeChangeInterceptor(): LocaleChangeInterceptor {
        val interceptor = LocaleChangeInterceptor()
        interceptor.paramName = "lang"
        return interceptor
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(localeChangeInterceptor())
    }
}

/**
 * Custom locale resolver that checks user preferences.
 */
class UserPreferenceLocaleResolver(
    private val userPreferenceRepository: UserPreferenceRepository,
) : LocaleResolver {

    private val logger = LoggerFactory.getLogger(UserPreferenceLocaleResolver::class.java)

    companion object {
        const val LOCALE_SESSION_ATTRIBUTE = "LOCALE"
        const val LOCALE_COOKIE_NAME = "PREFERRED_LANGUAGE"
        const val COOKIE_MAX_AGE = 365 * 24 * 60 * 60 // 1 year
        
        val SUPPORTED_LOCALES = listOf(
            Locale.ENGLISH,           // en
            Locale("no"),             // Norwegian
            Locale.GERMAN,            // de
            Locale("es"),             // Spanish
            Locale.FRENCH,            // fr
            Locale.ITALIAN,           // it
            Locale.JAPANESE,          // ja
            Locale("pl"),             // Polish
            Locale("pt"),             // Portuguese
        )
        
        val DEFAULT_LOCALE: Locale = Locale.ENGLISH
    }

    override fun resolveLocale(request: HttpServletRequest): Locale {
        // 1. Check session (set by LocaleChangeInterceptor when ?lang= is used)
        val sessionLocale = request.session?.getAttribute(LOCALE_SESSION_ATTRIBUTE) as? Locale
        if (sessionLocale != null) {
            return sessionLocale
        }

        // 2. Check user preference (if logged in)
        val userLocale = getUserPreferenceLocale()
        if (userLocale != null) {
            return userLocale
        }

        // 3. Check cookie (for non-logged-in users)
        val cookieLocale = getCookieLocale(request)
        if (cookieLocale != null) {
            return cookieLocale
        }

        // 4. Check Accept-Language header
        val headerLocale = request.locale
        if (headerLocale != null && isSupported(headerLocale)) {
            return normalizeLocale(headerLocale)
        }

        // 5. Default
        return DEFAULT_LOCALE
    }

    override fun setLocale(request: HttpServletRequest, response: HttpServletResponse?, locale: Locale?) {
        val normalizedLocale = locale?.let { normalizeLocale(it) }
        
        // Store in session
        if (normalizedLocale != null) {
            request.session?.setAttribute(LOCALE_SESSION_ATTRIBUTE, normalizedLocale)
        } else {
            request.session?.removeAttribute(LOCALE_SESSION_ATTRIBUTE)
        }

        // Also set cookie for persistence across sessions
        if (response != null && normalizedLocale != null) {
            val cookie = Cookie(LOCALE_COOKIE_NAME, normalizedLocale.language).apply {
                maxAge = COOKIE_MAX_AGE
                path = "/"
                isHttpOnly = true
            }
            response.addCookie(cookie)
        }
    }

    private fun getUserPreferenceLocale(): Locale? {
        return try {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated) {
                val principal = authentication.principal
                val userId = when (principal) {
                    is OAuth2User -> principal.name
                    is String -> if (principal != "anonymousUser") principal else null
                    else -> null
                }
                
                if (userId != null) {
                    val pref = userPreferenceRepository.findByUserId(userId)
                    pref?.preferredLanguage?.let { Locale(it) }
                } else null
            } else null
        } catch (e: Exception) {
            logger.debug("Could not get user preference locale: ${e.message}")
            null
        }
    }

    private fun getCookieLocale(request: HttpServletRequest): Locale? {
        val cookie = request.cookies?.find { it.name == LOCALE_COOKIE_NAME }
        return cookie?.value?.let { lang ->
            val locale = Locale(lang)
            if (isSupported(locale)) locale else null
        }
    }

    private fun isSupported(locale: Locale): Boolean {
        return SUPPORTED_LOCALES.any { it.language == locale.language }
    }

    private fun normalizeLocale(locale: Locale): Locale {
        // Return just the language part to match our supported locales
        return SUPPORTED_LOCALES.find { it.language == locale.language } ?: DEFAULT_LOCALE
    }
}

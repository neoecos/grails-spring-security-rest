package co.tpaga.grails.plugin.springsecurity.rest

import co.tpaga.grails.plugin.springsecurity.rest.apiKey.storage.ApiKeyStorageService
import org.springframework.security.authentication.DisabledException

/**
 * @author Sebastián Ortiz V. <sortiz@tappsi.co>
 */



import com.odobo.grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import groovy.util.logging.Slf4j
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.util.Assert

/**
 * Authenticates a request based on the token passed. This is called by {@link com.odobo.grails.plugin.springsecurity.rest.RestTokenValidationFilter}.
 */
@Slf4j
class RestApiKeyAuthenticationProvider implements AuthenticationProvider {

    ApiKeyStorageService apiKeyStorageService

    /**
     * Returns an authentication object based on the token value contained in the authentication parameter. To do so,
     * it uses a {@link TokenStorageService}.
     * @throws AuthenticationException
     */
    Authentication authenticate(Authentication authentication) throws AuthenticationException {

        Assert.isInstanceOf(RestApiKeyAuthenticationToken, authentication, "Only RestApiKeyAuthenticationToken is supported")
        RestApiKeyAuthenticationToken authenticationRequest = authentication
        RestApiKeyAuthenticationToken authenticationResult = new RestApiKeyAuthenticationToken(authenticationRequest.apiKeyValue)

        if (authenticationRequest.apiKeyValue) {
            log.debug "Trying to validate token ${authenticationRequest.apiKeyValue}"
            def userDetails = apiKeyStorageService.loadUserByApiKey(authenticationRequest.apiKeyValue)
            if (userDetails.isAccountNonLocked() && userDetails.isEnabled()) {
                authenticationResult = new RestApiKeyAuthenticationToken(userDetails, userDetails.password, userDetails.authorities, authenticationRequest.apiKeyValue)
                log.debug "Authentication result for enabled and unlocked user: ${authenticationResult}"
            } else {
                log.debug "Auth failed - user locked or disabled"
                throw new DisabledException("user account locked/disabled")
            }
        }

        return authenticationResult
    }

    boolean supports(Class<?> authentication) {
        return RestApiKeyAuthenticationToken.isAssignableFrom(authentication)
    }
}

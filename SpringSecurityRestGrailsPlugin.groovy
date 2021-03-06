import co.tpaga.grails.plugin.springsecurity.rest.RestApiKeyAuthenticationProvider
import co.tpaga.grails.plugin.springsecurity.rest.RestApiKeyAuthenticationSuccessHandler
import co.tpaga.grails.plugin.springsecurity.rest.RestApiKeyValidationFilter

import co.tpaga.grails.plugin.springsecurity.rest.RestApiKeyAuthenticationSuccessHandler
import co.tpaga.grails.plugin.springsecurity.rest.apiKey.basic.HTTPBasicAuthApiKeyAuthenticationEntryPoint
import co.tpaga.grails.plugin.springsecurity.rest.apiKey.basic.HTTPBasicAuthApiKeyAuthenticationEntryPoint
import co.tpaga.grails.plugin.springsecurity.rest.apiKey.basic.HTTPBasicAuthApiKeyAuthenticationFailureHandler
import co.tpaga.grails.plugin.springsecurity.rest.apiKey.basic.HTTPBasicAuthApiKeyAuthenticationFailureHandler
import co.tpaga.grails.plugin.springsecurity.rest.apiKey.reader.HTTPBasicApiKeyReader
import co.tpaga.grails.plugin.springsecurity.rest.apiKey.storage.GormApiKeyStorageService
import com.odobo.grails.plugin.springsecurity.rest.*
import com.odobo.grails.plugin.springsecurity.rest.credentials.DefaultJsonPayloadCredentialsExtractor
import com.odobo.grails.plugin.springsecurity.rest.credentials.RequestParamsCredentialsExtractor
import com.odobo.grails.plugin.springsecurity.rest.oauth.DefaultOauthUserDetailsService
import com.odobo.grails.plugin.springsecurity.rest.token.bearer.BearerTokenAuthenticationEntryPoint
import com.odobo.grails.plugin.springsecurity.rest.token.bearer.BearerTokenAuthenticationFailureHandler
import com.odobo.grails.plugin.springsecurity.rest.token.bearer.BearerTokenReader
import com.odobo.grails.plugin.springsecurity.rest.token.reader.HttpHeaderTokenReader
import com.odobo.grails.plugin.springsecurity.rest.token.generation.SecureRandomTokenGenerator
import com.odobo.grails.plugin.springsecurity.rest.token.rendering.DefaultRestAuthenticationTokenJsonRenderer
import com.odobo.grails.plugin.springsecurity.rest.token.storage.GormTokenStorageService
import com.odobo.grails.plugin.springsecurity.rest.token.storage.MemcachedTokenStorageService
import com.odobo.grails.plugin.springsecurity.rest.token.storage.GrailsCacheTokenStorageService
import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityUtils
import net.spy.memcached.DefaultHashAlgorithm
import net.spy.memcached.spring.MemcachedClientFactoryBean
import net.spy.memcached.transcoders.SerializingTranscoder
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.savedrequest.NullRequestCache

import javax.servlet.http.HttpServletResponse

class SpringSecurityRestGrailsPlugin {

    String version = "1.5.0-SNAPSHOT"
    String grailsVersion = "2.0 > *"
    List loadAfter = ['springSecurityCore']
    List pluginExcludes = [
        "grails-app/views/**"
    ]

    String title = "Spring Security REST Plugin"
    String author = "Alvaro Sanchez-Mariscal"
    String authorEmail = "alvaro.sanchez@odobo.com"
    String description = 'Implements authentication for REST APIs based on Spring Security. It uses a token-based workflow or API Key'

    String documentation = "http://alvarosanchez.github.io/grails-spring-security-rest/"

    String license = "APACHE"
    def organization = [ name: "Odobo Limited", url: "http://www.odobo.com" ]

    def issueManagement = [ system: "GitHub", url: "https://github.com/alvarosanchez/grails-spring-security-rest/issues" ]
    def scm = [ url: "https://github.com/alvarosanchez/grails-spring-security-rest" ]

    def doWithSpring = {


        def conf = SpringSecurityUtils.securityConfig
        if (!conf || !conf.active) {
            return
        }

        SpringSecurityUtils.loadSecondaryConfig 'DefaultRestSecurityConfig'
        conf = SpringSecurityUtils.securityConfig

        if (!conf.rest.active) {
            return
        }

        else if (conf.rest.token.active) {
            boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true

            if (printStatusMessages) {
                println '\nConfiguring Spring Security REST ...'
            }

            ///*
            SpringSecurityUtils.registerProvider 'restAuthenticationProvider'
            SpringSecurityUtils.registerProvider 'restAuthenticationProvider'

            /* restAuthenticationFilter */
            if(conf.rest.login.active) {
                SpringSecurityUtils.registerFilter 'restAuthenticationFilter', SecurityFilterPosition.FORM_LOGIN_FILTER.order + 1
                SpringSecurityUtils.registerFilter 'restLogoutFilter', SecurityFilterPosition.LOGOUT_FILTER.order - 1

                restAuthenticationFilter(RestAuthenticationFilter) {
                    authenticationManager = ref('authenticationManager')
                    authenticationSuccessHandler = ref('restAuthenticationSuccessHandler')
                    authenticationFailureHandler = ref('restAuthenticationFailureHandler')
                    authenticationDetailsSource = ref('authenticationDetailsSource')
                    credentialsExtractor = ref('credentialsExtractor')
                    endpointUrl = conf.rest.login.endpointUrl
                    tokenGenerator = ref('tokenGenerator')
                    tokenStorageService = ref('tokenStorageService')
                }

                def paramsClosure = {
                    usernamePropertyName = conf.rest.login.usernamePropertyName // username
                    passwordPropertyName = conf.rest.login.passwordPropertyName // password
                }

                if (conf.rest.login.useRequestParamsCredentials) {
                    credentialsExtractor(RequestParamsCredentialsExtractor, paramsClosure)
                } else if (conf.rest.login.useJsonCredentials) {
                    credentialsExtractor(DefaultJsonPayloadCredentialsExtractor, paramsClosure)
                }

                /* restLogoutFilter */
                restLogoutFilter(RestLogoutFilter) {
                    endpointUrl = conf.rest.logout.endpointUrl
                    headerName = conf.rest.token.validation.headerName
                    tokenStorageService = ref('tokenStorageService')
                    tokenReader = ref('tokenReader')
                }
            }


            restAuthenticationSuccessHandler(RestAuthenticationSuccessHandler) {
                renderer = ref('restAuthenticationTokenJsonRenderer')
            }
            restAuthenticationTokenJsonRenderer(DefaultRestAuthenticationTokenJsonRenderer) {
                usernamePropertyName = conf.rest.token.rendering.usernamePropertyName
                tokenPropertyName = conf.rest.token.rendering.tokenPropertyName
                authoritiesPropertyName = conf.rest.token.rendering.authoritiesPropertyName
                useBearerToken = conf.rest.token.validation.useBearerToken
            }

            if( conf.rest.token.validation.useBearerToken ) {
                tokenReader(BearerTokenReader)
                restAuthenticationFailureHandler(BearerTokenAuthenticationFailureHandler)
                restAuthenticationEntryPoint(BearerTokenAuthenticationEntryPoint) {
                    tokenReader = ref('tokenReader')
                }

            } else {
                restAuthenticationEntryPoint(Http403ForbiddenEntryPoint)
                tokenReader(HttpHeaderTokenReader) {
                    headerName = conf.rest.token.validation.headerName
                }
                restAuthenticationFailureHandler(RestAuthenticationFailureHandler) {
                    statusCode = conf.rest.login.failureStatusCode?:HttpServletResponse.SC_UNAUTHORIZED
                }
            }

            /* restTokenValidationFilter */
            SpringSecurityUtils.registerFilter 'restTokenValidationFilter', SecurityFilterPosition.ANONYMOUS_FILTER.order + 1
            SpringSecurityUtils.registerFilter 'restExceptionTranslationFilter', SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order - 5

            restTokenValidationFilter(RestTokenValidationFilter) {
                headerName = conf.rest.token.validation.headerName
                validationEndpointUrl = conf.rest.token.validation.endpointUrl
                active = conf.rest.token.validation.active
                tokenReader = ref('tokenReader')
                enableAnonymousAccess = conf.rest.token.validation.enableAnonymousAccess
                authenticationSuccessHandler = ref('restAuthenticationSuccessHandler')
                authenticationFailureHandler = ref('restAuthenticationFailureHandler')
                restAuthenticationProvider = ref('restAuthenticationProvider')
            }

            restExceptionTranslationFilter(ExceptionTranslationFilter, ref('restAuthenticationEntryPoint'), ref('restRequestCache')) {
                accessDeniedHandler = ref('restAccessDeniedHandler')
                authenticationTrustResolver = ref('authenticationTrustResolver')
                throwableAnalyzer = ref('throwableAnalyzer')
            }

            restRequestCache(NullRequestCache)
            restAccessDeniedHandler(AccessDeniedHandlerImpl) {
                errorPage = null //403
            }

            /* tokenStorageService */
            if (conf.rest.token.storage.useMemcached) {

                Properties systemProperties = System.properties
                systemProperties.put("net.spy.log.LoggerImpl", "net.spy.memcached.compat.log.SLF4JLogger")
                System.setProperties(systemProperties)

                memcachedClient(MemcachedClientFactoryBean) {
                    servers = conf.rest.token.storage.memcached.hosts
                    protocol = 'BINARY'
                    transcoder = new CustomSerializingTranscoder()
                    opTimeout = 1000
                    timeoutExceptionThreshold = 1998
                    hashAlg = DefaultHashAlgorithm.KETAMA_HASH
                    locatorType = 'CONSISTENT'
                    failureMode = 'Redistribute'
                    useNagleAlgorithm = false
                }

                tokenStorageService(MemcachedTokenStorageService) {
                    memcachedClient = ref('memcachedClient')
                    expiration = conf.rest.token.storage.memcached.expiration
                }
            } else if (conf.rest.token.storage.useGrailsCache) {
                tokenStorageService(GrailsCacheTokenStorageService) {
                    grailsCacheManager = ref('grailsCacheManager')
                    cacheName = conf.rest.token.storage.grailsCacheName
                }
            } else if (conf.rest.token.storage.useGorm) {
                tokenStorageService(GormTokenStorageService) {
                    userDetailsService = ref('userDetailsService')
                }
            } else {
                tokenStorageService(GormTokenStorageService) {
                    userDetailsService = ref('userDetailsService')
                }
            }

            /* tokenGenerator */
            tokenGenerator(SecureRandomTokenGenerator)

            /* restAuthenticationProvider */
            restAuthenticationProvider(RestAuthenticationProvider) {
                tokenStorageService = ref('tokenStorageService')
            }

            /* oauthUserDetailsService */
            oauthUserDetailsService(DefaultOauthUserDetailsService) {
                userDetailsService = ref('userDetailsService')
            }

            //*/

            if (printStatusMessages) {
                println '... finished configuring Spring Security REST\n'
            }
        }

        else if (conf.rest.apiKey.active){
            boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true

            if (printStatusMessages) {
                println '\nConfiguring Spring Security REST Basic Auth Api Key...'
            }

            ///*
            SpringSecurityUtils.registerProvider 'restApiKeyAuthenticationProvider'

            /* restApiKeyValidationFilter */

            SpringSecurityUtils.registerFilter 'restApiKeyValidationFilter', SecurityFilterPosition.ANONYMOUS_FILTER.order + 1
            SpringSecurityUtils.registerFilter 'restApiKeyExceptionTranslationFilter', SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order - 5

            restApiKeyValidationFilter(RestApiKeyValidationFilter) {
                validationEndpointUrl = conf.rest.apiKey.validation.endpointUrl
                active = conf.rest.apiKey.validation.active
                apiKeyReader = ref('apiKeyReader')
                enableAnonymousAccess = conf.rest.apiKey.validation.enableAnonymousAccess
                authenticationSuccessHandler = ref('restApiKeyAuthenticationSuccessHandler')
                authenticationFailureHandler = ref('restApiKeyAuthenticationFailureHandler')
                restApiKeyAuthenticationProvider = ref('restApiKeyAuthenticationProvider')
            }


            apiKeyReader(HTTPBasicApiKeyReader)

            restApiKeyAuthenticationSuccessHandler(RestApiKeyAuthenticationSuccessHandler)

            restApiKeyAuthenticationFailureHandler(HTTPBasicAuthApiKeyAuthenticationFailureHandler){
                realm = conf.rest.apiKey.realm
                statusCode = conf.rest.apiKey.failureStatusCode?:HttpServletResponse.SC_UNAUTHORIZED
            }
            restApiKeyAuthenticationEntryPoint(HTTPBasicAuthApiKeyAuthenticationEntryPoint) {
                realm = conf.rest.apiKey.realm
            }

            restApiKeyExceptionTranslationFilter(ExceptionTranslationFilter, ref('restApiKeyAuthenticationEntryPoint'), ref('restApiKeyRequestCache')) {
                accessDeniedHandler = ref('restApiKeyAccessDeniedHandler')
                authenticationTrustResolver = ref('authenticationTrustResolver')
                throwableAnalyzer = ref('throwableAnalyzer')
            }

            restApiKeyRequestCache(NullRequestCache)
            restApiKeyAccessDeniedHandler(AccessDeniedHandlerImpl) {
                errorPage = null //403
            }

            /* apiKeyStorageService */

            if (conf.rest.apiKey.storage.useGorm) {
                apiKeyStorageService(GormApiKeyStorageService) {
                    userDetailsService = ref('userDetailsService')
                }
            } else {
                apiKeyStorageService(GormTokenStorageService) {
                    userDetailsService = ref('userDetailsService')
                }
            }


            /* restAuthenticationProvider */
            restApiKeyAuthenticationProvider(RestApiKeyAuthenticationProvider) {
                apiKeyStorageService = ref('apiKeyStorageService')
            }

            if (printStatusMessages) {
                println '... finished configuring Spring Security REST Basic Api Key\n'
            }
        }

    }


}

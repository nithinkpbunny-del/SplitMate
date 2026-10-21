package com.splitmate.config;

import com.splitmate.auth.AuthCookies;
import com.splitmate.auth.CookieBearerTokenResolver;
import com.splitmate.auth.JsonSecurityErrorHandlers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless JWT security.
 *
 * <ul>
 * <li>The JWT travels in an httpOnly cookie (or an Authorization header for API clients).</li>
 * <li>Because a cookie is sent automatically, CSRF protection is on, using the cookie-to-header pattern:
 * the SPA reads the XSRF-TOKEN cookie and echoes it in X-XSRF-TOKEN. Requests that authenticate with an
 * explicit Authorization header cannot be forged by a browser, so they skip the CSRF check.</li>
 * <li>Everything under /api requires login except the endpoints in {@link #publicEndpoints()}; other paths
 * serve the React app.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthCookies cookies,
			JsonSecurityErrorHandlers errorHandlers) throws Exception {
		RequestMatcher publicEndpoints = publicEndpoints();
		RequestMatcher usesAuthorizationHeader = request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null;

		http.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf.spa().ignoringRequestMatchers(usesAuthorizationHeader))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable())
			.logout(logout -> logout.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(publicEndpoints).permitAll()
				.requestMatchers("/api/**").authenticated()
				.anyRequest().permitAll())
			.oauth2ResourceServer(resourceServer -> resourceServer
				.bearerTokenResolver(new CookieBearerTokenResolver(cookies, publicEndpoints))
				.jwt(Customizer.withDefaults())
				.authenticationEntryPoint(errorHandlers)
				.accessDeniedHandler(errorHandlers))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(errorHandlers)
				.accessDeniedHandler(errorHandlers));
		return http.build();
	}

	/** API endpoints that work without logging in. */
	static RequestMatcher publicEndpoints() {
		PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
		return new OrRequestMatcher(
				path.matcher(HttpMethod.POST, "/api/auth/register"),
				path.matcher(HttpMethod.POST, "/api/auth/login"),
				path.matcher(HttpMethod.POST, "/api/auth/logout"),
				path.matcher(HttpMethod.GET, "/api/auth/csrf"),
				path.matcher(HttpMethod.GET, "/api/invites/{code}"),
				path.matcher(HttpMethod.GET, "/api/health/**"),
				path.matcher("/api/docs/**"),
				path.matcher("/swagger-ui/**"),
				path.matcher("/swagger-ui.html"));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}

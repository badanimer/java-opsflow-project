package com.opsflow.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.opsflow.common.web.WebConfig;

import jakarta.servlet.DispatcherType;

/**
 * Who may call what (see Authentication in docs/backend/backend-design.md).
 *
 * <ul>
 * <li>The server keeps no session. Every request carries its token in {@code Authorization: Bearer}, and
 * {@link TokenAuthenticator} checks the account behind it against the database each time.</li>
 * <li><b>Refuse by default.</b> A request that no rule below allows is refused, so an endpoint that is
 * added without a rule is closed and not open. The rules for the modules are added with the
 * permission module (T-27).</li>
 * <li>Cross-site request forgery protection is off: no cookie authenticates a request, so a foreign site
 * cannot make the browser send one.</li>
 * </ul>
 *
 * These rules exist only in the web application. The console command that creates an administrator
 * starts the application without the web server, where there is no request to check.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

	private static final String API = WebConfig.BASE_PATH;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens, TokenAuthenticator authenticator,
			SecurityErrorHandler errors) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				// The error page of the container only renders a failure that was already decided.
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.POST, API + "/auth/login").permitAll()
					// Sign-up and Forgot password are for people who are not logged in.
					.requestMatchers(HttpMethod.POST, API + "/auth/email-codes", API + "/auth/sign-up", API + "/auth/password-reset").permitAll()
				// A user with no role may ask who they are: the pending page needs the answer.
				.requestMatchers(HttpMethod.GET, API + "/auth/me").authenticated()
				.anyRequest().denyAll())
			.oauth2ResourceServer(server -> server
				.jwt(jwt -> jwt.decoder(tokens.decoder()).jwtAuthenticationConverter(authenticator::authenticate))
				.authenticationEntryPoint(errors)
				.accessDeniedHandler(errors))
			.exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors));
		return http.build();
	}

}

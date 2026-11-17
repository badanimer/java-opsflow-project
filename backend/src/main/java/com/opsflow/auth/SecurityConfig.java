package com.opsflow.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

/**
 * Who may call what (see Authentication in docs/backend/backend-design.md).
 *
 * <ul>
 * <li>The server keeps no session. Every request carries its token in {@code Authorization: Bearer}, and
 * {@link TokenAuthenticator} checks the account behind it against the database each time.</li>
 * <li><b>Refuse by default.</b> A controller method declares who may call it, with {@code @Public},
 * {@code @AnyAccount}, or {@code @RequiresPermission}, and {@link EndpointPermissions} decides from that. A
 * request whose method declares nothing, or that has no method at all, is refused, so an endpoint that
 * is added without a declaration is closed and not open.</li>
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

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens, TokenAuthenticator authenticator,
			SecurityErrorHandler errors, EndpointPermissions permissions, Environment environment) throws Exception {
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
				// The second dispatch of an event stream that was already allowed, when the stream ends.
				.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
				// Actuator, on the management port only: the health check and the counters. The reverse proxy
				// passes nothing to that port, so it is reachable from the machine and its containers only.
				.requestMatchers(request -> onManagementPort(request.getLocalPort(), environment)).permitAll()
				// Every other request is decided by what its controller method declares.
				.anyRequest().access(permissions))
			.oauth2ResourceServer(server -> server
				.jwt(jwt -> jwt.decoder(tokens.decoder()).jwtAuthenticationConverter(authenticator::authenticate))
				.authenticationEntryPoint(errors)
				.accessDeniedHandler(errors))
			.exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors));
		return http.build();
	}

	/**
	 * Whether a request came in on the management port. The port is read when the request comes, because the
	 * server publishes the port it really took ({@code local.management.port}) only after it started. With
	 * no management port of its own, Actuator would answer on the port of the API, and nothing is opened.
	 */
	static boolean onManagementPort(int localPort, Environment environment) {
		Integer management = environment.getProperty("local.management.port", Integer.class);
		Integer server = environment.getProperty("local.server.port", Integer.class);
		return management != null && management > 0 && !management.equals(server) && management == localPort;
	}

}

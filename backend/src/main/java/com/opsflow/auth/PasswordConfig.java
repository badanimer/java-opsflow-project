package com.opsflow.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * How passwords are hashed. It is apart from {@link SecurityConfig}, because the console command that
 * creates an administrator hashes a password without the web application.
 */
@Configuration
public class PasswordConfig {

	/** BCrypt, stored with the {@code {bcrypt}} prefix, so that another algorithm can be added later. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}

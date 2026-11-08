package com.opsflow.common.web;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web conventions that every controller shares (see docs/api/api-design.md).
 *
 * <ul>
 * <li>Every {@code @RestController} of the application is served under {@value #BASE_PATH}, so a
 * controller declares only {@code /orders}, never the prefix. A breaking change gets a new prefix.</li>
 * <li>The one {@link Clock} of the application, in UTC.</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	public static final String BASE_PATH = "/api/v1";

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(BASE_PATH, type -> type.isAnnotationPresent(RestController.class)
				&& type.getPackageName().startsWith("com.opsflow"));
	}

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}

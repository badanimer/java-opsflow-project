package com.opsflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OpsflowBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpsflowBackendApplication.class, args);
	}

}

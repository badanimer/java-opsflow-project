package com.opsflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.opsflow.auth.CreateAdminCommand;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OpsflowBackendApplication {

	/**
	 * Starts the web application. With the first argument {@code create-admin} it runs the console
	 * command that creates an administrator instead, without the web server, and exits.
	 */
	public static void main(String[] args) {
		if (args.length > 0 && CreateAdminCommand.NAME.equals(args[0])) {
			System.exit(CreateAdminCommand.run(OpsflowBackendApplication.class, args));
		}
		SpringApplication.run(OpsflowBackendApplication.class, args);
	}

}

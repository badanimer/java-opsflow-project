package com.opsflow.demo;

import java.util.Arrays;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.opsflow.auth.AdminCreator;
import com.opsflow.auth.CreateAdminCommand;
import com.opsflow.auth.Prompt;

/**
 * The console command that fills a new deployment with demo data (see Demo data in
 * docs/backend/backend-design.md):
 *
 * <pre>docker compose run --rm backend seed-demo</pre>
 *
 * It asks for one password for the five demo accounts, typed twice with the characters hidden, as
 * {@code create-admin} does, so no password is written in the repository or a configuration. It makes the
 * accounts in the database, and then everything else through the API of the running backend, whose address
 * is {@code OPSFLOW_DEMO_API_URL} ({@code http://localhost:8080} by default; the deployment sets
 * {@code http://backend:8080}).
 */
public final class SeedDemoCommand {

	public static final String NAME = "seed-demo";

	private SeedDemoCommand() {
	}

	/** @return the exit code of the process: 0 when the demo data was made */
	public static int run(Class<?> application, String[] args) {
		Prompt prompt = Prompt.ofTerminal();
		if (prompt == null) {
			System.err.println("seed-demo must be run from a terminal, so that the password can be typed without being shown.");
			return 1;
		}
		String server = System.getenv().getOrDefault("OPSFLOW_DEMO_API_URL", "http://localhost:8080");
		prompt.say("Fill OpsFlow with demo data, through the backend at " + server + ".");
		prompt.say("The accounts adam, mary, olive, otto, and vera will share the password typed now.");
		char[] password = prompt.secret("Password for the demo accounts (8 to 24 characters, with a letter and a digit)");
		char[] again = prompt.secret("Password again");
		try {
			if (password == null || again == null) {
				prompt.say("Cancelled. Nothing was made.");
				return 1;
			}
			if (!Arrays.equals(password, again)) {
				prompt.say("The two passwords differ. Nothing was made.");
				return 1;
			}
			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(application).web(WebApplicationType.NONE)
					.properties("opsflow.jobs.enabled=false", "spring.main.banner-mode=off", "logging.level.root=warn",
							"logging.level.org.springframework.boot.SpringApplication=off")
					.run(args)) {
				DemoSeeder.Summary made = context.getBean(DemoSeeder.class).seed(new String(password), server, prompt::say);
				prompt.say("Done: " + made.items() + " warehouse items, " + made.products() + " products, " + made.orders() + " orders and "
						+ made.refusedOrders() + " refused, and " + made.tickets() + " tickets on every screen.");
				prompt.say("Log in as adam, mary, olive, otto, or vera with the password you typed.");
				return 0;
			}
			catch (AdminCreator.Refused | DemoClient.Failed refused) {
				prompt.say(refused.getMessage());
				prompt.say("The demo data is meant for a new deployment. Stopped.");
				return 1;
			}
			catch (RuntimeException startFailed) {
				prompt.say(CreateAdminCommand.describeStartFailure(startFailed));
				return 1;
			}
		}
		finally {
			if (password != null) {
				Arrays.fill(password, ' ');
			}
			if (again != null) {
				Arrays.fill(again, ' ');
			}
		}
	}

}

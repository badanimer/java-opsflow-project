package com.opsflow.auth;

import java.util.Arrays;
import java.util.Set;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.validation.Limits;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The console command that creates an administrator on the server (T-24; see First administrator in
 * docs/database/database-design.md):
 *
 * <pre>java -jar opsflow-backend.jar create-admin</pre>
 *
 * A new user has no role and only an administrator can appoint one, so the first administrator cannot
 * be made through the API. Whoever can run this command already controls the server, so it needs no
 * login, and it is not reachable over HTTP: it starts the application without the web server. It can be
 * run again at any time, for another administrator.
 *
 * <b>The password is typed and kept nowhere.</b> It is asked for twice at the terminal with the typed
 * characters hidden. It is not an argument, an environment variable, or a file, so it cannot stay in a
 * shell history or a configuration, and the command refuses to run without a real terminal. It is never
 * logged or printed.
 *
 * The values are checked with the rules of sign-up, and the password is hashed before the transaction of
 * {@link AdminCreator} starts.
 */
@Component
public class CreateAdminCommand {

	public static final String NAME = "create-admin";

	static final int OK = 0;
	static final int FAILED = 1;

	private record EmailValue(@NotBlank @Email @Size(max = Limits.EMAIL) String email) {
	}

	private final PasswordHasher hasher;
	private final AdminCreator creator;
	private final TransactionRetry retry;
	private final Validator validator;

	CreateAdminCommand(PasswordHasher hasher, AdminCreator creator, TransactionRetry retry, Validator validator) {
		this.hasher = hasher;
		this.creator = creator;
		this.retry = retry;
		this.validator = validator;
	}

	/**
	 * Starts the application without the web server and without the scheduled jobs, runs the command at
	 * the terminal, and stops the application.
	 *
	 * @return the exit code of the process: 0 when the administrator was created
	 */
	public static int run(Class<?> application, String[] args) {
		Prompt prompt = Prompt.ofTerminal();
		if (prompt == null) {
			System.err.println("create-admin must be run from a terminal, so that the password can be typed without being shown.");
			System.err.println("Do not pipe input into it, and do not start it from an IDE or a build tool.");
			return FAILED;
		}
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(application)
				.web(WebApplicationType.NONE)
				// A failed start is reported below in one sentence, so the framework does not print its stack trace.
				.properties("opsflow.jobs.enabled=false", "spring.main.banner-mode=off", "logging.level.root=warn",
						"logging.level.org.springframework.boot.SpringApplication=off",
						"logging.level.org.springframework.context.annotation.AnnotationConfigApplicationContext=error")
				.run(args)) {
			return context.getBean(CreateAdminCommand.class).execute(prompt);
		}
		catch (RuntimeException startFailed) {
			prompt.say(describeStartFailure(startFailed));
			prompt.say("Nothing was created.");
			return FAILED;
		}
	}

	/**
	 * Why the application did not start, in one sentence for the person at the console. The usual reason is
	 * an environment variable that this terminal does not have. The text never holds the value of a variable.
	 */
	public static String describeStartFailure(Throwable failure) {
		Throwable root = failure;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String message = String.valueOf(root.getMessage());
		java.util.regex.Matcher missing = java.util.regex.Pattern.compile("Could not resolve placeholder '([A-Z0-9_]+)'").matcher(message);
		if (missing.find()) {
			return "The application could not start: the environment variable " + missing.group(1) + " is not set in this terminal. "
					+ "If it was set a moment ago, open a new terminal (restart the IDE if the terminal is inside it) and run the command again.";
		}
		if (message.contains("Access denied for user") || message.contains("Communications link failure")) {
			return "The application could not start: it could not connect to MySQL. Check that the MySQL service is running "
					+ "and that the OPSFLOW_DB_* and OPSFLOW_MIGRATION_* variables hold the right accounts.";
		}
		if (message.contains("OPSFLOW_TOKEN_SIGNING_KEY")) {
			return "The application could not start: " + message + ".";
		}
		return "The application could not start (" + root.getClass().getSimpleName() + "). Start it normally to see the full error.";
	}

	/** Asks for the values, checks them, and creates the administrator. Every message goes to the prompt. */
	int execute(Prompt prompt) {
		prompt.say("Create an OpsFlow administrator.");
		String username = prompt.line("Username");
		String email = prompt.line("Email");
		char[] password = prompt.secret("Password (8 to 24 characters, with a letter and a digit)");
		char[] again = prompt.secret("Password again");
		try {
			if (username == null || email == null || password == null || again == null) {
				prompt.say("Cancelled. Nothing was created.");
				return FAILED;
			}
			username = username.trim();
			email = email.trim();
			if (!Arrays.equals(password, again)) {
				prompt.say("The two passwords differ. Nothing was created.");
				return FAILED;
			}
			String typed = new String(password);
			try {
				AccountRules.checkUsername("Username", username);
				checkEmail(email);
				AccountRules.checkPassword("Password", typed);
			}
			catch (ApiException invalid) {
				prompt.say(describe(invalid) + " Nothing was created.");
				return FAILED;
			}
			String passwordHash = hasher.hash(typed);
			String name = username;
			String address = email;
			long id;
			try {
				id = retry.run(() -> creator.create(name, address, passwordHash));
			}
			catch (AdminCreator.Refused | ApiException refused) {
				prompt.say(refused.getMessage() + " Nothing was created.");
				return FAILED;
			}
			prompt.say("Administrator '" + username + "' was created with the id " + id + ". It can log in now.");
			return OK;
		}
		finally {
			// The typed characters are not left in memory longer than needed.
			if (password != null) {
				Arrays.fill(password, '\0');
			}
			if (again != null) {
				Arrays.fill(again, '\0');
			}
		}
	}

	private void checkEmail(String email) {
		Set<ConstraintViolation<EmailValue>> violations = validator.validate(new EmailValue(email));
		if (!violations.isEmpty()) {
			throw ApiException.invalid("Email", "must be a valid email address of at most " + Limits.EMAIL + " characters");
		}
	}

	/** "Username must be ...", from the refusal that names the field. */
	private static String describe(ApiException invalid) {
		Object errors = invalid.extras().get("errors");
		if (errors instanceof Iterable<?> list) {
			for (Object error : list) {
				if (error instanceof java.util.Map<?, ?> entry) {
					return entry.get("field") + " " + entry.get("message") + ".";
				}
			}
		}
		return invalid.getMessage() + ".";
	}

}

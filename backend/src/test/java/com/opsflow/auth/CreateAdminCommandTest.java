package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.opsflow.support.MySqlIntegrationTest;
import com.opsflow.support.RaceRunner;

/**
 * The console command that creates an administrator (T-24), against the MySQL container. The
 * application runs here without the web server, as the command starts it, and the terminal is played by
 * the test.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class CreateAdminCommandTest extends MySqlIntegrationTest {

	private static final String PASSWORD = "correct-horse-1";

	/** A terminal that answers from a list and keeps everything that was printed. */
	private static final class Terminal implements Prompt {

		private final Deque<String> answers = new ArrayDeque<>();
		private final StringBuilder printed = new StringBuilder();

		/** The answers in the order the command asks: username, email, password, password again. */
		Terminal(String... answers) {
			this.answers.addAll(List.of(answers));
		}

		@Override
		public String line(String label) {
			printed.append(label).append(": ").append('\n');
			return answers.poll();
		}

		@Override
		public char[] secret(String label) {
			printed.append(label).append(": ").append('\n');
			String answer = answers.poll();
			return answer == null ? null : answer.toCharArray();
		}

		@Override
		public void say(String message) {
			printed.append(message).append('\n');
		}

		String printed() {
			return printed.toString();
		}
	}

	@Autowired
	CreateAdminCommand command;

	@Autowired
	LoginService loginService;

	private int users() {
		return root().queryForObject("SELECT COUNT(*) FROM users WHERE is_system = FALSE", Integer.class);
	}

	@Test
	void theCommandCreatesAnAdministratorWhoCanLogIn() {
		Terminal terminal = new Terminal("root", "root@example.com", PASSWORD, PASSWORD);

		assertThat(command.execute(terminal)).isZero();

		Map<String, Object> user = root().queryForMap("SELECT id, email, password_hash, status, deleted, is_system FROM users WHERE username = 'root'");
		long id = ((Number) user.get("id")).longValue();
		assertThat(user.get("email")).isEqualTo("root@example.com");
		assertThat((String) user.get("password_hash")).startsWith("{bcrypt}").doesNotContain(PASSWORD);
		assertThat(user.get("status")).isEqualTo("ACTIVE");
		assertThat(root().queryForList("SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?",
				String.class, id)).containsExactly("ADMIN");

		// Nobody is logged in at the console: both log rows have no operator and the remark 'console'.
		List<Map<String, Object>> logs = root().queryForList(
				"SELECT action, category, operator_id, remark, CAST(after_data AS CHAR) AS after_data FROM operation_logs WHERE target_id = ? ORDER BY id", id);
		assertThat(logs).extracting(row -> row.get("action")).containsExactly("USER_REGISTERED", "ROLE_GRANTED");
		assertThat(logs).allSatisfy(row -> {
			assertThat(row.get("operator_id")).isNull();
			assertThat(row.get("remark")).isEqualTo("console");
			assertThat((String) row.get("after_data")).doesNotContain(PASSWORD).doesNotContain("bcrypt");
		});
		assertThat(logs.get(1).get("category")).isEqualTo("ROLE");
		assertThat((String) logs.get(1).get("after_data")).contains("\"roleCode\": \"ADMIN\"");

		// The password was typed, and is neither printed nor kept anywhere but as a hash.
		assertThat(terminal.printed()).contains("was created").doesNotContain(PASSWORD);
		assertThat(loginService.login("root", PASSWORD, "127.0.0.1").value()).isNotBlank();
	}

	/** Whoever can run the command controls the server, so it makes another administrator at any time. */
	@Test
	void theCommandCanBeRunAgainForAnotherAdministrator() {
		assertThat(command.execute(new Terminal("root", "root@example.com", PASSWORD, PASSWORD))).isZero();
		assertThat(command.execute(new Terminal("second", "second@example.com", PASSWORD, PASSWORD))).isZero();

		assertThat(root().queryForObject("SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.code = 'ADMIN'",
				Integer.class)).isEqualTo(2);
	}

	@Test
	void twoPasswordsThatDifferCreateNothing() {
		Terminal terminal = new Terminal("root", "root@example.com", PASSWORD, "correct-horse-2");

		assertThat(command.execute(terminal)).isEqualTo(1);

		assertThat(terminal.printed()).contains("The two passwords differ").doesNotContain(PASSWORD).doesNotContain("correct-horse-2");
		assertThat(users()).isZero();
	}

	/** The values are checked with the rules of sign-up, and the message names the field and never the password. */
	@Test
	void aValueThatBreaksARuleOfSignUpCreatesNothing() {
		Terminal weak = new Terminal("root", "root@example.com", "onlyletters", "onlyletters");
		Terminal name = new Terminal("root admin", "root@example.com", PASSWORD, PASSWORD);
		Terminal address = new Terminal("root", "not-an-address", PASSWORD, PASSWORD);

		assertThat(command.execute(weak)).isEqualTo(1);
		assertThat(command.execute(name)).isEqualTo(1);
		assertThat(command.execute(address)).isEqualTo(1);

		assertThat(weak.printed()).contains("Password must be 8 to 24 characters").doesNotContain("onlyletters");
		assertThat(name.printed()).contains("Username must be 3 to 64 letters and digits");
		assertThat(address.printed()).contains("Email must be a valid email address");
		assertThat(users()).isZero();
		assertThat(root().queryForObject("SELECT COUNT(*) FROM operation_logs", Integer.class)).isZero();
	}

	@Test
	void aUsernameOrAnAddressThatIsTakenCreatesNothing() {
		assertThat(command.execute(new Terminal("root", "root@example.com", PASSWORD, PASSWORD))).isZero();
		Terminal sameName = new Terminal("ROOT", "other@example.com", PASSWORD, PASSWORD);
		Terminal sameAddress = new Terminal("other", "root@example.com", PASSWORD, PASSWORD);
		Terminal system = new Terminal("system", "other@example.com", PASSWORD, PASSWORD);

		assertThat(command.execute(sameName)).isEqualTo(1);
		assertThat(command.execute(sameAddress)).isEqualTo(1);
		assertThat(command.execute(system)).isEqualTo(1);

		assertThat(sameName.printed()).contains("This username is already used");
		assertThat(sameAddress.printed()).contains("This email address already has an account");
		assertThat(users()).isEqualTo(1);
	}

	/** The command runs after the roles are seeded. Without them it says what to do and creates no user. */
	@Test
	void withoutTheAdminRoleTheCommandSaysSoAndCreatesNothing() {
		root().update("DELETE FROM roles WHERE code = 'ADMIN'");
		Terminal terminal = new Terminal("root", "root@example.com", PASSWORD, PASSWORD);

		assertThat(command.execute(terminal)).isEqualTo(1);

		assertThat(terminal.printed()).contains("The ADMIN role does not exist");
		assertThat(users()).isZero();
	}

	@Test
	void inputThatEndsEarlyCreatesNothing() {
		Terminal terminal = new Terminal("root", "root@example.com");

		assertThat(command.execute(terminal)).isEqualTo(1);

		assertThat(terminal.printed()).contains("Cancelled");
		assertThat(users()).isZero();
	}

	/** A start that fails is told in one sentence that names the missing variable, and never a value. */
	@Test
	void aFailedStartIsDescribedInOneSentence() {
		RuntimeException missing = new IllegalStateException("Error creating bean", new IllegalArgumentException(
				"Could not resolve placeholder 'OPSFLOW_TOKEN_SIGNING_KEY' in value \"${OPSFLOW_TOKEN_SIGNING_KEY}\""));
		RuntimeException database = new IllegalStateException("Error creating bean",
				new java.sql.SQLException("Access denied for user 'opsflow_app'@'localhost' (using password: YES)"));
		RuntimeException other = new IllegalStateException("something with a secret-value inside");

		assertThat(CreateAdminCommand.describeStartFailure(missing))
				.contains("the environment variable OPSFLOW_TOKEN_SIGNING_KEY is not set in this terminal").contains("open a new terminal");
		assertThat(CreateAdminCommand.describeStartFailure(database)).contains("could not connect to MySQL").doesNotContain("opsflow_app");
		assertThat(CreateAdminCommand.describeStartFailure(other)).contains("IllegalStateException").doesNotContain("secret-value");
	}

	/** Two commands at the same moment with one username: the unique key lets one through. */
	@Test
	void twoCommandsAtOnceWithOneUsernameCreateOneAdministrator() {
		AtomicInteger next = new AtomicInteger();

		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(2,
				() -> command.execute(new Terminal("root", "root" + next.incrementAndGet() + "@example.com", PASSWORD, PASSWORD)));

		assertThat(outcomes).extracting(RaceRunner.Outcome::value).containsExactlyInAnyOrder(0, 1);
		assertThat(users()).isEqualTo(1);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM user_roles", Integer.class)).isEqualTo(1);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM operation_logs", Integer.class)).isEqualTo(2);
	}

	/** Two different administrators at the same moment are both valid. */
	@Test
	void twoCommandsAtOnceForDifferentPeopleCreateTwoAdministrators() {
		AtomicInteger next = new AtomicInteger();

		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(2, () -> {
			int i = next.incrementAndGet();
			return command.execute(new Terminal("root" + i, "root" + i + "@example.com", PASSWORD, PASSWORD));
		});

		assertThat(outcomes).extracting(RaceRunner.Outcome::value).containsExactly(0, 0);
		assertThat(users()).isEqualTo(2);
	}

}

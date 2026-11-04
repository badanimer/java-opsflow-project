package com.opsflow.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base class of the integration tests. They run against a real MySQL 8.4 in a container, because an
 * in-memory database does not lock like MySQL.
 *
 * The container is started once for the whole test run and set up the way the real server is: the
 * two accounts of ENVIRONMENT_SETUP.md, the Flyway migrations run as the migration account, and then
 * db/admin/restrict-app-privileges.sql. The application connects as the application account, so a
 * missing grant fails here as it would in production. The passwords are made at random for each run.
 *
 * Every test starts with all tables empty. A race test lets several threads commit, so it cannot be
 * rolled back at the end.
 */
@SpringBootTest
public abstract class MySqlIntegrationTest {

	private static final String DATABASE = "opsflow";
	private static final String MIGRATOR = "opsflow_migrator";
	private static final String APP = "opsflow_app";
	private static final String JDBC_PARAMETERS = "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=utf8";
	private static final Path PRIVILEGE_SCRIPT = Path.of("..", "db", "admin", "restrict-app-privileges.sql");

	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName(DATABASE);
	private static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();
	private static final String APP_PASSWORD = UUID.randomUUID().toString();
	private static final JdbcTemplate ROOT;

	static {
		MYSQL.start();
		ROOT = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl(), "root", MYSQL.getPassword()));
		createAccounts();
		Flyway.configure()
			.dataSource(jdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
			.locations("classpath:db/migration")
			.load()
			.migrate();
		restrictAppPrivileges();
	}

	@DynamicPropertySource
	static void connection(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MySqlIntegrationTest::jdbcUrl);
		registry.add("spring.datasource.username", () -> APP);
		registry.add("spring.datasource.password", () -> APP_PASSWORD);
		// The schema is already built above, and the privileges can only be set after it.
		registry.add("spring.flyway.enabled", () -> "false");
	}

	/** The application account, with the limited rights of production. */
	@Autowired
	protected JdbcTemplate jdbc;

	/** The root account of the container, for setting up and checking data that the application may not touch. */
	protected static JdbcTemplate root() {
		return ROOT;
	}

	@BeforeEach
	void emptyAllTables() {
		List<String> tables = ROOT.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'",
				String.class, DATABASE);
		ROOT.execute((java.sql.Connection connection) -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				for (String table : tables) {
					statement.execute("TRUNCATE TABLE `" + table + "`");
				}
				statement.execute("SET FOREIGN_KEY_CHECKS = 1");
			}
			return null;
		});
	}

	private static String jdbcUrl() {
		return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + DATABASE + JDBC_PARAMETERS;
	}

	private static void createAccounts() {
		ROOT.execute("CREATE USER '" + MIGRATOR + "'@'%' IDENTIFIED BY '" + MIGRATOR_PASSWORD + "'");
		ROOT.execute("CREATE USER '" + APP + "'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
		ROOT.execute("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON " + DATABASE + ".* TO '" + MIGRATOR + "'@'%'");
		ROOT.execute("GRANT SELECT, INSERT, UPDATE ON " + DATABASE + ".* TO '" + APP + "'@'%'");
	}

	/** Runs the real privilege script. It names the account host 'localhost', and in the container the host is '%'. */
	private static void restrictAppPrivileges() {
		try {
			String script = Files.readString(PRIVILEGE_SCRIPT, StandardCharsets.UTF_8).replace("'localhost'", "'%'");
			try (Connection connection = ROOT.getDataSource().getConnection()) {
				ScriptUtils.executeSqlScript(connection, new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)));
			}
		}
		catch (IOException | SQLException e) {
			throw new IllegalStateException("Could not apply " + PRIVILEGE_SCRIPT, e);
		}
	}

}

package com.opsflow.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T-09: Java handles every time in UTC. A call that reads the default time zone of the machine would
 * write a different time on a machine in another zone, so none may appear in the application code. Take
 * the time from the {@code Clock} bean and convert with {@link UtcTime}.
 */
class UtcSourceRuleTest {

	private static final Path MAIN = Path.of("src", "main", "java");

	private static final List<Pattern> FORBIDDEN = List.of(
			Pattern.compile("LocalDateTime\\.now\\(\\s*\\)"),
			Pattern.compile("LocalDate\\.now\\(\\s*\\)"),
			Pattern.compile("LocalTime\\.now\\(\\s*\\)"),
			Pattern.compile("ZonedDateTime\\.now\\(\\s*\\)"),
			Pattern.compile("OffsetDateTime\\.now\\(\\s*\\)"),
			Pattern.compile("ZoneId\\.systemDefault"),
			Pattern.compile("TimeZone\\.getDefault"),
			Pattern.compile("new\\s+Date\\s*\\("),
			Pattern.compile("Calendar\\.getInstance"),
			Pattern.compile("Timestamp\\.valueOf"),
			Pattern.compile("System\\.currentTimeMillis"));

	@Test
	void noCodeReadsTheDefaultTimeZoneOfTheMachine() throws IOException {
		List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MAIN)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
				String[] lines = Files.readString(file).split("\n");
				for (int i = 0; i < lines.length; i++) {
					String code = lines[i].strip();
					if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
						continue; // a comment may name the call it forbids
					}
					for (Pattern pattern : FORBIDDEN) {
						if (pattern.matcher(code).find()) {
							found.add(MAIN.relativize(file) + ":" + (i + 1) + " " + code);
						}
					}
				}
			}
		}

		assertThat(found).as("calls that depend on the default time zone").isEmpty();
	}

}

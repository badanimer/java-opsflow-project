package com.opsflow.auth;

import java.io.Console;

/** The terminal of the person who runs a console command. Tests replace it. */
public interface Prompt {

	/** Asks for a line of text and returns it, or null at the end of the input. */
	String line(String label);

	/** Asks for a secret without showing what is typed, or returns null at the end of the input. */
	char[] secret(String label);

	void say(String message);

	/**
	 * The real terminal, or null when the process has none (its input or output is a file or a pipe). A
	 * secret is never read from anything but a terminal: it would be shown while it is typed, and a pipe
	 * means that the secret was written into a command or a file.
	 */
	static Prompt ofTerminal() {
		Console console = System.console();
		if (console == null) {
			return null;
		}
		return new Prompt() {

			@Override
			public String line(String label) {
				return console.readLine("%s: ", label);
			}

			@Override
			public char[] secret(String label) {
				return console.readPassword("%s: ", label);
			}

			@Override
			public void say(String message) {
				console.printf("%s%n", message);
				console.flush();
			}
		};
	}

}

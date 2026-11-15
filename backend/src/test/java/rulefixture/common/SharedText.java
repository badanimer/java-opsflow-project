package rulefixture.common;

/** Allowed: shared code that depends on no module. */
public final class SharedText {

	private SharedText() {
	}

	public static String trim(String text) {
		return text.trim();
	}

}

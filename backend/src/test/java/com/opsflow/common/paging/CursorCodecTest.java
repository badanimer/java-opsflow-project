package com.opsflow.common.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

class CursorCodecTest {

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-04T03:00:00.123Z", "Widget", "with:colon:inside", "", "日本語 – ünï", "a/b+c=d" })
	void aCursorSurvivesTheRoundTrip(String key) {
		Cursor cursor = new Cursor(key, 9_007_199_254_740_993L);

		String encoded = CursorCodec.encode(cursor);

		assertThat(CursorCodec.decode(encoded)).isEqualTo(cursor);
	}

	@Test
	void theEncodedFormCanBeUsedInAUrlWithoutEscaping() {
		String encoded = CursorCodec.encode(new Cursor("a/b+c=d??>>", 1));

		assertThat(encoded).matches("[A-Za-z0-9_-]+");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "!!!not-base64!!!", "bm9jb2xvbg", "OmtleQ", "YWJjOmtleQ", "LTU6a2V5" })
	void aStringThatIsNotACursorIsRefused(String text) {
		// "bm9jb2xvbg" = "nocolon", "OmtleQ" = ":key" (no id), "YWJjOmtleQ" = "abc:key" (id not a number), "LTU6a2V5" = "-5:key"
		assertThatThrownBy(() -> CursorCodec.decode(text))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void nullAndTooLongStringsAreRefused() {
		assertThatThrownBy(() -> CursorCodec.decode(null)).isInstanceOf(ApiException.class);
		String huge = Base64.getUrlEncoder().withoutPadding().encodeToString(("1:" + "x".repeat(2000)).getBytes(StandardCharsets.UTF_8));
		assertThatThrownBy(() -> CursorCodec.decode(huge)).isInstanceOf(ApiException.class);
	}

}

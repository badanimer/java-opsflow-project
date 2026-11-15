package com.opsflow.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/**
 * Item images (T-42; see Item images in docs/database/database-design.md): the upload is judged by its
 * first bytes and its size, stored under a random name, and served without a token.
 */
class ImageTest extends WarehouseIntegrationSupport {

	@BeforeEach
	void people() {
		account("mary", "MANAGER");
		account("olivia", "OPERATOR");
	}

	@Test
	void aJpegAPngAndAWebpAreStoredUnderRandomNamesWhateverTheyAreCalled() throws Exception {
		String[][] uploads = { { "photo.png", "jpg" }, { "photo.jpg", "png" }, { "photo", "webp" } };
		byte[][] contents = { JPEG, PNG, WEBP };
		for (int i = 0; i < uploads.length; i++) {
			String answer = upload("mary", contents[i], uploads[i][0], "application/octet-stream").andExpect(status().isCreated())
					.andReturn().getResponse().getContentAsString();
			String path = JsonPath.read(answer, "$.path");
			assertThat(path).matches("items/[0-9a-f-]{36}\\." + uploads[i][1]);
			assertThat(JsonPath.<String>read(answer, "$.url")).isEqualTo("/api/v1/images/" + path);
			assertThat(Files.readAllBytes(IMAGE_DIR.resolve(path))).isEqualTo(contents[i]);
		}
	}

	@Test
	void aFileThatIsNotOneOfTheThreeTypesIsRefusedWhateverItsNameAndHeaderSay() throws Exception {
		upload("mary", "just some text".getBytes(), "image.png", "image/png").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("IMAGE_TYPE_NOT_ALLOWED"));
		upload("mary", new byte[] { 'G', 'I', 'F', '8', '9', 'a' }, "anim.gif", "image/gif").andExpect(status().isBadRequest());
		upload("mary", new byte[0], "empty.jpg", "image/jpeg").andExpect(status().isBadRequest());
	}

	@Test
	void anImageLargerThan2MbIsRefused() throws Exception {
		byte[] large = Arrays.copyOf(JPEG, 2 * 1024 * 1024 + 1);
		upload("mary", large, "big.jpg", "image/jpeg").andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
		byte[] exactly = Arrays.copyOf(JPEG, 2 * 1024 * 1024);
		upload("mary", exactly, "fits.jpg", "image/jpeg").andExpect(status().isCreated());
	}

	@Test
	void anImageIsServedWithoutATokenWithItsTypeAndALongCache() throws Exception {
		String path = JsonPath.read(upload("mary", WEBP, "a.webp", "image/webp").andReturn().getResponse().getContentAsString(), "$.path");

		mvc.perform(get("/api/v1/images/" + path)).andExpect(status().isOk())
				.andExpect(content().contentType("image/webp"))
				.andExpect(content().bytes(WEBP))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=31536000")))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
	}

	@Test
	void anyOtherImageNameIsNotFound() throws Exception {
		mvc.perform(get("/api/v1/images/items/3f2b8c1e-0000-0000-0000-000000000000.png")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/images/items/secret.txt")).andExpect(status().isNotFound());
		// An encoded slash is refused before any controller, by the firewall of Spring Security.
		mvc.perform(get("/api/v1/images/items/..%2F..%2Fapplication.yml")).andExpect(status().isBadRequest());
	}

	@Test
	void onlyAManagerOrAnAdministratorUploadsAndOnlyAsMultipart() throws Exception {
		upload("olivia", JPEG, "a.jpg", "image/jpeg").andExpect(status().isForbidden());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/item-images")
				.header("Authorization", "Bearer " + token("mary")).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

}

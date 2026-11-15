package com.opsflow.warehouse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

/**
 * The files of the item images (T-42; see Item images in docs/database/database-design.md).
 *
 * An upload is checked before anything is written: at most the configured size, and a JPEG, PNG, or WebP
 * judged by its first bytes, never by its name or by the type the browser sent. It is stored under a
 * random name, {@code items/<uuid>.<extension>}, in the folder of {@code opsflow.warehouse.image-dir},
 * and the path is what an item stores. A path is accepted only in exactly that form, so no request can
 * name a file outside the folder. Files are never deleted: history refers to them.
 *
 * The store does no database work, so it is called before the transaction that refers to a file, and a
 * failed transaction leaves an unused file and never a row that points to nothing.
 */
@Component
public class ImageStore {

	/** A stored image, by its path in the folder. */
	public record StoredImage(String path, String url) {
	}

	/** An image ready to be sent: its file and its content type. */
	public record ImageFile(Path file, String contentType) {
	}

	static final String URL_PREFIX = "/api/v1/images/";
	private static final String FOLDER = "items";
	private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");
	private static final Pattern PATH = Pattern.compile(FOLDER + "/" + FILE_NAME.pattern());

	private final Path root;
	private final long maxBytes;

	ImageStore(OpsflowProperties properties) {
		this.root = Path.of(properties.warehouse().imageDir()).toAbsolutePath().normalize();
		this.maxBytes = properties.warehouse().imageMaxBytes();
	}

	/** Checks and stores an upload. */
	public StoredImage store(byte[] content) {
		if (content.length > maxBytes) {
			throw tooLarge();
		}
		String extension = extensionOf(content);
		if (extension == null) {
			throw new ApiException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED, "The file must be a JPEG, PNG, or WebP image");
		}
		String path = FOLDER + "/" + UUID.randomUUID() + "." + extension;
		Path file = root.resolve(path);
		try {
			Files.createDirectories(file.getParent());
			// CREATE_NEW: a random name is never reused, and an existing file is never overwritten.
			Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}
		catch (IOException e) {
			throw new UncheckedIOException("The image could not be stored", e);
		}
		return new StoredImage(path, urlOf(path));
	}

	/** The answer to an upload larger than the limit, whether this class or the multipart reader found it. */
	public static ApiException tooLarge() {
		return new ApiException(ErrorCode.IMAGE_TOO_LARGE, "The image must be at most 2 MB");
	}

	/** Whether the path is one that {@link #store} made and its file exists. */
	public boolean exists(String path) {
		return path != null && PATH.matcher(path).matches() && Files.isRegularFile(root.resolve(path));
	}

	/** The file of an image by its name in the folder of the items, or empty for any other name. */
	public Optional<ImageFile> find(String fileName) {
		if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
			return Optional.empty();
		}
		Path file = root.resolve(FOLDER).resolve(fileName);
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
		return Optional.of(new ImageFile(file, contentTypeOf(extension)));
	}

	/** The address the frontend loads an image from, or null without an image. */
	public static String urlOf(String path) {
		return path == null ? null : URL_PREFIX + path;
	}

	private static String contentTypeOf(String extension) {
		return switch (extension) {
			case "jpg" -> "image/jpeg";
			case "png" -> "image/png";
			default -> "image/webp";
		};
	}

	/** The type of an image from its first bytes, or null when it is none of the three. */
	static String extensionOf(byte[] b) {
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "jpg";
		}
		if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A
				&& b[6] == 0x1A && b[7] == 0x0A) {
			return "png";
		}
		if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B'
				&& b[11] == 'P') {
			return "webp";
		}
		return null;
	}

}

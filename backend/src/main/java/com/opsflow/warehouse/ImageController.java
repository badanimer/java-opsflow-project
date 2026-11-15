package com.opsflow.warehouse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.Public;
import com.opsflow.common.security.RequiresPermission;
import com.opsflow.warehouse.ImageStore.ImageFile;
import com.opsflow.warehouse.ImageStore.StoredImage;

/**
 * The upload of an item image, and the images themselves (T-42; see Item images in
 * docs/database/database-design.md).
 *
 * An image is served without a token: a browser loads an {@code <img>} without the {@code Authorization}
 * header, and a product picture is not a secret. Its name is a random UUID that nobody can guess, and it
 * never changes, so a browser may keep it for a year.
 */
@RestController
public class ImageController {

	private final ImageStore images;
	private final long maxBytes;

	ImageController(ImageStore images, OpsflowProperties properties) {
		this.images = images;
		this.maxBytes = properties.warehouse().imageMaxBytes();
	}

	@RequiresPermission(Permission.ITEM_MANAGE)
	@PostMapping("/item-images")
	@ResponseStatus(HttpStatus.CREATED)
	public StoredImage upload(@RequestParam("file") MultipartFile file) {
		if (file.getSize() > maxBytes) {
			throw ImageStore.tooLarge();
		}
		try {
			return images.store(file.getBytes());
		}
		catch (IOException e) {
			throw new UncheckedIOException("The upload could not be read", e);
		}
	}

	@Public
	@GetMapping("/images/items/{file}")
	public ResponseEntity<Resource> image(@PathVariable("file") String fileName) {
		ImageFile image = images.find(fileName).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "There is no such image"));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.contentType()))
				.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
				.body(new FileSystemResource(image.file()));
	}

}

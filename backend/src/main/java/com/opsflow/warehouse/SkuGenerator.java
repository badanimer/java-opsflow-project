package com.opsflow.warehouse;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Makes the SKU of a new warehouse item: 8 characters from {@code A-Z} and {@code 0-9}, at random (see
 * Warehouse items in docs/database/database-design.md). The SKU is never typed, and it must differ from
 * every SKU ever issued, which the unique key {@code uk_inventory_sku} decides: on a collision the caller
 * asks for another one.
 */
@Component
public class SkuGenerator {

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final int LENGTH = 8;

	private final SecureRandom random = new SecureRandom();

	public String next() {
		StringBuilder sku = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			sku.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return sku.toString();
	}

}

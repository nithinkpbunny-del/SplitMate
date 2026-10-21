package com.splitmate.split;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 12-character base62 invite codes from {@link SecureRandom} (~71 bits of entropy), so links cannot be
 * guessed.
 */
@Component
public class InviteCodeGenerator {

	static final int LENGTH = 12;

	private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

	private final SecureRandom random = new SecureRandom();

	public String next() {
		char[] code = new char[LENGTH];
		for (int i = 0; i < LENGTH; i++) {
			code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
		}
		return new String(code);
	}

}

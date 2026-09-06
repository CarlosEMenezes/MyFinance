package ie.budgetTracker.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Makes session tokens, and reduces them to what is safe to store (ADR-11).
 *
 * 32 bytes of `SecureRandom` is the token; a SHA-256 of it is what the database
 * holds, so a leaked backup contains no usable session. Plain SHA-256 rather
 * than a password hash is right here and would be wrong for a password: this
 * input is 256 bits of uniform randomness, so there is nothing to guess and
 * nothing for a slow KDF to protect against.
 */
@Component
public class SessionTokens {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		// URL-safe and unpadded, so it survives a cookie without escaping.
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException impossible) {
			// SHA-256 is required of every Java platform.
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}

package com.splitmate.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.splitmate.config.AppProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 JWT signing with Spring Security's built-in Nimbus support.
 *
 * <p>The secret comes from {@code JWT_SECRET} and must be at least 32 bytes. Without it, startup fails,
 * except in the dev/demo profiles where a random secret is generated with a loud warning.
 */
@Configuration
public class JwtConfig {

	static final String ISSUER = "splitmate";

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	private static final int MIN_SECRET_BYTES = 32;

	@Bean
	SecretKey jwtSigningKey(AppProperties properties) {
		String secret = properties.jwt().secret();
		if (secret == null || secret.isBlank()) {
			if (!properties.jwt().allowGeneratedSecret()) {
				throw new IllegalStateException(
						"JWT_SECRET is not set. Set it to a random value of at least 32 bytes, e.g. `openssl rand -base64 48`.");
			}
			byte[] random = new byte[48];
			new SecureRandom().nextBytes(random);
			secret = Base64.getEncoder().encodeToString(random);
			log.warn("""

					************************************************************************
					*  JWT_SECRET is not set: using a random secret for this run only.     *
					*  Everyone is logged out when the app restarts. Set JWT_SECRET in .env *
					*  (never do this in production; the prod profile refuses to start).   *
					************************************************************************""");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long.");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER)));
		return decoder;
	}

}

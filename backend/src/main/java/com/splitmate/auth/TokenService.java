package com.splitmate.auth;

import java.time.Duration;
import java.time.Instant;

import com.splitmate.config.AppProperties;
import com.splitmate.user.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed login tokens. The subject is the user's id.
 */
@Service
public class TokenService {

	private final JwtEncoder encoder;

	private final Duration lifetime;

	public TokenService(JwtEncoder encoder, AppProperties properties) {
		this.encoder = encoder;
		this.lifetime = Duration.ofDays(properties.jwt().expiryDays());
	}

	public String issue(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(JwtConfig.ISSUER)
			.subject(user.getId().toHexString())
			.issuedAt(now)
			.expiresAt(now.plus(lifetime))
			.claim("name", user.getName())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public Duration lifetime() {
		return lifetime;
	}

}

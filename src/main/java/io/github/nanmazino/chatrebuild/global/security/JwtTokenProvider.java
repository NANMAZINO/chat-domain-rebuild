package io.github.nanmazino.chatrebuild.global.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final Key signingKey;
    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }

    public String generateAccessToken(Long userId, String email) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.accessTokenExpirationSeconds());

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();
    }
}

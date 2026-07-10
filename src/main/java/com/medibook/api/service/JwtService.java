package com.medibook.api.service;

import com.medibook.api.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    /** Minimum signing-key length in bytes (256-bit) required for HMAC-SHA256. */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Fail-fast validation of the JWT configuration at bean initialization.
     * The secret must be present and provide at least 32 bytes (256 bits) of key
     * material, so HMAC-SHA256 tokens cannot be forged with a weak/empty key.
     * The expiration must be a positive duration (milliseconds) so generated
     * tokens have a valid, non-immediate expiry.
     */
    @PostConstruct
    void validateSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                "jwt.secret must be configured. Set the JWT_SECRET environment variable to a value of at least "
                    + MIN_SECRET_BYTES + " bytes.");
        }
        int keyBytes = secretKey.getBytes(StandardCharsets.UTF_8).length;
        if (keyBytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "jwt.secret is too short: " + keyBytes + " bytes. It must be at least "
                    + MIN_SECRET_BYTES + " bytes (256 bits) for HMAC-SHA256.");
        }
        if (jwtExpiration <= 0) {
            throw new IllegalStateException(
                "jwt.expiration must be a positive number of milliseconds. Set the JWT_DURATION "
                    + "environment variable (current value: " + jwtExpiration + ").");
        }
    }

    public String generateToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email",user.getEmail())
            .claim("role",user.getRole())
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date((System.currentTimeMillis()) + jwtExpiration))
            .signWith(getSignInKey(), Jwts.SIG.HS256)
            .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public void validateTokenThrows(String token){
        Jwts.parser()
            .verifyWith(getSignInKey())
            .build()
            .parseSignedClaims(token);
    }

    private SecretKey getSignInKey(){
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSignInKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}

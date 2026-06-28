package com.medibook.api.service;

import com.medibook.api.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes
    private static final long EXPIRATION = 3600000L;

    private JwtService buildService(String secret, long expiration) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        ReflectionTestUtils.setField(service, "jwtExpiration", expiration);
        return service;
    }

    private User buildUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole("PATIENT");
        return user;
    }

    @Test
    void init_throwsWhenSecretIsNull() {
        JwtService service = buildService(null, EXPIRATION);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecretKey);
        assertTrue(ex.getMessage().toLowerCase().contains("jwt.secret"));
    }

    @Test
    void init_throwsWhenSecretIsEmpty() {
        JwtService service = buildService("", EXPIRATION);

        assertThrows(IllegalStateException.class, service::validateSecretKey);
    }

    @Test
    void init_throwsWhenSecretIsBlank() {
        JwtService service = buildService("    ", EXPIRATION);

        assertThrows(IllegalStateException.class, service::validateSecretKey);
    }

    @Test
    void init_throwsWhenSecretShorterThan32Bytes() {
        // 31 ASCII chars => 31 bytes
        JwtService service = buildService("0123456789abcdef0123456789abcde", EXPIRATION);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecretKey);
        assertTrue(ex.getMessage().contains("32"));
    }

    @Test
    void init_succeedsWhenSecretIsExactly32Bytes() {
        JwtService service = buildService(VALID_SECRET, EXPIRATION);

        assertDoesNotThrow(service::validateSecretKey);
    }

    @Test
    void init_throwsWhenExpirationIsZero() {
        JwtService service = buildService(VALID_SECRET, 0L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecretKey);
        assertTrue(ex.getMessage().toLowerCase().contains("jwt.expiration"));
    }

    @Test
    void init_throwsWhenExpirationIsNegative() {
        JwtService service = buildService(VALID_SECRET, -1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecretKey);
        assertTrue(ex.getMessage().toLowerCase().contains("jwt.expiration"));
    }

    @Test
    void init_succeedsWhenExpirationIsPositive() {
        JwtService service = buildService(VALID_SECRET, EXPIRATION);

        assertDoesNotThrow(service::validateSecretKey);
    }

    @Test
    void validKey_producesVerifiableToken() {
        JwtService service = buildService(VALID_SECRET, EXPIRATION);
        service.validateSecretKey();

        User user = buildUser();
        String token = service.generateToken(user);

        assertNotNull(token);
        assertTrue(service.isTokenValid(token));
        assertEquals(user.getId().toString(), service.extractUserId(token));
        assertDoesNotThrow(() -> service.validateTokenThrows(token));
    }

    @Test
    void tokenSignedWithDifferentKey_isNotValid() {
        JwtService signer = buildService(VALID_SECRET, EXPIRATION);
        signer.validateSecretKey();
        String token = signer.generateToken(buildUser());

        JwtService otherKeyService = buildService("ffffffffffffffffffffffffffffffff", EXPIRATION);
        otherKeyService.validateSecretKey();

        assertFalse(otherKeyService.isTokenValid(token));
    }
}

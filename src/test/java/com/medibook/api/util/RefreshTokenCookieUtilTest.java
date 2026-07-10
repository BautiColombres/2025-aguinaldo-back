package com.medibook.api.util;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenCookieUtilTest {

    @Test
    void build_setsAllExpectedAttributes_secureOff() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil(false, "Strict");

        ResponseCookie cookie = util.build("raw-refresh-token-value");

        assertEquals("refreshToken", cookie.getName());
        assertEquals("raw-refresh-token-value", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals(2592000L, cookie.getMaxAge().getSeconds());
    }

    @Test
    void build_secureOn_whenConfigured() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil(true, "Strict");

        ResponseCookie cookie = util.build("value");

        assertTrue(cookie.isSecure());
    }

    @Test
    void build_serialization_containsExpectedAttributes() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil(false, "Strict");

        String header = util.build("abc").toString();

        assertTrue(header.contains("refreshToken=abc"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
        assertTrue(header.contains("Path=/api/auth"));
        assertTrue(header.contains("Max-Age=2592000"));
        assertFalse(header.contains("Secure"));
    }

    @Test
    void clear_hasZeroMaxAgeAndEmptyValue_sameScope() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil(false, "Strict");

        ResponseCookie cookie = util.clear();

        assertEquals("refreshToken", cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(0L, cookie.getMaxAge().getSeconds());
        assertEquals("/api/auth", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
    }

    @Test
    void read_returnsTokenWhenCookiePresent() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil(false, "Strict");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("refreshToken", "the-token"));

        Optional<String> read = util.read(req);

        assertTrue(read.isPresent());
        assertEquals("the-token", read.get());
    }

    @Test
    void read_emptyWhenNoCookie() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil(false, "Strict");
        MockHttpServletRequest req = new MockHttpServletRequest();

        assertTrue(util.read(req).isEmpty());
    }

    @Test
    void read_emptyWhenDifferentCookie() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil(false, "Strict");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("somethingElse", "x"));

        assertTrue(util.read(req).isEmpty());
    }
}

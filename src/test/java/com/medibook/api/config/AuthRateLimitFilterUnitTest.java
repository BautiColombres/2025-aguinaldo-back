package com.medibook.api.config;

import com.medibook.api.service.RateLimitService;
import com.medibook.api.util.TrustedProxyResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit-level coverage of the filter's keying behaviour
 * (trusted-proxy gating + per-username dimension) without a full Spring context.
 */
class AuthRateLimitFilterUnitTest {

    private MockHttpServletRequest signinRequest(String remoteAddr, String forwardedFor, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/signin");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        request.setContentType("application/json");
        if (email != null) {
            request.setContent(("{\"email\":\"" + email + "\",\"password\":\"x\"}").getBytes());
        }
        return request;
    }

    private FilterChain passThrough(AtomicInteger passes) {
        return (req, res) -> passes.incrementAndGet();
    }

    @Test
    void spoofedForwardedForKeysOnRealPeerWhenNoTrustedProxy() throws Exception {
        RateLimitService service = new RateLimitService(2, 1, 1000);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(service, new TrustedProxyResolver(List.of()));
        AtomicInteger passes = new AtomicInteger();

        // Distinct emails per request so the username dimension never blocks;
        // only the per-peer IP dimension can produce the 429 here.
        int lastStatus = 200;
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(signinRequest("9.9.9.9", "203.0.113." + i, "user" + i + "@b.com"),
                    res, passThrough(passes));
            lastStatus = res.getStatus();
        }
        assertEquals(429, lastStatus,
                "rotating spoofed X-Forwarded-For must not bypass the per-peer limit");
    }

    @Test
    void trustedProxyHonorsForwardedForSoDistinctClientsGetDistinctBuckets() throws Exception {
        // capacity 1: one request per client key. If XFF is honored, two distinct
        // forwarded clients (behind the trusted proxy) each get their own bucket.
        RateLimitService service = new RateLimitService(1, 1, 1000);
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(service, new TrustedProxyResolver(List.of("10.0.0.1")));
        AtomicInteger passes = new AtomicInteger();

        // Distinct emails so only the IP/forwarded-for dimension is exercised here.
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(signinRequest("10.0.0.1", "1.1.1.1", "a@b.com"), res1, passThrough(passes));

        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(signinRequest("10.0.0.1", "2.2.2.2", "c@d.com"), res2, passThrough(passes));

        assertEquals(200, res1.getStatus());
        assertEquals(200, res2.getStatus(),
                "behind a trusted proxy, a different forwarded client must get its own bucket");
        assertEquals(2, passes.get());
    }

    @Test
    void perUsernameDimensionThrottlesAcrossRotatingIps() throws Exception {
        // capacity 2. Same account, different (real) peer IPs each time. The IP
        // bucket never fills, but the per-username bucket must catch the brute force.
        RateLimitService service = new RateLimitService(2, 1, 1000);
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(service, new TrustedProxyResolver(List.of()));
        AtomicInteger passes = new AtomicInteger();

        int lastStatus = 200;
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(signinRequest("8.8.8." + i, null, "victim@example.com"),
                    res, passThrough(passes));
            lastStatus = res.getStatus();
        }
        assertEquals(429, lastStatus,
                "per-account brute force from rotating IPs must be throttled by the username dimension");
    }

    @Test
    void signinBodyStillReadableDownstream() throws Exception {
        RateLimitService service = new RateLimitService(10, 1, 1000);
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(service, new TrustedProxyResolver(List.of()));
        StringBuilder seen = new StringBuilder();

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(signinRequest("1.2.3.4", null, "downstream@example.com"), res,
                (req, r) -> seen.append(new String(req.getInputStream().readAllBytes())));

        assertEquals("{\"email\":\"downstream@example.com\",\"password\":\"x\"}", seen.toString(),
                "the filter must not consume the body; the controller must still read it");
    }
}

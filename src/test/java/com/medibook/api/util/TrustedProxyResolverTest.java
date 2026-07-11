package com.medibook.api.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BSEC-M-4 hardening — X-Forwarded-For must only be trusted when the immediate
 * peer (remoteAddr) is in the configured trusted-proxy allowlist. Default empty
 * list = never trust the header.
 */
class TrustedProxyResolverTest {

    @Test
    void ignoresForwardedHeaderWhenNoTrustedProxyConfigured() {
        TrustedProxyResolver resolver = new TrustedProxyResolver(List.of());
        String clientIp = resolver.resolveClientIp("203.0.113.7", "1.2.3.4");
        assertEquals("203.0.113.7", clientIp,
                "with no trusted proxies, the spoofable X-Forwarded-For must be ignored");
    }

    @Test
    void ignoresForwardedHeaderWhenPeerNotTrusted() {
        TrustedProxyResolver resolver = new TrustedProxyResolver(List.of("10.0.0.1"));
        String clientIp = resolver.resolveClientIp("203.0.113.7", "1.2.3.4");
        assertEquals("203.0.113.7", clientIp,
                "an untrusted peer's X-Forwarded-For must be ignored");
    }

    @Test
    void honorsForwardedHeaderWhenPeerIsTrustedExactIp() {
        TrustedProxyResolver resolver = new TrustedProxyResolver(List.of("10.0.0.1"));
        String clientIp = resolver.resolveClientIp("10.0.0.1", "1.2.3.4");
        assertEquals("1.2.3.4", clientIp,
                "a trusted peer's X-Forwarded-For first hop must be honored");
    }

    @Test
    void honorsForwardedHeaderWhenPeerIsInTrustedCidr() {
        TrustedProxyResolver resolver = new TrustedProxyResolver(List.of("10.0.0.0/24"));
        String clientIp = resolver.resolveClientIp("10.0.0.55", "1.2.3.4");
        assertEquals("1.2.3.4", clientIp,
                "a trusted CIDR peer's X-Forwarded-For must be honored");
    }

    @Test
    void fallsBackToRemoteAddrWhenNoForwardedHeader() {
        TrustedProxyResolver resolver = new TrustedProxyResolver(List.of("10.0.0.1"));
        String clientIp = resolver.resolveClientIp("10.0.0.1", null);
        assertEquals("10.0.0.1", clientIp,
                "without an X-Forwarded-For header the remote addr is used");
    }

    @Test
    void usesFirstHopFromForwardedChainForTrustedPeer() {
        TrustedProxyResolver resolver = new TrustedProxyResolver(List.of("10.0.0.1"));
        String clientIp = resolver.resolveClientIp("10.0.0.1", "1.2.3.4, 10.0.0.9, 10.0.0.1");
        assertEquals("1.2.3.4", clientIp,
                "the original client is the first entry in the X-Forwarded-For chain");
    }

    @Test
    void peerOutsideTrustedCidrIsIgnored() {
        TrustedProxyResolver resolver = new TrustedProxyResolver(List.of("10.0.0.0/24"));
        String clientIp = resolver.resolveClientIp("10.0.1.5", "1.2.3.4");
        assertEquals("10.0.1.5", clientIp,
                "a peer outside the trusted CIDR must not have its header honored");
    }
}

package com.medibook.api.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BSEC-M-4 hardening — resolves the real client IP for rate-limiting purposes
 * WITHOUT blindly trusting the {@code X-Forwarded-For} header.
 *
 * <p>{@code X-Forwarded-For} is honored only when the immediate peer
 * ({@code remoteAddr}) is contained in the configured trusted-proxy allowlist
 * (exact IPs or CIDR ranges). When the list is empty (the secure default) the
 * header is ignored entirely and the real peer address is used. This prevents a
 * client from minting a fresh rate-limit bucket per request by spoofing the
 * header.
 */
public class TrustedProxyResolver {

    private final List<CidrRange> trustedProxies;

    public TrustedProxyResolver(List<String> trustedProxyCidrs) {
        List<CidrRange> parsed = new ArrayList<>();
        if (trustedProxyCidrs != null) {
            for (String entry : trustedProxyCidrs) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                CidrRange range = CidrRange.parse(entry.trim());
                if (range != null) {
                    parsed.add(range);
                }
            }
        }
        this.trustedProxies = Collections.unmodifiableList(parsed);
    }

    /**
     * @param remoteAddr   the immediate TCP peer ({@code request.getRemoteAddr()})
     * @param forwardedFor the raw {@code X-Forwarded-For} header (may be null/blank)
     * @return the client IP to key the rate limiter on
     */
    public String resolveClientIp(String remoteAddr, String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank() || !isTrustedPeer(remoteAddr)) {
            return remoteAddr;
        }
        String firstHop = forwardedFor.split(",")[0].trim();
        return firstHop.isEmpty() ? remoteAddr : firstHop;
    }

    private boolean isTrustedPeer(String remoteAddr) {
        if (trustedProxies.isEmpty() || remoteAddr == null) {
            return false;
        }
        byte[] peer;
        try {
            peer = InetAddress.getByName(remoteAddr).getAddress();
        } catch (UnknownHostException e) {
            return false;
        }
        for (CidrRange range : trustedProxies) {
            if (range.contains(peer)) {
                return true;
            }
        }
        return false;
    }

    /** Minimal CIDR / exact-IP matcher supporting IPv4 and IPv6. */
    private static final class CidrRange {
        private final byte[] network;
        private final int prefixBits;

        private CidrRange(byte[] network, int prefixBits) {
            this.network = network;
            this.prefixBits = prefixBits;
        }

        static CidrRange parse(String cidr) {
            try {
                String addressPart = cidr;
                int prefix = -1;
                int slash = cidr.indexOf('/');
                if (slash >= 0) {
                    addressPart = cidr.substring(0, slash);
                    prefix = Integer.parseInt(cidr.substring(slash + 1).trim());
                }
                byte[] network = InetAddress.getByName(addressPart).getAddress();
                if (prefix < 0) {
                    prefix = network.length * 8;
                }
                if (prefix < 0 || prefix > network.length * 8) {
                    return null;
                }
                return new CidrRange(network, prefix);
            } catch (UnknownHostException | NumberFormatException e) {
                return null;
            }
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false; // different IP family
            }
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}

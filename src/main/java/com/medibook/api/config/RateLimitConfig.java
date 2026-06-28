package com.medibook.api.config;

import com.medibook.api.service.RateLimitService;
import com.medibook.api.util.TrustedProxyResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Arrays;
import java.util.List;

/**
 * BSEC-M-4 — wiring for the in-app rate limiters.
 *
 * <ul>
 *   <li>{@code authRateLimitService} + {@link AuthRateLimitFilter} protect
 *       {@code /api/auth/**} per client IP.</li>
 *   <li>{@code externalRateLimitService} is a bounded limiter that replaces the
 *       previously unbounded in-memory counter map in {@code ExternalController}.</li>
 * </ul>
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitService authRateLimitService(
            @Value("${auth.ratelimit.capacity:10}") long capacity,
            @Value("${auth.ratelimit.refill-minutes:1}") long refillMinutes,
            @Value("${auth.ratelimit.max-clients:100000}") int maxClients) {
        return new RateLimitService(capacity, refillMinutes, maxClients);
    }

    @Bean
    public RateLimitService externalRateLimitService(
            @Value("${external.ratelimit.capacity:10}") long capacity,
            @Value("${external.ratelimit.refill-minutes:1}") long refillMinutes,
            @Value("${external.ratelimit.max-clients:100000}") int maxClients) {
        return new RateLimitService(capacity, refillMinutes, maxClients);
    }

    /**
     * Trusted-proxy allowlist for X-Forwarded-For handling. Default EMPTY: the
     * header is ignored and the real peer (remoteAddr) is used. Configure with a
     * comma-separated list of proxy IPs/CIDRs (e.g. the load balancer subnet)
     * only when the app runs behind a trusted reverse proxy.
     */
    @Bean
    public TrustedProxyResolver trustedProxyResolver(
            @Value("${auth.ratelimit.trusted-proxies:}") String trustedProxies) {
        List<String> entries = (trustedProxies == null || trustedProxies.isBlank())
                ? List.of()
                : Arrays.stream(trustedProxies.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new TrustedProxyResolver(entries);
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            @Qualifier("authRateLimitService") RateLimitService authRateLimitService,
            TrustedProxyResolver trustedProxyResolver) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(
                        new AuthRateLimitFilter(authRateLimitService, trustedProxyResolver));
        registration.addUrlPatterns("/api/auth/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

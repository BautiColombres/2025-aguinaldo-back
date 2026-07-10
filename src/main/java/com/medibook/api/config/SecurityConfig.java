package com.medibook.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final JwtAuthenticationEntryEndpoint jwtAuthenticationEntryEndpoint;

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(TokenAuthenticationFilter tokenAuthenticationFilter,
                            JwtAuthenticationEntryEndpoint jwtAuthenticationEntryEndpoint) {
        
        this.tokenAuthenticationFilter = tokenAuthenticationFilter;
        this.jwtAuthenticationEntryEndpoint = jwtAuthenticationEntryEndpoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF (FSEC-H1 decision): intentionally disabled. The refresh-token cookie is
            // the ONLY cookie-borne credential and is scoped to Path=/api/auth with
            // SameSite=Strict, so the browser never attaches it on a cross-site request —
            // that neutralizes CSRF on /refresh-token and /signout. Every other endpoint
            // authenticates via the Authorization: Bearer header (not auto-sent by the
            // browser), so it is inherently CSRF-immune. Do NOT re-enable/add a CSRF token
            // unless the cookie is ever broadened beyond /api/auth or SameSite is relaxed
            // to None (a future cross-domain deploy) — then add a double-submit token on
            // /api/auth/refresh-token + /signout.
            .csrf(csrf -> csrf.disable())
            // CORS (BSEC-M-2): pinned origins + explicit method/header allow-lists (no
            // wildcards) with credentials enabled so the httpOnly refresh cookie is sent.
            .cors(cors -> cors.configurationSource(request -> {
                var config = new org.springframework.web.cors.CorsConfiguration();
                config.setAllowedOrigins(java.util.Arrays.asList(allowedOrigins.split(",")));
                config.setAllowedMethods(java.util.Arrays.asList(
                        "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                // FSEC-H1 Stage 3: the Refresh-Token header is gone — the refresh token
                // travels ONLY via the httpOnly cookie. Do NOT re-add it here.
                config.setAllowedHeaders(java.util.Arrays.asList(
                        "Authorization", "Content-Type"));
                config.setAllowCredentials(true);
                return config;
            }))
            .authorizeHttpRequests(authz -> authz
                // Rutas públicas de autenticación (registro admin queda fuera a propósito)
                .requestMatchers(HttpMethod.POST, "/api/auth/signin").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh-token").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/signout").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register/patient").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register/doctor").permitAll()
                // register/admin requiere autenticación + @PreAuthorize("hasRole('ADMIN')")
                .requestMatchers("/api/gymcloud/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Rutas privadas
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryEndpoint)
            )
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
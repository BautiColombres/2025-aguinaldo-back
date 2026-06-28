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
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(request -> {
                var config = new org.springframework.web.cors.CorsConfiguration();
                config.setAllowedOrigins(java.util.Arrays.asList(allowedOrigins.split(",")));
                config.setAllowedMethods(java.util.Arrays.asList("*"));
                config.setAllowedHeaders(java.util.Arrays.asList("*"));
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
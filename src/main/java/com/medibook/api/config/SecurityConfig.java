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
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final ActiveUserAuthorizationManager activeUserAuthorizationManager;

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(TokenAuthenticationFilter tokenAuthenticationFilter,
                            JwtAuthenticationEntryEndpoint jwtAuthenticationEntryEndpoint,
                            JwtAccessDeniedHandler jwtAccessDeniedHandler,
                            ActiveUserAuthorizationManager activeUserAuthorizationManager) {

        this.tokenAuthenticationFilter = tokenAuthenticationFilter;
        this.jwtAuthenticationEntryEndpoint = jwtAuthenticationEntryEndpoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.activeUserAuthorizationManager = activeUserAuthorizationManager;
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
                config.setAllowedMethods(java.util.Arrays.asList(
                        "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                config.setAllowedHeaders(java.util.Arrays.asList(
                        "Authorization", "Content-Type"));
                config.setAllowCredentials(true);
                return config;
            }))
            .authorizeHttpRequests(authz -> authz
                // Rutas públicas
                .requestMatchers(HttpMethod.POST, "/api/auth/signin").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh-token").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/signout").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register/patient").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register/doctor").permitAll()
                .requestMatchers("/api/gymcloud/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Rutas privadas: autenticado Y con la cuenta ACTIVE.
                // Un usuario autenticado pero no-ACTIVE (doctor PENDING, usuario DISABLED)
                // se deniega ACA, en la capa de autorizacion => 403 (no 401). BUG-001.
                .anyRequest().access(activeUserAuthorizationManager)
            )
            .exceptionHandling(exception -> exception
                // Anonimo (sin token / token invalido o vencido) -> 401.
                .authenticationEntryPoint(jwtAuthenticationEntryEndpoint)
                // Autenticado pero sin permiso (rol incorrecto, recurso ajeno, cuenta
                // no-ACTIVE) -> 403 generico, sin filtrar el motivo.
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
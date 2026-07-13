package com.medibook.api.config;

import com.medibook.api.entity.User;
import com.medibook.api.service.AuthenticatedUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    
    private final AuthenticatedUserService authenticatedUserService;
    
    public TokenAuthenticationFilter(AuthenticatedUserService authenticatedUserService) {
        this.authenticatedUserService = authenticatedUserService;
    }
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                   @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }      
        
        try {
            String authorizationHeader = request.getHeader("Authorization");
            
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }
            
            Optional<User> userOpt = authenticatedUserService.getUserFromAuthorizationHeader(authorizationHeader);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                );

                SecurityContextHolder.getContext().setAuthentication(authToken);
                request.setAttribute("authenticatedUser", user);
            }
            
            filterChain.doFilter(request, response);

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Error 401: Token válido pero vencido
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token expired\", \"message\": \"El token ha expirado.\"}");
        
        } catch (io.jsonwebtoken.security.SignatureException | io.jsonwebtoken.MalformedJwtException e) {
            // Error 401/403: Bloqueo de seguridad
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\", \"message\": \"Firma de token inválida.\"}");
        
        } catch (Exception e) {
            // Otros errores
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Authentication error\", \"message\": \"Error procesando autenticación.\"}");
        }
    }
}
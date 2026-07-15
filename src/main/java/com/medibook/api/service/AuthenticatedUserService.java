package com.medibook.api.service;

import com.medibook.api.entity.User;
import com.medibook.api.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the {@link User} a valid access token belongs to — this is AUTHENTICATION
 * ("who are you?"), not authorization ("may you?").
 *
 * <p>BUG-001: the account status is deliberately NOT checked here. A non-ACTIVE account
 * (PENDING doctor awaiting approval, DISABLED user) holding a VALID token is still
 * identified, so the request is authenticated-but-not-authorized and is denied with
 * <b>403</b> by {@link com.medibook.api.config.ActiveUserAuthorizationManager} at the
 * authorization layer, instead of being silently treated as anonymous and returning a
 * misleading <b>401</b>. The ACTIVE requirement itself is unchanged and still total —
 * only the status code the client sees changes.
 */
@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthenticatedUserService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public Optional<User> validateAccessToken(String accessToken) {
        jwtService.validateTokenThrows(accessToken);

        String userIdString = jwtService.extractUserId(accessToken);

        try {
            UUID userId = UUID.fromString(userIdString);
            // Status is intentionally NOT filtered here — see the class javadoc.
            return userRepository.findById(userId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
    
    public Optional<User> getUserFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        
        String accessToken = authorizationHeader.substring("Bearer ".length());
        return validateAccessToken(accessToken);
    }

}
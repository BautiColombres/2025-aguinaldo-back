package com.medibook.api.service;

import com.medibook.api.entity.User;
import com.medibook.api.repository.UserRepository;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticatedUserService authenticatedUserService;

    private User activeUser;
    private User inactiveUser;
    private UUID userId;
    private String validToken; 


    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        validToken = "valid.jwt.token";
        
        activeUser = new User();
        activeUser.setId(userId);
        activeUser.setEmail("test@example.com");
        activeUser.setRole("PATIENT");
        activeUser.setStatus("ACTIVE");

        inactiveUser = new User();
        inactiveUser.setId(userId);
        inactiveUser.setEmail("inactive@example.com");
        inactiveUser.setRole("PATIENT");
        inactiveUser.setStatus("DISABLED");
    }

     @Test
    void validateAccessToken_ValidToken_ReturnsUser() {
        when(jwtService.extractUserId(validToken)).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        Optional<User> result = authenticatedUserService.validateAccessToken(validToken);

        assertTrue(result.isPresent());
        assertEquals(activeUser, result.get());
    }

    @Test
    void validateAccessToken_InvalidTokenFormat_ThrowsException() {        
        doThrow(new MalformedJwtException("Token invalido")).when(jwtService).validateTokenThrows("invalid-token");

        assertThrows(MalformedJwtException.class, () -> 
            authenticatedUserService.validateAccessToken("invalid-token")
        );
    }

    @Test
    void validateAccessToken_UserNotFound_ReturnsEmpty() {
        when(jwtService.extractUserId(validToken)).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<User> result = authenticatedUserService.validateAccessToken(validToken);

        assertTrue(result.isEmpty());
    }

    /**
     * BUG-001: this service answers "who does this token belong to?" — it is AUTHENTICATION,
     * not authorization. A non-ACTIVE account holding a VALID token is still identified, so the
     * request becomes authenticated-but-not-authorized and the authorization layer
     * ({@code ActiveUserAuthorizationManager}) can deny it with 403 instead of 401.
     */
    @Test
    void validateAccessToken_NonActiveUser_StillResolvesUser_StatusIsAuthorizationConcern() {
        when(jwtService.extractUserId(validToken)).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(inactiveUser));

        Optional<User> result = authenticatedUserService.validateAccessToken(validToken);

        assertTrue(result.isPresent(), "a valid token must resolve its user regardless of account status");
        assertEquals(inactiveUser, result.get());
        assertEquals("DISABLED", result.get().getStatus());
    }

    @Test
    void validateAccessToken_PendingDoctor_StillResolvesUser() {
        User pendingDoctor = new User();
        pendingDoctor.setId(userId);
        pendingDoctor.setEmail("pending@example.com");
        pendingDoctor.setRole("DOCTOR");
        pendingDoctor.setStatus("PENDING");

        when(jwtService.extractUserId(validToken)).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(pendingDoctor));

        Optional<User> result = authenticatedUserService.validateAccessToken(validToken);

        assertTrue(result.isPresent());
        assertEquals("PENDING", result.get().getStatus());
    }

    @Test
    void getUserFromAuthorizationHeader_ValidHeader_ReturnsUser() {
        String authHeader = "Bearer " + validToken;
        
        when(jwtService.extractUserId(validToken)).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        Optional<User> result = authenticatedUserService.getUserFromAuthorizationHeader(authHeader);

        assertTrue(result.isPresent());
        assertEquals(activeUser, result.get());
    }

    @Test
    void getUserFromAuthorizationHeader_NoBearer_ReturnsEmpty() {
        Optional<User> result = authenticatedUserService.getUserFromAuthorizationHeader(validToken);
        assertTrue(result.isEmpty());
    }
}
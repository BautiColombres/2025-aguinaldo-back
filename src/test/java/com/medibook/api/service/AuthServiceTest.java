package com.medibook.api.service;

import com.medibook.api.dto.Auth.RegisterRequestDTO;
import com.medibook.api.dto.Auth.RegisterResponseDTO;
import com.medibook.api.dto.Auth.SignInRequestDTO;
import com.medibook.api.dto.Auth.SignInResponseDTO;
import com.medibook.api.dto.Auth.SignInResultDTO;
import com.medibook.api.entity.RefreshToken;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.AuthMapper;
import com.medibook.api.mapper.UserMapper;
import com.medibook.api.repository.EmailVerificationRepository;
import com.medibook.api.repository.RefreshTokenRepository;
import com.medibook.api.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, refreshTokenRepository, passwordEncoder, userMapper, authMapper, emailService, emailVerificationRepository, jwtService);
    }

    @Test
    void whenRegisterPatient_thenSuccess() {
        RegisterRequestDTO request = new RegisterRequestDTO(
            "patient@example.com",
            12345678L,
            "password123",
            "John",
            "Doe",
            "+1234567890",
            LocalDate.of(1990, 1, 1),
            "MALE",
            null,
            null,
            null
        );

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setName(request.name());
        user.setRole("PATIENT");

        RegisterResponseDTO expectedResponse = new RegisterResponseDTO(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getSurname(),
            user.getRole(),
            "ACTIVE"
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed_password");
        when(userMapper.toUser(request, "PATIENT", "hashed_password")).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toRegisterResponse(user)).thenReturn(expectedResponse);

        RegisterResponseDTO response = authService.registerPatient(request);

        assertNotNull(response);
        assertEquals(expectedResponse.id(), response.id());
        assertEquals(expectedResponse.email(), response.email());
        assertEquals(expectedResponse.role(), response.role());

        verify(userRepository).existsByEmail(request.email());
        verify(userRepository).existsByDni(request.dni());
        verify(passwordEncoder).encode(request.password());
        verify(userMapper).toUser(request, "PATIENT", "hashed_password");
        verify(userRepository).save(user);
        verify(userMapper).toRegisterResponse(user);
    }

    @Test
    void whenRegisterPatientWithExistingEmail_thenThrowException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
            "existing@example.com",
            12345678L,
            "Password123",  // Corregido para cumplir patrón
            "John",
            "Doe",
            "+1234567890",  // Agregado phone obligatorio
            LocalDate.of(1990, 1, 1),  // Agregado birthdate obligatorio
            "MALE",  // Agregado gender obligatorio
            null,
            null,
            null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.registerPatient(request)
        );

        assertEquals("Email already registered", exception.getMessage());
        verify(userRepository).existsByEmail(request.email());
        verifyNoMoreInteractions(userRepository, passwordEncoder, userMapper);
    }

    @Test
    void whenRegisterDoctor_thenSuccess() {
        RegisterRequestDTO request = new RegisterRequestDTO(
            "doctor@example.com",
            87654322L,
            "Password123",  // Corregido para cumplir patrón
            "John",
            "Doe",
            "+1234567890",
            LocalDate.of(1990, 1, 1),
            "MALE",
            "1234567890",  // Corregido: licencia médica debe ser 4-10 dígitos
            "CARDIOLOGÍA",
            30
        );

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setName(request.name());
        user.setRole("DOCTOR");

        RegisterResponseDTO expectedResponse = new RegisterResponseDTO(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getSurname(),
            user.getRole(),
            "ACTIVE"
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed_password");
        when(userMapper.toUser(request, "DOCTOR", "hashed_password")).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toRegisterResponse(user)).thenReturn(expectedResponse);

        RegisterResponseDTO response = authService.registerDoctor(request);

        assertNotNull(response);
        assertEquals(expectedResponse.id(), response.id());
        assertEquals(expectedResponse.email(), response.email());
        assertEquals(expectedResponse.role(), response.role());

        verify(userRepository).existsByEmail(request.email());
        verify(userRepository).existsByDni(request.dni());
        verify(passwordEncoder).encode(request.password());
        verify(userMapper).toUser(request, "DOCTOR", "hashed_password");
        verify(userRepository).save(user);
        verify(userMapper).toRegisterResponse(user);
    }

    @Test
    void whenRegisterDoctorWithoutLicense_thenThrowException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
            "doctor@example.com",
            87654321L,
            "Password123",  // Corregido para cumplir patrón
            "John",
            "Doe",
            "+1234567890",  // Agregado phone obligatorio
            LocalDate.of(1990, 1, 1),  // Agregado birthdate obligatorio
            "MALE",  // Agregado gender obligatorio
            null,  // Sin licencia médica
            null,  // Sin especialidad
            null
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.registerDoctor(request)
        );

        assertEquals("Medical license is required for doctors", exception.getMessage());
        // No verificamos interacciones con el repositorio porque la excepción se lanza
        // en validateDoctorFields antes de llegar a las verificaciones de email/DNI
    }

    @Test
    void whenRegisterPatientWithExistingDni_thenThrowException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
            "patient@example.com",
            12345678L,
            "password123",
            "John",
            "Doe",
            "+1234567890",
            LocalDate.of(1990, 1, 1),
            "MALE",
            null,
            null,
            null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.registerPatient(request)
        );

        assertEquals("DNI already registered", exception.getMessage());
        verify(userRepository).existsByEmail(request.email());
        verify(userRepository).existsByDni(request.dni());
        verifyNoMoreInteractions(userRepository, passwordEncoder, userMapper);
    }

    @Test
    void whenSignInWithValidCredentials_thenSuccess() {
        SignInRequestDTO request = new SignInRequestDTO("user@example.com", "password123");
        
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setPasswordHash("hashed_password");
        user.setStatus("ACTIVE");
        user.setRole("PATIENT"); // Agregamos el rol
        user.setEmailVerified(true);

        SignInResponseDTO expectedResponse = new SignInResponseDTO(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getSurname(),
            user.getRole(),
            user.getStatus(),
            "access-token"
        );

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(authMapper.toSignInResponse(any(User.class), anyString()))
            .thenReturn(expectedResponse);
        when(jwtService.generateToken(any(User.class))).thenReturn("mocked-jwt-token");

        SignInResultDTO result = authService.signIn(request);

        assertNotNull(result);
        assertEquals(expectedResponse.id(), result.response().id());
        assertEquals(expectedResponse.email(), result.response().email());
        assertEquals(expectedResponse.accessToken(), result.response().accessToken());
        // FSEC-H1 Stage 3: refresh token is on the result (cookie), not in the body DTO.
        assertNotNull(result.refreshToken());

        verify(userRepository).findByEmail(request.email());
        verify(passwordEncoder).matches(request.password(), user.getPasswordHash());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(authMapper).toSignInResponse(eq(user), anyString());
    }

    @Test
    void whenSignInWithInvalidEmail_thenThrowException() {
        SignInRequestDTO request = new SignInRequestDTO("invalid@example.com", "password123");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.signIn(request)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
        verify(userRepository).findByEmail(request.email());
        verifyNoMoreInteractions(passwordEncoder, refreshTokenRepository, authMapper);
    }

    @Test
    void whenSignInWithInvalidPassword_thenThrowException() {
        SignInRequestDTO request = new SignInRequestDTO("user@example.com", "wrongpassword");
        
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash("hashed_password");
        user.setStatus("ACTIVE");
        user.setRole("PATIENT");
        user.setEmailVerified(true);

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.signIn(request)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
        verify(userRepository).findByEmail(request.email());
        verify(passwordEncoder).matches(request.password(), user.getPasswordHash());
        verifyNoMoreInteractions(refreshTokenRepository, authMapper);
    }

    @Test
    void whenSignOut_thenRevokeToken() {
        String refreshTokenHash = "token-hash";

        authService.signOut(refreshTokenHash);

        verify(refreshTokenRepository).revokeTokenByHash(eq(hashToken(refreshTokenHash)), any(ZonedDateTime.class));
    }

    @Test
    void whenRefreshTokenWithValidToken_thenSuccess() {
        String refreshTokenHash = "valid-token-hash";
        String mockedNewAccessToken = "mocked-new-access-token"; // Token simulado
        
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setName("John");
        user.setSurname("Doe");
        user.setRole("PATIENT");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(refreshTokenHash));
        refreshToken.setExpiresAt(ZonedDateTime.now().plusDays(30));
        refreshToken.setCreatedAt(ZonedDateTime.now());

        SignInResponseDTO expectedResponse = new SignInResponseDTO(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getSurname(),
            user.getRole(),
            "ACTIVE",
            mockedNewAccessToken
        );

        when(jwtService.generateToken(any(User.class))).thenReturn(mockedNewAccessToken);

        when(refreshTokenRepository.findByTokenHash(hashToken(refreshTokenHash))).thenReturn(Optional.of(refreshToken));

        when(authMapper.toSignInResponse(any(User.class), anyString()))
            .thenReturn(expectedResponse);

        SignInResultDTO result = authService.refreshToken(refreshTokenHash);

        assertNotNull(result);
        assertEquals(expectedResponse.id(), result.response().id());
        assertEquals(expectedResponse.email(), result.response().email());
        assertNotNull(result.refreshToken());

        verify(refreshTokenRepository).findByTokenHash(hashToken(refreshTokenHash));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(refreshTokenRepository).revokeTokenByHash(eq(hashToken(refreshTokenHash)), any(ZonedDateTime.class));

        verify(authMapper).toSignInResponse(eq(user), anyString());
    }

    @Test
    void whenRefreshTokenWithInvalidToken_thenThrowException() {
        String invalidTokenHash = "invalid-token-hash";

        when(refreshTokenRepository.findByTokenHash(hashToken(invalidTokenHash))).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.refreshToken(invalidTokenHash)
        );

        assertEquals("Invalid refresh token", exception.getMessage());
        verify(refreshTokenRepository).findByTokenHash(hashToken(invalidTokenHash));
        verifyNoMoreInteractions(refreshTokenRepository, authMapper);
    }

    @Test
    void whenRefreshTokenWithExpiredToken_thenThrowException() {
        String expiredTokenHash = "expired-token-hash";
        
        User user = new User();
        user.setEmail("user@example.com");

        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setUser(user);
        expiredToken.setTokenHash(hashToken(expiredTokenHash)); // Store hashed version
        expiredToken.setExpiresAt(ZonedDateTime.now().minusDays(1));
        expiredToken.setCreatedAt(ZonedDateTime.now().minusDays(31));

        when(refreshTokenRepository.findByTokenHash(hashToken(expiredTokenHash))).thenReturn(Optional.of(expiredToken));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.refreshToken(expiredTokenHash)
        );

        assertEquals("Refresh token is expired or revoked", exception.getMessage());
        verify(refreshTokenRepository).findByTokenHash(hashToken(expiredTokenHash));
        verifyNoMoreInteractions(refreshTokenRepository, authMapper);
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

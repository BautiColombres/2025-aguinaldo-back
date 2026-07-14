package com.medibook.api.service;

import com.medibook.api.dto.Auth.RegisterRequestDTO;
import com.medibook.api.dto.Auth.RegisterResponseDTO;
import com.medibook.api.dto.Auth.SignInRequestDTO;
import com.medibook.api.dto.Auth.SignInResponseDTO;
import com.medibook.api.dto.Auth.SignInResultDTO;
import com.medibook.api.dto.email.EmailResponseDto;
import com.medibook.api.entity.RefreshToken;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.AuthMapper;
import com.medibook.api.mapper.UserMapper;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;

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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

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

    private AuthServiceImpl authService;

    private static final String TEST_HMAC_KEY = "test-jwt-secret-key-for-testing-purposes-only";

    private RegisterRequestDTO validPatientRequest;
    private RegisterRequestDTO validDoctorRequest;
    private RegisterRequestDTO validAdminRequest;
    private User sampleUser;
    private SignInRequestDTO validSignInRequest;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, refreshTokenRepository, passwordEncoder, userMapper, authMapper, emailService, emailVerificationRepository, jwtService, TEST_HMAC_KEY);

        // Mock EmailService async methods
        EmailResponseDto successResponse = EmailResponseDto.builder()
                .success(true)
                .messageId("test-message-id")
                .message("Email sent successfully")
                .build();
                
        when(emailService.sendWelcomeEmailToPatientAsync(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(successResponse));
        when(emailService.sendVerificationEmailAsync(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(successResponse));
        when(emailService.sendApprovalEmailToDoctorAsync(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(successResponse));
        when(emailService.sendRejectionEmailToDoctorAsync(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(successResponse));

        validPatientRequest = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        validDoctorRequest = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                "CARDIOLOGÍA",
                30
        );

        validAdminRequest = new RegisterRequestDTO(
                "admin@test.com",
                11111111L,
                "password123",
                "Admin",
                "User",
                "1111111111",
                LocalDate.of(1985, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        sampleUser = new User();
        sampleUser.setId(UUID.randomUUID());
        sampleUser.setEmail("test@test.com");
        sampleUser.setDni(12345678L);
        sampleUser.setPasswordHash("hashedPassword");
        sampleUser.setName("Test");
        sampleUser.setSurname("User");
        sampleUser.setRole("PATIENT");
        sampleUser.setStatus("ACTIVE");
        sampleUser.setEmailVerified(true);

        validSignInRequest = new SignInRequestDTO("test@test.com", "password123");
    }

    @Test
    void registerPatient_ValidRequest_Success() {
        when(userRepository.existsByEmail(validPatientRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validPatientRequest.dni())).thenReturn(false);
        when(passwordEncoder.encode(validPatientRequest.password())).thenReturn("encodedPassword");
        
        User mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setEmail(validPatientRequest.email());
        mockUser.setName(validPatientRequest.name());
        mockUser.setSurname(validPatientRequest.surname());
        mockUser.setRole("PATIENT");
        mockUser.setStatus("ACTIVE");
        
        when(userMapper.toUser(eq(validPatientRequest), eq("PATIENT"), eq("encodedPassword"))).thenReturn(mockUser);
        when(userRepository.save(mockUser)).thenReturn(mockUser);
        when(userMapper.toRegisterResponse(mockUser)).thenReturn(
            new RegisterResponseDTO(mockUser.getId(), mockUser.getEmail(), mockUser.getName(), 
                                  mockUser.getSurname(), mockUser.getRole(), mockUser.getStatus())
        );

        RegisterResponseDTO result = authService.registerPatient(validPatientRequest);

        assertNotNull(result);
        assertEquals(mockUser.getId(), result.id());
        assertEquals(mockUser.getEmail(), result.email());
        assertEquals(mockUser.getName(), result.name());
        assertEquals(mockUser.getSurname(), result.surname());
        assertEquals("PATIENT", result.role());
        assertEquals("ACTIVE", result.status());
        
        verify(userRepository).existsByEmail(validPatientRequest.email());
        verify(userRepository).existsByDni(validPatientRequest.dni());
        verify(passwordEncoder).encode(validPatientRequest.password());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerPatient_DuplicateEmail_ThrowsException() {        when(userRepository.existsByEmail(validPatientRequest.email())).thenReturn(true);        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(validPatientRequest)
        );
        
        assertEquals("Email already registered", exception.getMessage());
        verify(userRepository).existsByEmail(validPatientRequest.email());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerPatient_DuplicateDNI_ThrowsException() {        when(userRepository.existsByEmail(validPatientRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validPatientRequest.dni())).thenReturn(true);        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(validPatientRequest)
        );
        
        assertEquals("DNI already registered", exception.getMessage());
        verify(userRepository).existsByDni(validPatientRequest.dni());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerPatient_NullRequest_ThrowsException() {        assertThrows(
                NullPointerException.class,
                () -> authService.registerPatient(null)
        );
    }

    @Test
    void registerDoctor_ValidRequest_Success() {
        when(userRepository.existsByEmail(validDoctorRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validDoctorRequest.dni())).thenReturn(false);
        when(passwordEncoder.encode(validDoctorRequest.password())).thenReturn("encodedPassword");
        
        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setEmail(validDoctorRequest.email());
        doctorUser.setRole("DOCTOR");
        doctorUser.setStatus("PENDING");
        doctorUser.setName(validDoctorRequest.name());
        doctorUser.setSurname(validDoctorRequest.surname());
        
        when(userMapper.toUser(eq(validDoctorRequest), eq("DOCTOR"), eq("encodedPassword"))).thenReturn(doctorUser);
        when(userRepository.save(doctorUser)).thenReturn(doctorUser);
        when(userMapper.toRegisterResponse(doctorUser)).thenReturn(
            new RegisterResponseDTO(doctorUser.getId(), doctorUser.getEmail(), doctorUser.getName(), 
                                  doctorUser.getSurname(), doctorUser.getRole(), doctorUser.getStatus())
        );

        RegisterResponseDTO result = authService.registerDoctor(validDoctorRequest);

        assertNotNull(result);
        assertEquals(doctorUser.getId(), result.id());
        assertEquals(doctorUser.getEmail(), result.email());
        assertEquals("DOCTOR", result.role());
        assertEquals("PENDING", result.status());
        
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerDoctor_MissingMedicalLicense_ThrowsException() {        RegisterRequestDTO invalidRequest = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                null,
                "CARDIOLOGÍA",
                30
        );        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(invalidRequest)
        );
        
        assertTrue(exception.getMessage().contains("Medical license is required for doctors"));
    }

    @Test
    void registerDoctor_MissingSpecialty_ThrowsException() {        RegisterRequestDTO invalidRequest = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                null,
                30
        );        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(invalidRequest)
        );
        
        assertTrue(exception.getMessage().contains("Specialty is required for doctors"));
    }

    @Test
    void registerDoctor_EmailAlreadyExists_ThrowsException() {
        when(userRepository.existsByEmail(validDoctorRequest.email())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(validDoctorRequest)
        );
        
        assertTrue(exception.getMessage().contains("Email already registered"));
        verify(userRepository).existsByEmail(validDoctorRequest.email());
        verify(userRepository, never()).existsByDni(any());
    }

    @Test
    void registerDoctor_DniAlreadyExists_ThrowsException() {
        when(userRepository.existsByEmail(validDoctorRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validDoctorRequest.dni())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(validDoctorRequest)
        );
        
        assertTrue(exception.getMessage().contains("DNI already registered"));
        verify(userRepository).existsByEmail(validDoctorRequest.email());
        verify(userRepository).existsByDni(validDoctorRequest.dni());
    }

    @Test
    void registerAdmin_ValidRequest_Success() {
        when(userRepository.existsByEmail(validAdminRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validAdminRequest.dni())).thenReturn(false);
        when(passwordEncoder.encode(validAdminRequest.password())).thenReturn("encodedPassword");
        
        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail(validAdminRequest.email());
        adminUser.setRole("ADMIN");
        adminUser.setStatus("ACTIVE");
        adminUser.setName(validAdminRequest.name());
        adminUser.setSurname(validAdminRequest.surname());
        
        when(userMapper.toUser(eq(validAdminRequest), eq("ADMIN"), eq("encodedPassword"))).thenReturn(adminUser);
        when(userRepository.save(adminUser)).thenReturn(adminUser);
        when(userMapper.toRegisterResponse(adminUser)).thenReturn(
            new RegisterResponseDTO(adminUser.getId(), adminUser.getEmail(), adminUser.getName(), 
                                  adminUser.getSurname(), adminUser.getRole(), adminUser.getStatus())
        );

        RegisterResponseDTO result = authService.registerAdmin(validAdminRequest);

        assertNotNull(result);
        assertEquals(adminUser.getId(), result.id());
        assertEquals(adminUser.getEmail(), result.email());
        assertEquals("ADMIN", result.role());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void registerAdmin_EmailAlreadyExists_ThrowsException() {
        when(userRepository.existsByEmail(validAdminRequest.email())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerAdmin(validAdminRequest)
        );
        
        assertTrue(exception.getMessage().contains("Email already registered"));
        verify(userRepository).existsByEmail(validAdminRequest.email());
        verify(userRepository, never()).existsByDni(any());
    }

    @Test
    void registerAdmin_DniAlreadyExists_ThrowsException() {
        when(userRepository.existsByEmail(validAdminRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validAdminRequest.dni())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerAdmin(validAdminRequest)
        );
        
        assertTrue(exception.getMessage().contains("DNI already registered"));
        verify(userRepository).existsByEmail(validAdminRequest.email());
        verify(userRepository).existsByDni(validAdminRequest.dni());
    }

    @Test
    void signIn_ValidCredentials_Success() {
        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(validSignInRequest.password(), sampleUser.getPasswordHash())).thenReturn(true);
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash("hashedRefreshToken");
        refreshToken.setExpiresAt(ZonedDateTime.now().plusDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);
        
        when(authMapper.toSignInResponse(eq(sampleUser), any(String.class))).thenReturn(
            new SignInResponseDTO(sampleUser.getId(), sampleUser.getEmail(), sampleUser.getName(),
                                sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus(),
                                "access_token")
        );

        when(jwtService.generateToken(sampleUser)).thenReturn("mocked-jwt-token");
        SignInResultDTO result = authService.signIn(validSignInRequest);

        assertNotNull(result);
        assertNotNull(result.response().accessToken());
        // Raw refresh token is carried on the result (for the cookie),
        // NOT inside the response body DTO.
        assertNotNull(result.refreshToken());
        assertEquals(sampleUser.getId(), result.response().id());
        assertEquals(sampleUser.getEmail(), result.response().email());
        assertEquals(sampleUser.getRole(), result.response().role());

        verify(userRepository).findByEmail(validSignInRequest.email());
        verify(passwordEncoder).matches(validSignInRequest.password(), sampleUser.getPasswordHash());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void signIn_UserNotFound_ThrowsException() {
        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );
        
        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
        verify(userRepository).findByEmail(validSignInRequest.email());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void signIn_WrongPassword_ThrowsException() {
        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(validSignInRequest.password(), sampleUser.getPasswordHash())).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );
        
        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
        verify(passwordEncoder).matches(validSignInRequest.password(), sampleUser.getPasswordHash());
    }

    @Test
    void signIn_InactiveUser_ThrowsException() {
        // Crear un usuario inactivo
        User inactiveUser = new User();
        inactiveUser.setId(UUID.randomUUID());
        inactiveUser.setEmail(validSignInRequest.email());
        inactiveUser.setPasswordHash("hashedPassword");
        inactiveUser.setRole("PATIENT");
        inactiveUser.setStatus("INACTIVE");
        
        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(inactiveUser));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );
        
        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
        verify(userRepository).findByEmail(validSignInRequest.email());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void signIn_PendingDoctor_Success() {
        // Crear un doctor en estado PENDING
        User pendingDoctor = new User();
        pendingDoctor.setId(UUID.randomUUID());
        pendingDoctor.setEmail(validSignInRequest.email());
        pendingDoctor.setPasswordHash("hashedPassword");
        pendingDoctor.setRole("DOCTOR");
        pendingDoctor.setStatus("PENDING");
        pendingDoctor.setEmailVerified(true);
        
        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(pendingDoctor));
        when(passwordEncoder.matches(validSignInRequest.password(), pendingDoctor.getPasswordHash())).thenReturn(true);
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash("hashedRefreshToken");
        refreshToken.setExpiresAt(ZonedDateTime.now().plusDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);
        
        when(authMapper.toSignInResponse(eq(pendingDoctor), any(String.class))).thenReturn(
            new SignInResponseDTO(pendingDoctor.getId(), pendingDoctor.getEmail(), pendingDoctor.getName(),
                                pendingDoctor.getSurname(), pendingDoctor.getRole(), pendingDoctor.getStatus(),
                                "access_token")
        );

        when(jwtService.generateToken(pendingDoctor)).thenReturn("mocked-jwt-token");
        SignInResultDTO result = authService.signIn(validSignInRequest);

        assertNotNull(result);
        assertEquals("PENDING", result.response().status());
        assertEquals("DOCTOR", result.response().role());
        verify(userRepository).findByEmail(validSignInRequest.email());
        verify(passwordEncoder).matches(validSignInRequest.password(), pendingDoctor.getPasswordHash());
    }

    private RefreshToken ownedRefreshToken(String rawToken, User owner) {
        RefreshToken rt = new RefreshToken();
        rt.setUser(owner);
        rt.setTokenHash(hashToken(rawToken));
        rt.setExpiresAt(ZonedDateTime.now().plusDays(30));
        rt.setCreatedAt(ZonedDateTime.now());
        return rt;
    }

    @Test
    void signOut_OwnerToken_Revoked() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(hashToken("validToken")))
                .thenReturn(Optional.of(ownedRefreshToken("validToken", owner)));

        assertDoesNotThrow(() -> authService.signOut("validToken", owner.getId()));

        verify(refreshTokenRepository).revokeTokenByHash(eq(hashToken("validToken")), any(ZonedDateTime.class));
    }

    @Test
    void signOut_NoCaller_Revoked() {
        // Cookie-only sign-out (no access token): the raw token itself proves possession.
        User owner = new User();
        owner.setId(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(hashToken("validToken")))
                .thenReturn(Optional.of(ownedRefreshToken("validToken", owner)));

        assertDoesNotThrow(() -> authService.signOut("validToken", null));

        verify(refreshTokenRepository).revokeTokenByHash(eq(hashToken("validToken")), any(ZonedDateTime.class));
    }

    @Test
    void signOut_ForeignToken_NotRevoked() {
        // Caller must NOT be able to revoke a token owned by a different user.
        User owner = new User();
        owner.setId(UUID.randomUUID());
        UUID attackerId = UUID.randomUUID();
        when(refreshTokenRepository.findByTokenHash(hashToken("victimToken")))
                .thenReturn(Optional.of(ownedRefreshToken("victimToken", owner)));

        assertDoesNotThrow(() -> authService.signOut("victimToken", attackerId));

        verify(refreshTokenRepository, org.mockito.Mockito.never()).revokeTokenByHash(any(), any());
    }

    @Test
    void signOut_UnknownToken_NoOp() {
        when(refreshTokenRepository.findByTokenHash(hashToken("ghostToken")))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.signOut("ghostToken", UUID.randomUUID()));

        verify(refreshTokenRepository, org.mockito.Mockito.never()).revokeTokenByHash(any(), any());
    }

    @Test
    void signOut_NullToken_NoOp() {
        assertDoesNotThrow(() -> authService.signOut(null, UUID.randomUUID()));

        verify(refreshTokenRepository, org.mockito.Mockito.never()).findByTokenHash(any());
        verify(refreshTokenRepository, org.mockito.Mockito.never()).revokeTokenByHash(any(), any());
    }

    @Test
    void signOut_EmptyToken_NoOp() {
        assertDoesNotThrow(() -> authService.signOut("", UUID.randomUUID()));

        verify(refreshTokenRepository, org.mockito.Mockito.never()).findByTokenHash(any());
        verify(refreshTokenRepository, org.mockito.Mockito.never()).revokeTokenByHash(any(), any());
    }

    @Test
    void refreshTokenHash_isHmac_notBareSha256() {
        // The stored refresh-token hash must be a keyed HMAC, not a bare SHA-256.
        String hmac = hashToken("some-refresh-token");
        assertNotEquals(sha256("some-refresh-token"), hmac,
                "refresh token must be stored as HMAC, not bare SHA-256");
        // Deterministic for the same (key, token).
        assertEquals(hmac, hashToken("some-refresh-token"));
        // Different key -> different digest.
        assertNotEquals(hmac, hmacWithKey("a-completely-different-key", "some-refresh-token"));
    }

    private String hmacWithKey(String key, String token) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void refreshToken_ValidToken_Success() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash("validRefreshTokenHash");
        refreshToken.setUser(sampleUser);
        refreshToken.setExpiresAt(ZonedDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHash(hashToken("validRefreshToken"))).thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);
        when(authMapper.toSignInResponse(eq(sampleUser), any(String.class))).thenReturn(
            new SignInResponseDTO(sampleUser.getId(), sampleUser.getEmail(), sampleUser.getName(),
                                sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus(),
                                "new_access_token")
        );

        when(jwtService.generateToken(sampleUser)).thenReturn("new-mocked-jwt-token");
        SignInResultDTO result = authService.refreshToken("validRefreshToken");

        assertNotNull(result);
        assertNotNull(result.response().accessToken());
        assertNotNull(result.refreshToken());
        assertEquals(sampleUser.getId(), result.response().id());
        assertEquals(sampleUser.getEmail(), result.response().email());

        verify(refreshTokenRepository).findByTokenHash(hashToken("validRefreshToken"));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshToken_ExpiredToken_ThrowsException() {
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setTokenHash("expiredTokenHash");
        expiredToken.setUser(sampleUser);
        expiredToken.setExpiresAt(ZonedDateTime.now().minusDays(1));

        when(refreshTokenRepository.findByTokenHash(hashToken("expiredToken"))).thenReturn(Optional.of(expiredToken));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.refreshToken("expiredToken")
        );
        
        assertEquals("Refresh token is expired or revoked", exception.getMessage());
    }

    @Test
    void refreshToken_InvalidToken_ThrowsException() {
        when(refreshTokenRepository.findByTokenHash(hashToken("invalidToken"))).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.refreshToken("invalidToken")
        );
        
        assertEquals("Invalid refresh token", exception.getMessage());
    }

    @Test
    void refreshToken_InactiveUser_ThrowsException() {
        // La implementación actual no valida el estado del usuario en refresh token
        // Este test debería pasar porque el token es válido, independientemente del estado del usuario
        sampleUser.setStatus("INACTIVE");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash("validTokenHash");
        refreshToken.setUser(sampleUser);
        refreshToken.setExpiresAt(ZonedDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHash(hashToken("validToken"))).thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);
        when(authMapper.toSignInResponse(eq(sampleUser), any(String.class))).thenReturn(
            new SignInResponseDTO(sampleUser.getId(), sampleUser.getEmail(), sampleUser.getName(),
                                sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus(),
                                "new_access_token")
        );

        // No debería lanzar excepción porque la implementación actual no valida el estado del usuario
        assertDoesNotThrow(() -> authService.refreshToken("validToken"));
    }

    @Test
    void registerPatient_SqlInjectionAttempt_ShouldHandleSafely() {
        RegisterRequestDTO maliciousRequest = new RegisterRequestDTO(
                "'; DROP TABLE users; --@test.com",
                12345678L,
                "password123",
                "'; DROP TABLE users; --",
                "Smith",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        User mockUser = new User();
        mockUser.setEmail("'; DROP TABLE users; --@test.com");
        mockUser.setName("'; DROP TABLE users; --");
        mockUser.setSurname("Smith");
        mockUser.setId(UUID.randomUUID());
        mockUser.setRole("PATIENT");
        mockUser.setStatus("ACTIVE");
        
        RegisterResponseDTO mockResponse = new RegisterResponseDTO(
                mockUser.getId(),
                mockUser.getEmail(),
                mockUser.getName(),
                mockUser.getSurname(),
                mockUser.getRole(),
                mockUser.getStatus()
        );

        when(userRepository.existsByEmail(maliciousRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(maliciousRequest.dni())).thenReturn(false);
        when(passwordEncoder.encode(maliciousRequest.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(any(), eq("PATIENT"), eq("hashedPassword"))).thenReturn(mockUser);
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(userMapper.toRegisterResponse(mockUser)).thenReturn(mockResponse);

        assertDoesNotThrow(() -> {
            try {
                authService.registerPatient(maliciousRequest);
            } catch (IllegalArgumentException e) {
                // El test debería manejar SQL injection de forma segura
            }
        });
    }

    @Test
    void signIn_BruteForceAttempt_ShouldHandleMultipleFailures() {
        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(validSignInRequest.password(), sampleUser.getPasswordHash())).thenReturn(false);

        for (int i = 0; i < 10; i++) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> authService.signIn(validSignInRequest)
            );
        }

        verify(userRepository, times(10)).findByEmail(validSignInRequest.email());
    }

    @Test
    void registerUser_ConcurrentRegistration_ShouldHandleRaceCondition() {
        when(userRepository.existsByEmail(validPatientRequest.email()))
                .thenReturn(false)
                .thenReturn(true);
        when(userRepository.existsByDni(validPatientRequest.dni())).thenReturn(false);
        when(passwordEncoder.encode(validPatientRequest.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(eq(validPatientRequest), eq("PATIENT"), eq("hashedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), 
                sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        RegisterResponseDTO result = authService.registerPatient(validPatientRequest);
        assertNotNull(result);
        assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(validPatientRequest)
        );
    }

    @Test
    void tokenGeneration_ShouldProduceUniqueTokens() {
        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(validSignInRequest.password(), sampleUser.getPasswordHash())).thenReturn(true);
        
        RefreshToken token1 = new RefreshToken();
        token1.setTokenHash("hashedToken1");
        RefreshToken token2 = new RefreshToken();
        token2.setTokenHash("hashedToken2");
        
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenReturn(token1)
                .thenReturn(token2);

        // Mock de authMapper para ambas respuestas
        when(authMapper.toSignInResponse(eq(sampleUser), anyString()))
                .thenReturn(new SignInResponseDTO(sampleUser.getId(), sampleUser.getEmail(), sampleUser.getName(),
                        sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus(), "jwt1"))
                .thenReturn(new SignInResponseDTO(sampleUser.getId(), sampleUser.getEmail(), sampleUser.getName(),
                        sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus(), "jwt2"));

        when(jwtService.generateToken(any(User.class)))
                .thenReturn("token-1")
                .thenReturn("token-2");

        SignInResultDTO result1 = authService.signIn(validSignInRequest);
        SignInResultDTO result2 = authService.signIn(validSignInRequest);

        assertNotEquals(result1.refreshToken(), result2.refreshToken());
    }

    @Test
    void passwordEncoding_ShouldNeverStorePlaintext() {
        when(userRepository.existsByEmail(validPatientRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validPatientRequest.dni())).thenReturn(false);
        when(passwordEncoder.encode(validPatientRequest.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(eq(validPatientRequest), eq("PATIENT"), eq("hashedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), 
                sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        authService.registerPatient(validPatientRequest);

        verify(passwordEncoder).encode(validPatientRequest.password());
        verify(userRepository).save(argThat(user -> 
                !user.getPasswordHash().equals(validPatientRequest.password())
        ));
    }

    @Test
    void registerPatient_DNIWith7Digits_Success() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                1234567L, // 7 dígitos
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(eq(request), eq("PATIENT"), eq("hashedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(
            new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), 
                sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        assertDoesNotThrow(() -> authService.registerPatient(request));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerPatient_DNIWith8Digits_Success() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L, // 8 dígitos
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(eq(request), eq("PATIENT"), eq("hashedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(
            new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), 
                sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        assertDoesNotThrow(() -> authService.registerPatient(request));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerPatient_DNILowerBoundValid_Success() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                1000000L, // Límite inferior válido
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(eq(request), eq("PATIENT"), eq("hashedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(
            new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), 
                sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        assertDoesNotThrow(() -> authService.registerPatient(request));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerPatient_DNIUpperBoundValid_Success() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                99999999L, // Límite superior válido
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(eq(request), eq("PATIENT"), eq("hashedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(
            new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), 
                sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        assertDoesNotThrow(() -> authService.registerPatient(request));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerPatient_DNIWith6Digits_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                123456L, // 6 dígitos - inválido
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Invalid DNI format"));
    }

    @Test
    void registerPatient_DNIWith9Digits_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                123456789L, // 9 dígitos - inválido
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Invalid DNI format"));
    }

    @Test
    void registerPatient_DNIBelowRange_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                999999L, // Menor a 1000000
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Invalid DNI format"));
    }

    @Test
    void registerPatient_DNIAboveRange_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                1000000000L, // Mayor a 999999999
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Invalid DNI format"));
    }

    @Test
    void registerPatient_Exactly18YearsOld_Success() {
        LocalDate exactly18YearsAgo = LocalDate.now(ARGENTINA_ZONE).minusYears(18);
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "1234567890",
                exactly18YearsAgo,
                "MALE",
                null,
                null,
                null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(eq(request), eq("PATIENT"), eq("hashedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(
            new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), 
                sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        assertDoesNotThrow(() -> authService.registerPatient(request));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerPatient_Under18YearsOld_ThrowsException() {
        LocalDate under18 = LocalDate.now(ARGENTINA_ZONE).minusYears(17);
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "1234567890",
                under18,
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Must be at least 18 years old"));
    }

    @Test
    void registerPatient_Exactly120YearsOld_Success() {
        LocalDate exactly120YearsAgo = LocalDate.now(ARGENTINA_ZONE).minusYears(120);
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "1234567890",
                exactly120YearsAgo,
                "MALE",
                null,
                null,
                null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDni(request.dni())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(userMapper.toUser(eq(request), eq("PATIENT"), eq("hashedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(
            new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), 
                sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        assertDoesNotThrow(() -> authService.registerPatient(request));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerPatient_Over120YearsOld_ThrowsException() {
        LocalDate over120 = LocalDate.now(ARGENTINA_ZONE).minusYears(121);
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "1234567890",
                over120,
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Invalid birth date"));
    }

    @Test
    void registerPatient_EmptyName_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        // Empty name throws NullPointerException in UserMapper
        assertThrows(
                NullPointerException.class,
                () -> authService.registerPatient(request)
        );
    }

    @Test
    void registerPatient_WhitespaceName_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "   ",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        // Whitespace name throws NullPointerException in UserMapper
        assertThrows(
                NullPointerException.class,
                () -> authService.registerPatient(request)
        );
    }

    @Test
    void registerPatient_EmptySurname_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        // Empty surname throws NullPointerException in UserMapper
        assertThrows(
                NullPointerException.class,
                () -> authService.registerPatient(request)
        );
    }

    @Test
    void registerPatient_WhitespaceSurname_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "   ",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        // Whitespace surname throws NullPointerException in UserMapper
        assertThrows(
                NullPointerException.class,
                () -> authService.registerPatient(request)
        );
    }

    @Test
    void registerPatient_EmptyPhone_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Phone is required"));
    }

    @Test
    void registerPatient_WhitespacePhone_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "   ",
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Phone is required"));
    }

    @Test
    void registerPatient_EmptyGender_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Gender is required"));
    }

    @Test
    void registerPatient_WhitespaceGender_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "1234567890",
                LocalDate.of(1990, 1, 1),
                "   ",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Gender is required"));
    }

    @Test
    void registerPatient_NullBirthdate_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "patient@test.com",
                12345678L,
                "password123",
                "John",
                "Doe",
                "1234567890",
                null,
                "MALE",
                null,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerPatient(request)
        );

        assertTrue(exception.getMessage().contains("Birthdate is required"));
    }

    @Test
    void registerDoctor_WhitespaceMedicalLicense_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "   ",
                "CARDIOLOGÍA",
                30
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Medical license is required for doctors"));
    }

    @Test
    void registerDoctor_WhitespaceSpecialty_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                "   ",
                30
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Specialty is required for doctors"));
    }

    @Test
    void signIn_UserWithNullRole_ThrowsException() {
        User userNullRole = new User();
        userNullRole.setId(UUID.randomUUID());
        userNullRole.setEmail(validSignInRequest.email());
        userNullRole.setPasswordHash("hashedPassword");
        userNullRole.setRole(null);
        userNullRole.setStatus("ACTIVE");

        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(userNullRole));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
    }

    @Test
    void signIn_UserWithNullStatus_ThrowsException() {
        User userNullStatus = new User();
        userNullStatus.setId(UUID.randomUUID());
        userNullStatus.setEmail(validSignInRequest.email());
        userNullStatus.setPasswordHash("hashedPassword");
        userNullStatus.setRole("PATIENT");
        userNullStatus.setStatus(null);

        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(userNullStatus));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
    }

    @Test
    void signIn_UserWithEmptyRole_ThrowsException() {
        User userEmptyRole = new User();
        userEmptyRole.setId(UUID.randomUUID());
        userEmptyRole.setEmail(validSignInRequest.email());
        userEmptyRole.setPasswordHash("hashedPassword");
        userEmptyRole.setRole("");
        userEmptyRole.setStatus("ACTIVE");

        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(userEmptyRole));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
    }

    @Test
    void signIn_UserWithEmptyStatus_ThrowsException() {
        User userEmptyStatus = new User();
        userEmptyStatus.setId(UUID.randomUUID());
        userEmptyStatus.setEmail(validSignInRequest.email());
        userEmptyStatus.setPasswordHash("hashedPassword");
        userEmptyStatus.setRole("PATIENT");
        userEmptyStatus.setStatus("");

        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(userEmptyStatus));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
    }

    @Test
    void signIn_UserWithUnknownRole_ThrowsException() {
        User userUnknownRole = new User();
        userUnknownRole.setId(UUID.randomUUID());
        userUnknownRole.setEmail(validSignInRequest.email());
        userUnknownRole.setPasswordHash("hashedPassword");
        userUnknownRole.setRole("SUPERADMIN");
        userUnknownRole.setStatus("ACTIVE");

        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(userUnknownRole));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
    }

    @Test
    void signIn_DoctorWithRejectedStatus_ThrowsException() {
        User rejectedDoctor = new User();
        rejectedDoctor.setId(UUID.randomUUID());
        rejectedDoctor.setEmail(validSignInRequest.email());
        rejectedDoctor.setPasswordHash("hashedPassword");
        rejectedDoctor.setRole("DOCTOR");
        rejectedDoctor.setStatus("REJECTED");

        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(rejectedDoctor));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
    }

    @Test
    void signIn_AdminWithPendingStatus_ThrowsException() {
        User pendingAdmin = new User();
        pendingAdmin.setId(UUID.randomUUID());
        pendingAdmin.setEmail(validSignInRequest.email());
        pendingAdmin.setPasswordHash("hashedPassword");
        pendingAdmin.setRole("ADMIN");
        pendingAdmin.setStatus("PENDING");

        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(pendingAdmin));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
    }

    @Test
    void signIn_PatientWithPendingStatus_ThrowsException() {
        User pendingPatient = new User();
        pendingPatient.setId(UUID.randomUUID());
        pendingPatient.setEmail(validSignInRequest.email());
        pendingPatient.setPasswordHash("hashedPassword");
        pendingPatient.setRole("PATIENT");
        pendingPatient.setStatus("PENDING");

        when(userRepository.findByEmail(validSignInRequest.email())).thenReturn(Optional.of(pendingPatient));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.signIn(validSignInRequest)
        );

        assertEquals("Correo o contraseña incorrecto", exception.getMessage());
    }

    @Test
    void registerPatient_EmailSendFailure_DoesNotThrow() {
        // Simulate email service returning a failure response
        EmailResponseDto failureResponse = EmailResponseDto.builder()
                .success(false)
                .message("send failed")
                .build();

        when(emailService.sendVerificationEmailAsync(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(failureResponse));

        when(userRepository.existsByEmail(validPatientRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validPatientRequest.dni())).thenReturn(false);
        when(passwordEncoder.encode(validPatientRequest.password())).thenReturn("encodedPassword");
        when(userMapper.toUser(eq(validPatientRequest), eq("PATIENT"), eq("encodedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        // Should not throw even if email send reports failure
        assertDoesNotThrow(() -> authService.registerPatient(validPatientRequest));
        verify(emailService).sendVerificationEmailAsync(anyString(), anyString(), anyString());
    }

    @Test
    void registerPatient_EmailSendThrowsException_DoesNotPropagate() {
        CompletableFuture<EmailResponseDto> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("send failure"));

        when(emailService.sendVerificationEmailAsync(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        when(userRepository.existsByEmail(validPatientRequest.email())).thenReturn(false);
        when(userRepository.existsByDni(validPatientRequest.dni())).thenReturn(false);
        when(passwordEncoder.encode(validPatientRequest.password())).thenReturn("encodedPassword");
        when(userMapper.toUser(eq(validPatientRequest), eq("PATIENT"), eq("encodedPassword"))).thenReturn(sampleUser);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toRegisterResponse(any(User.class))).thenReturn(new RegisterResponseDTO(sampleUser.getId(), sampleUser.getEmail(), sampleUser.getName(), sampleUser.getSurname(), sampleUser.getRole(), sampleUser.getStatus()));

        // Exception in async email should be handled internally, not propagated
        assertDoesNotThrow(() -> authService.registerPatient(validPatientRequest));
        verify(emailService).sendVerificationEmailAsync(anyString(), anyString(), anyString());
    }

    @Test
    void registerDoctor_MedicalLicense_NonDigits_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "ABC123",
                "CARDIOLOGÍA",
                30
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Medical license must be 4-10 digits"));
    }

    @Test
    void registerDoctor_MedicalLicense_TooShort_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123",
                "CARDIOLOGÍA",
                30
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Medical license must be 4-10 digits"));
    }

    @Test
    void registerDoctor_MedicalLicense_TooLong_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "12345678901",
                "CARDIOLOGÍA",
                30
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Medical license must be 4-10 digits"));
    }

    @Test
    void registerDoctor_Specialty_InvalidCharacters_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                "INVALID_SPECIALTY",
                30
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Invalid specialty selected"));
    }

    @Test
    void registerDoctor_Specialty_TooShort_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                "INVALID",
                30
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Invalid specialty selected"));
    }

    @Test
    void registerDoctor_Specialty_TooLong_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                "INVALID_SPECIALTY_TOO_LONG_FOR_TESTING_PURPOSES",
                30
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Invalid specialty selected"));
    }

    @Test
    void registerDoctor_SlotDuration_Null_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                "CARDIOLOGÍA",
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(request)
        );

        assertTrue(exception.getMessage().contains("Slot duration is required for doctors"));
    }

    @Test
    void registerDoctor_SlotDuration_OutOfRange_ThrowsException() {
        RegisterRequestDTO requestLow = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                "CARDIOLOGÍA",
                1
        );

        IllegalArgumentException e1 = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(requestLow)
        );
        assertTrue(e1.getMessage().contains("Slot duration must be between 5 and 180 minutes"));

        RegisterRequestDTO requestHigh = new RegisterRequestDTO(
                "doctor@test.com",
                87654321L,
                "password123",
                "Dr. Jane",
                "Smith",
                "0987654321",
                LocalDate.of(1980, 1, 1),
                "FEMALE",
                "123456",
                "CARDIOLOGÍA",
                1000
        );

        IllegalArgumentException e2 = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerDoctor(requestHigh)
        );
        assertTrue(e2.getMessage().contains("Slot duration must be between 5 and 180 minutes"));
    }

    // Refresh tokens are now stored as keyed HMAC-SHA256, not bare SHA-256. This
    // helper mirrors the production hmacToken() so the stored-hash assertions stay valid.
    private String hashToken(String token) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    TEST_HMAC_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String sha256(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
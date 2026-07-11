package com.medibook.api.service;

import com.medibook.api.dto.Auth.RegisterRequestDTO;
import com.medibook.api.dto.Auth.RegisterResponseDTO;
import com.medibook.api.dto.Auth.SignInRequestDTO;
import com.medibook.api.dto.Auth.SignInResponseDTO;
import com.medibook.api.dto.Auth.SignInResultDTO;
import com.medibook.api.entity.EmailVerification;
import com.medibook.api.entity.RefreshToken;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.AuthMapper;
import com.medibook.api.mapper.UserMapper;
import com.medibook.api.repository.EmailVerificationRepository;
import com.medibook.api.repository.RefreshTokenRepository;
import com.medibook.api.repository.UserRepository;
import com.medibook.api.util.LogMaskingUtil;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.MessageDigest;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;


@Service
@Slf4j
class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuthMapper authMapper;
    private final EmailService emailService;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JwtService jwtService;

    // BSEC-L-2: server-side key used to HMAC refresh tokens before persisting them.
    private final String refreshTokenHmacKey;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final java.util.Set<String> VALID_SPECIALTIES = java.util.Set.of(
        "ALERGIA E INMUNOLOGÍA",
        "ANATOMÍA PATOLÓGICA",
        "ANESTESIOLOGÍA",
        "ANGIOLOGÍA GENERAL y HEMODINAMIA",
        "CARDIOLOGÍA",
        "CARDIÓLOGO INFANTIL",
        "CIRUGÍA GENERAL",
        "CIRUGÍA CARDIOVASCULAR",
        "CIRUGÍA DE CABEZA Y CUELLO",
        "CIRUGÍA DE TÓRAX (CIRUGÍA TORÁCICA)",
        "CIRUGÍA INFANTIL (CIRUGÍA PEDIÁTRICA)",
        "CIRUGÍA PLÁSTICA Y REPARADORA",
        "CIRUGÍA VASCULAR PERIFÉRICA",
        "CLÍNICA MÉDICA",
        "COLOPROCTOLOGÍA",
        "DERMATOLOGÍA",
        "DIAGNOSTICO POR IMÁGENES",
        "ENDOCRINOLOGÍA",
        "ENDOCRINÓLOGO INFANTIL",
        "FARMACOLOGÍA CLÍNICA",
        "FISIATRÍA (MEDICINA FÍSICA Y REHABILITACIÓN)",
        "GASTROENTEROLOGÍA",
        "GASTROENTERÓLOGO INFANTIL",
        "GENÉTICA MEDICA",
        "GERIATRÍA",
        "GINECOLOGÍA",
        "HEMATOLOGÍA",
        "HEMATÓLOGO INFANTIL",
        "HEMOTERAPIA E INMUNOHEMATOLOGÍA",
        "INFECTOLOGÍA",
        "INFECTÓLOGO INFANTIL",
        "MEDICINA DEL DEPORTE",
        "MEDICINA GENERAL y/o MEDICINA DE FAMILIA",
        "MEDICINA LEGAL",
        "MEDICINA NUCLEAR",
        "MEDICINA DEL TRABAJO",
        "NEFROLOGÍA",
        "NEFRÓLOGO INFANTIL",
        "NEONATOLOGÍA",
        "NEUMONOLOGÍA",
        "NEUMONÓLOGO INFANTIL",
        "NEUROCIRUGÍA",
        "NEUROLOGÍA",
        "NEURÓLOGO INFANTIL",
        "NUTRICIÓN",
        "OBSTETRICIA",
        "OFTALMOLOGÍA",
        "ONCOLOGÍA",
        "ONCÓLOGO INFANTIL",
        "ORTOPEDIA Y TRAUMATOLOGÍA",
        "OTORRINOLARINGOLOGÍA",
        "PEDIATRÍA",
        "PSIQUIATRÍA",
        "PSIQUIATRÍA INFANTO JUVENIL",
        "RADIOTERAPIA O TERAPIA RADIANTE",
        "REUMATOLOGÍA",
        "REUMATÓLOGO INFANTIL",
        "TERAPIA INTENSIVA",
        "TERAPISTA INTENSIVO INFANTIL",
        "TOCOGINECOLOGÍA",
        "TOXICOLOGÍA",
        "UROLOGÍA"
    );

    public AuthServiceImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            AuthMapper authMapper,
            EmailService emailService,
            EmailVerificationRepository emailVerificationRepository,
            JwtService jwtService,
            @Value("${app.refresh-token.hmac-key:${jwt.secret}}") String refreshTokenHmacKey) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.authMapper = authMapper;
        this.emailService = emailService;
        this.emailVerificationRepository = emailVerificationRepository;
        this.jwtService = jwtService;
        this.refreshTokenHmacKey = refreshTokenHmacKey;
    }

    @Override
    @Transactional
    public void verifyAccount(String rawToken) {
        String hashedToken = hashToken(rawToken);

        EmailVerification verification = emailVerificationRepository.findByCodeHash(hashedToken)
                .orElseThrow(() -> new IllegalArgumentException("Código de verificación inválido o no encontrado"));

        if (verification.getConsumedAt() != null) {
            throw new IllegalArgumentException("Este enlace ya fue utilizado");
        }

        if (verification.getExpiresAt().isBefore(ZonedDateTime.now(ARGENTINA_ZONE))) {
            throw new IllegalArgumentException("El enlace de verificación ha expirado");
        }

        User user = verification.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verification.setConsumedAt(ZonedDateTime.now(ARGENTINA_ZONE));
        emailVerificationRepository.save(verification);

        if ("PATIENT".equals(user.getRole())) {
            try {
                final String userEmail = user.getEmail();
                final String userName = user.getName();

                emailService.sendWelcomeEmailToPatientAsync(userEmail, userName);
                log.info("Email de bienvenida enviado a: {}", LogMaskingUtil.maskEmail(userEmail));

            } catch (Exception e) {
                log.warn("Error enviando email de bienvenida a {}: {}", LogMaskingUtil.maskEmail(user.getEmail()), e.getMessage());
            }
        }

        log.info("Cuenta verificada exitosamente para: {}", LogMaskingUtil.maskEmail(user.getEmail()));
    }

    @Override
    @Transactional
    public RegisterResponseDTO registerPatient(RegisterRequestDTO request) {
        validateCommonFields(request);
        
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        if (userRepository.existsByDni(request.dni())) {
            throw new IllegalArgumentException("DNI already registered");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = userMapper.toUser(request, "PATIENT", hashedPassword);

        user.setEmailVerified(false);
        
        user = userRepository.save(user);
        
        try {
            final String userEmail = user.getEmail();
            final String userName = user.getName();
            final String verificationToken = createVerificationForUser(user);
            
            emailService.sendVerificationEmailAsync(userEmail, userName, verificationToken);                
            
            log.info("Email de verificación enviado a: {}", LogMaskingUtil.maskEmail(userEmail));
        } catch (Exception e) {
            log.warn("Error enviando email de verificación a {}: {}", LogMaskingUtil.maskEmail(user.getEmail()), e.getMessage());
        }

        return userMapper.toRegisterResponse(user);
    }

    @Override
    @Transactional
    public RegisterResponseDTO registerDoctor(RegisterRequestDTO request) {        
        validateCommonFields(request);
        validateDoctorFields(request);
        
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (userRepository.existsByDni(request.dni())) {
            throw new IllegalArgumentException("DNI already registered");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = userMapper.toUser(request, "DOCTOR", hashedPassword);
        user.setStatus("PENDING");

        user.setEmailVerified(false);

        user = userRepository.save(user);

        try {
            final String userEmail = user.getEmail();
            final String userName = user.getName();
            final String verificationToken = createVerificationForUser(user);
            
            emailService.sendVerificationEmailAsync(userEmail, userName, verificationToken);                
            
            log.info("Email de verificación enviado a: {}", LogMaskingUtil.maskEmail(userEmail));
        } catch (Exception e) {
            log.warn("Error enviando email de verificación a {}: {}", LogMaskingUtil.maskEmail(user.getEmail()), e.getMessage());
        }

        return userMapper.toRegisterResponse(user);
    }

    @Override
    @Transactional
    public RegisterResponseDTO registerAdmin(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        if (userRepository.existsByDni(request.dni())) {
            throw new IllegalArgumentException("DNI already registered");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = userMapper.toUser(request, "ADMIN", hashedPassword);
        user = userRepository.save(user);

        return userMapper.toRegisterResponse(user);
    }

    @Override
    @Transactional
    public SignInResultDTO signIn(SignInRequestDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Correo o contraseña incorrecto"));

        if(!user.isEmailVerified()) {
            throw new IllegalArgumentException("Correo o contraseña incorrecto");
        }

        if (!isUserAuthorizedToSignIn(user)) {
            throw new IllegalArgumentException("Correo o contraseña incorrecto");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Correo o contraseña incorrecto");
        }

        String rawToken = generateSecureToken();
        String hashedToken = hmacToken(rawToken);

        RefreshToken refreshToken = createRefreshToken(user, hashedToken);
        refreshTokenRepository.save(refreshToken);

        String accessToken = generateAccessToken(user);

        // FSEC-H1 Stage 3: the raw refresh token is returned separately (for the httpOnly
        // cookie), NEVER inside the SignInResponseDTO body.
        return new SignInResultDTO(authMapper.toSignInResponse(user, accessToken), rawToken);
    }

    private boolean isUserAuthorizedToSignIn(User user) {
        String role = user.getRole();
        String status = user.getStatus();
        
        if (role == null || status == null || role.trim().isEmpty() || status.trim().isEmpty()) {
            return false;
        }
        
        return switch (role.trim().toUpperCase()) {
            case "PATIENT", "ADMIN" -> "ACTIVE".equalsIgnoreCase(status.trim());
            case "DOCTOR" -> "ACTIVE".equalsIgnoreCase(status.trim()) || "PENDING".equalsIgnoreCase(status.trim());
            default -> false;
        };
    }

    @Override
    @Transactional
    public void signOut(String rawRefreshToken, UUID callerId) {
        // BBUG-L2: a missing token is a logged no-op (do NOT silently swallow it).
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            log.warn("Sign-out requested with no refresh token; nothing to revoke");
            return;
        }

        String hashedToken = hmacToken(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(hashedToken);

        // BBUG-L2: no matching row -> logged no-op (token never issued, already deleted, or invalid).
        if (tokenOpt.isEmpty()) {
            log.warn("Sign-out: no refresh token matched the presented credential; nothing to revoke");
            return;
        }

        RefreshToken token = tokenOpt.get();

        // BBUG-L2: ownership check — a caller must not be able to revoke another user's token.
        UUID ownerId = token.getUser() != null ? token.getUser().getId() : null;
        if (callerId != null && ownerId != null && !callerId.equals(ownerId)) {
            log.warn("Sign-out: caller {} attempted to revoke a refresh token owned by user {}; ignored",
                    LogMaskingUtil.maskId(callerId), LogMaskingUtil.maskId(ownerId));
            return;
        }

        if (token.isRevoked()) {
            log.warn("Sign-out: refresh token for user {} was already revoked; nothing to do",
                    LogMaskingUtil.maskId(ownerId));
            return;
        }

        int revoked = refreshTokenRepository.revokeTokenByHash(hashedToken, ZonedDateTime.now(ARGENTINA_ZONE));
        if (revoked == 0) {
            log.warn("Sign-out: expected to revoke a refresh token for user {} but no row was updated",
                    LogMaskingUtil.maskId(ownerId));
        }
    }

    @Override
    @Transactional
    public SignInResultDTO refreshToken(String rawRefreshToken) {
        
        String hashedInputToken = hmacToken(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedInputToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }

        User user = refreshToken.getUser();
        String newAccessToken = generateAccessToken(user);

        String newRawToken = generateSecureToken();
        String newHashedToken = hmacToken(newRawToken);

        RefreshToken newRefreshToken = createRefreshToken(user, newHashedToken);
        refreshTokenRepository.save(newRefreshToken);
        
        refreshTokenRepository.revokeTokenByHash(hashedInputToken, ZonedDateTime.now(ARGENTINA_ZONE));

        // FSEC-H1 Stage 3: rotated raw refresh token travels via the cookie only.
        return new SignInResultDTO(authMapper.toSignInResponse(user, newAccessToken), newRawToken);
    }

    private RefreshToken createRefreshToken(User user, String hashedToken) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashedToken);
        refreshToken.setExpiresAt(ZonedDateTime.now(ARGENTINA_ZONE).plusDays(30));
        refreshToken.setCreatedAt(ZonedDateTime.now(ARGENTINA_ZONE));
        return refreshToken;
    }

    private String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String generateAccessToken(User user) {
        return jwtService.generateToken(user);
    }

    private void validateCommonFields(RegisterRequestDTO request) {
        if (request.birthdate() == null) {
            throw new IllegalArgumentException("Birthdate is required");
        }
        
        if (request.gender() == null || request.gender().trim().isEmpty()) {
            throw new IllegalArgumentException("Gender is required");
        }
        
        if (request.phone() == null || request.phone().trim().isEmpty()) {
            throw new IllegalArgumentException("Phone is required");
        }
                
        if (request.dni() != null) {
            String dniStr = request.dni().toString();
            if (!dniStr.matches("^[0-9]{7,8}$")) {
                throw new IllegalArgumentException("Invalid DNI format (7-8 digits)");
            }
            if (request.dni() < 1000000L || request.dni() > 999999999L) {
                throw new IllegalArgumentException("DNI out of valid range");
            }
        }
                
        if (request.birthdate().isAfter(LocalDate.now(ARGENTINA_ZONE).minusYears(18))) {
            throw new IllegalArgumentException("Must be at least 18 years old");
        }
        
        if (request.birthdate().isBefore(LocalDate.now(ARGENTINA_ZONE).minusYears(120))) {
            throw new IllegalArgumentException("Invalid birth date");
        }
    }

    private void validateDoctorFields(RegisterRequestDTO request) {
        if (request.medicalLicense() == null || request.medicalLicense().trim().isEmpty()) {
            throw new IllegalArgumentException("Medical license is required for doctors");
        }
        
        if (request.specialty() == null || request.specialty().trim().isEmpty()) {
            throw new IllegalArgumentException("Specialty is required for doctors");
        }
        
        if (!VALID_SPECIALTIES.contains(request.specialty().trim())) {
            throw new IllegalArgumentException("Invalid specialty selected");
        }
        
        if (request.slotDurationMin() == null) {
            throw new IllegalArgumentException("Slot duration is required for doctors");
        }
                
        if (!request.medicalLicense().matches("^[0-9]{4,10}$")) {
            throw new IllegalArgumentException("Medical license must be 4-10 digits");
        }
                
        if (request.slotDurationMin() < 5 || request.slotDurationMin() > 180) {
            throw new IllegalArgumentException("Slot duration must be between 5 and 180 minutes");
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Error initializing SHA-256", e);
        }
    }

    /**
     * BSEC-L-2: keyed HMAC-SHA256 of a refresh token. Unlike a bare SHA-256, an attacker who
     * leaks the token_hash column cannot precompute/reverse tokens without the server-side
     * key. The raw and hashed tokens are never logged. Deterministic for a given (key, token).
     */
    private String hmacToken(String token) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(refreshTokenHmacKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new RuntimeException("Error initializing HMAC for refresh token", e);
        }
    }

    private String createVerificationForUser(User user) {
        String rawToken = generateSecureToken();
        String hashedToken = hashToken(rawToken);

        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setCodeHash(hashedToken);
        verification.setExpiresAt(ZonedDateTime.now(ARGENTINA_ZONE).plusHours(72));

        emailVerificationRepository.save(verification);

        return rawToken;
    }
}
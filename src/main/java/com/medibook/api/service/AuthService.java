package com.medibook.api.service;

import com.medibook.api.dto.Auth.RegisterRequestDTO;
import com.medibook.api.dto.Auth.RegisterResponseDTO;
import com.medibook.api.dto.Auth.SignInRequestDTO;
import com.medibook.api.dto.Auth.SignInResultDTO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public interface AuthService {
    RegisterResponseDTO registerPatient(RegisterRequestDTO request);
    RegisterResponseDTO registerDoctor(RegisterRequestDTO request);
    RegisterResponseDTO registerAdmin(RegisterRequestDTO request);
    
    void verifyAccount(String token);
    SignInResultDTO signIn(SignInRequestDTO request);

    /**
     * BBUG-L2: revokes the given refresh token. {@code callerId} is the id of the
     * authenticated user performing the sign-out (or {@code null} when the request carries
     * no access token). When present, the token is only revoked if it belongs to that
     * caller — a caller cannot revoke another user's token.
     */
    void signOut(String refreshToken, java.util.UUID callerId);
    SignInResultDTO refreshToken(String refreshToken);
}
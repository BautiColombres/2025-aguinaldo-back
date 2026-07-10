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
    void signOut(String refreshToken);
    SignInResultDTO refreshToken(String refreshToken);
}
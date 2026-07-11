package com.medibook.api.service;

import com.medibook.api.dto.ProfileResponseDTO;
import com.medibook.api.dto.ProfileUpdateRequestDTO;
import com.medibook.api.entity.User;
import com.medibook.api.repository.UserRepository;
import com.medibook.api.repository.RefreshTokenRepository;
import com.medibook.api.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository userRepository;
    private final ProfileMapper profileMapper;
    private final RefreshTokenRepository refreshTokenRepository;
    private static final ZoneId ARGENTINA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    public ProfileResponseDTO getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return profileMapper.toProfileResponse(user);
    }

    @Transactional
    public ProfileResponseDTO updateProfile(UUID userId, ProfileUpdateRequestDTO updateRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (updateRequest.email() != null && !updateRequest.email().isBlank() &&
            !user.getEmail().equals(updateRequest.email())) {
            // Email actually changed: block only on a real collision with another user.
            if (userRepository.existsByEmail(updateRequest.email())) {
                throw new IllegalArgumentException("Email already in use");
            }
        }

        profileMapper.updateUserFromRequest(user, updateRequest);
        user = userRepository.save(user);
        
        return profileMapper.toProfileResponse(user);
    }

    @Transactional
    public void deactivateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        user.setStatus("DISABLED");
        userRepository.save(user);
        
        refreshTokenRepository.revokeAllTokensByUserId(userId, ZonedDateTime.now(ARGENTINA_ZONE));
    }
}

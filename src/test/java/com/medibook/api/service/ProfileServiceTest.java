package com.medibook.api.service;

import com.medibook.api.dto.ProfileResponseDTO;
import com.medibook.api.dto.ProfileUpdateRequestDTO;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.ProfileMapper;
import com.medibook.api.repository.RefreshTokenRepository;
import com.medibook.api.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileMapper profileMapper;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private ProfileService profileService;

    private UUID userId;
    private User testUser;
    private ProfileResponseDTO profileResponseDTO;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setStatus("ACTIVE");
        testUser.setRole("PATIENT");

        profileResponseDTO = new ProfileResponseDTO();
        profileResponseDTO.setId(userId);
        profileResponseDTO.setEmail("test@example.com");
        profileResponseDTO.setName("Test User");
        profileResponseDTO.setRole("PATIENT");
        profileResponseDTO.setStatus("ACTIVE");
    }

    private ProfileUpdateRequestDTO createUpdateRequest(String email, String name, String surname) {
        return new ProfileUpdateRequestDTO(
                email,
                name,
                surname,
                null, // phone
                null, // birthdate
                null, // gender
                null, // medicalLicense
                null, // specialty
                null  // slotDurationMin
        );
    }

    @Test
    void getProfile_WithValidUserId_ShouldReturnProfileResponse() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(profileMapper.toProfileResponse(testUser)).thenReturn(profileResponseDTO);

        // When
        ProfileResponseDTO result = profileService.getProfile(userId);

        // Then
        assertNotNull(result);
        assertEquals(profileResponseDTO, result);
        verify(userRepository).findById(userId);
        verify(profileMapper).toProfileResponse(testUser);
    }

    @Test
    void getProfile_WithInvalidUserId_ShouldThrowRuntimeException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            profileService.getProfile(userId)
        );
        
        assertEquals("Usuario no encontrado", exception.getMessage());
        verify(userRepository).findById(userId);
        verifyNoInteractions(profileMapper);
    }

    @Test
    void updateProfile_WithValidRequest_ShouldReturnUpdatedProfile() {
        // Given
        ProfileUpdateRequestDTO updateRequest = createUpdateRequest("test@example.com", "Updated Name", "Updated Surname");

        User updatedUser = new User();
        updatedUser.setId(userId);
        updatedUser.setEmail("test@example.com");
        updatedUser.setName("Updated Name");

        ProfileResponseDTO updatedResponse = new ProfileResponseDTO();
        updatedResponse.setId(userId);
        updatedResponse.setEmail("test@example.com");
        updatedResponse.setName("Updated Name");
        updatedResponse.setRole("PATIENT");
        updatedResponse.setStatus("ACTIVE");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        doNothing().when(profileMapper).updateUserFromRequest(testUser, updateRequest);
        when(profileMapper.toProfileResponse(updatedUser)).thenReturn(updatedResponse);

        // When
        ProfileResponseDTO result = profileService.updateProfile(userId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(updatedResponse, result);
        verify(userRepository).findById(userId);
        verify(profileMapper).updateUserFromRequest(testUser, updateRequest);
        verify(userRepository).save(testUser);
        verify(profileMapper).toProfileResponse(updatedUser);
    }

    @Test
    void updateProfile_WithInvalidUserId_ShouldThrowRuntimeException() {
        // Given
        ProfileUpdateRequestDTO updateRequest = createUpdateRequest("test@example.com", "Updated Name", "Updated Surname");
        
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            profileService.updateProfile(userId, updateRequest)
        );
        
        assertEquals("Usuario no encontrado", exception.getMessage());
        verify(userRepository).findById(userId);
        verifyNoInteractions(profileMapper);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_WithNewUnusedEmail_ShouldUpdateSuccessfully() {
        // Given: email actually changed to one that no other user has
        ProfileUpdateRequestDTO updateRequest = createUpdateRequest("new@example.com", "Updated Name", "Updated Surname");

        User updatedUser = new User();
        updatedUser.setId(userId);
        updatedUser.setEmail("new@example.com");
        updatedUser.setName("Updated Name");

        ProfileResponseDTO updatedResponse = new ProfileResponseDTO();
        updatedResponse.setId(userId);
        updatedResponse.setEmail("new@example.com");
        updatedResponse.setName("Updated Name");
        updatedResponse.setRole("PATIENT");
        updatedResponse.setStatus("ACTIVE");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        doNothing().when(profileMapper).updateUserFromRequest(testUser, updateRequest);
        when(profileMapper.toProfileResponse(updatedUser)).thenReturn(updatedResponse);

        // When
        ProfileResponseDTO result = profileService.updateProfile(userId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(updatedResponse, result);
        verify(userRepository).existsByEmail("new@example.com");
        verify(profileMapper).updateUserFromRequest(testUser, updateRequest);
        verify(userRepository).save(testUser);
    }

    @Test
    void updateProfile_WithEmailUsedByAnotherUser_ShouldThrowIllegalArgumentException() {
        // Given: email actually changed to one already taken by another user
        ProfileUpdateRequestDTO updateRequest = createUpdateRequest("taken@example.com", "Updated Name", "Updated Surname");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            profileService.updateProfile(userId, updateRequest)
        );

        assertEquals("Email already in use", exception.getMessage());
        verify(userRepository).findById(userId);
        verify(userRepository).existsByEmail("taken@example.com");
        verifyNoInteractions(profileMapper);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_WithSameEmail_ShouldNotCheckUniquenessAndSucceed() {
        // Given: email unchanged -> must NOT be falsely rejected and must NOT query uniqueness
        ProfileUpdateRequestDTO updateRequest = createUpdateRequest("test@example.com", "Updated Name", "Updated Surname");

        User updatedUser = new User();
        updatedUser.setId(userId);
        updatedUser.setEmail("test@example.com");
        updatedUser.setName("Updated Name");

        ProfileResponseDTO updatedResponse = new ProfileResponseDTO();
        updatedResponse.setId(userId);
        updatedResponse.setEmail("test@example.com");
        updatedResponse.setName("Updated Name");
        updatedResponse.setRole("PATIENT");
        updatedResponse.setStatus("ACTIVE");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        doNothing().when(profileMapper).updateUserFromRequest(testUser, updateRequest);
        when(profileMapper.toProfileResponse(updatedUser)).thenReturn(updatedResponse);

        // When
        ProfileResponseDTO result = profileService.updateProfile(userId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(updatedResponse, result);
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository).save(testUser);
    }

    @Test
    void updateProfile_WithNullEmail_ShouldUpdateSuccessfully() {
        // Given
        ProfileUpdateRequestDTO updateRequest = createUpdateRequest(null, "Updated Name", "Updated Surname");

        User updatedUser = new User();
        updatedUser.setId(userId);
        updatedUser.setEmail("test@example.com");
        updatedUser.setName("Updated Name");

        ProfileResponseDTO updatedResponse = new ProfileResponseDTO();
        updatedResponse.setId(userId);
        updatedResponse.setEmail("test@example.com");
        updatedResponse.setName("Updated Name");
        updatedResponse.setRole("PATIENT");
        updatedResponse.setStatus("ACTIVE");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        doNothing().when(profileMapper).updateUserFromRequest(testUser, updateRequest);
        when(profileMapper.toProfileResponse(updatedUser)).thenReturn(updatedResponse);

        // When
        ProfileResponseDTO result = profileService.updateProfile(userId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(updatedResponse, result);
        verify(userRepository).findById(userId);
        verify(profileMapper).updateUserFromRequest(testUser, updateRequest);
        verify(userRepository).save(testUser);
        verify(profileMapper).toProfileResponse(updatedUser);
    }

    @Test
    void updateProfile_WithBlankEmail_ShouldUpdateSuccessfully() {
        // Given
        ProfileUpdateRequestDTO updateRequest = createUpdateRequest("   ", "Updated Name", "Updated Surname");

        User updatedUser = new User();
        updatedUser.setId(userId);
        updatedUser.setEmail("test@example.com");
        updatedUser.setName("Updated Name");

        ProfileResponseDTO updatedResponse = new ProfileResponseDTO();
        updatedResponse.setId(userId);
        updatedResponse.setEmail("test@example.com");
        updatedResponse.setName("Updated Name");
        updatedResponse.setRole("PATIENT");
        updatedResponse.setStatus("ACTIVE");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        doNothing().when(profileMapper).updateUserFromRequest(testUser, updateRequest);
        when(profileMapper.toProfileResponse(updatedUser)).thenReturn(updatedResponse);

        // When
        ProfileResponseDTO result = profileService.updateProfile(userId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(updatedResponse, result);
        verify(userRepository).findById(userId);
        verify(profileMapper).updateUserFromRequest(testUser, updateRequest);
        verify(userRepository).save(testUser);
        verify(profileMapper).toProfileResponse(updatedUser);
    }
}
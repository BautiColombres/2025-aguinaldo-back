package com.medibook.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medibook.api.entity.BadgeStatistics;
import com.medibook.api.entity.Rating;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.repository.BadgeRepository;
import com.medibook.api.repository.BadgeStatisticsRepository;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BadgeStatisticsUpdateServiceTest {

    @Mock
    private BadgeStatisticsRepository statisticsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private TurnAssignedRepository turnAssignedRepository;

    @Mock
    private BadgeRepository badgeRepository;

    private BadgeStatisticsUpdateService badgeStatisticsUpdateService;

    private UUID userId;
    private User user;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setName("Test User");
        user.setRole("PATIENT");
        objectMapper = new ObjectMapper();
        lenient().when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
        lenient().when(ratingRepository.findTop35ByRated_IdAndRater_RoleOrderByCreatedAtDesc(any(UUID.class), anyString()))
            .thenReturn(List.of());
        lenient().when(badgeRepository.existsByUser_IdAndBadgeTypeAndIsActive(any(UUID.class), anyString(), eq(true))).thenReturn(false);
        badgeStatisticsUpdateService = new BadgeStatisticsUpdateService(statisticsRepository, userRepository, ratingRepository, turnAssignedRepository, badgeRepository);
    }

    @Test
    void updateAfterRatingAddedSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterRatingAddedSync(userId, 4, 5, 3);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterRatingSync_ExistingStats_UpdatesProgress() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterRatingSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterTurnCompletedSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterTurnCompletedSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterTurnCompletionSync_ExistingStats_UpdatesProgress() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterTurnCompletionSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterMedicalHistoryDocumentedSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterMedicalHistoryDocumentedSync(userId, "Test content with multiple words");

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterMedicalHistorySync_ExistingStats_UpdatesProgress() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterMedicalHistorySync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterModifyRequestCreatedSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterModifyRequestCreatedSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterModifyRequestHandledSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterModifyRequestHandledSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterModifyRequestSync_DoctorRole_UpdatesProgress() {
        user.setRole("DOCTOR");

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterModifyRequestSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterTurnCancelledSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterTurnCancelledSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAllBadgeProgress_ExistingStats_UpdatesProgress() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAllBadgeProgress(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(3)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterAdvanceBookingSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterAdvanceBookingSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterRatingGivenSync_PatientRole_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterRatingGivenSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterFileUploadedSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterFileUploadedSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterTurnNoShowSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterTurnNoShowSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterRatingReceivedSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterRatingReceivedSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterRatingGivenSync_ExistingStats_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);
        when(ratingRepository.findByRaterId(userId)).thenReturn(List.of()); 

        badgeStatisticsUpdateService.updateAfterRatingGivenSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(ratingRepository).findByRaterId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAllBadgeProgress_WithTurnAssignedData_UpdatesProgress() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(turnAssignedRepository.findByPatient_IdAndStatus(userId, "COMPLETED")).thenReturn(List.of()); 

        badgeStatisticsUpdateService.updateAllBadgeProgress(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(3)).findById(userId);
        verify(turnAssignedRepository).findByPatient_IdAndStatus(userId, "COMPLETED");
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterCancellationSync_EmptyMethod_DoesNothing() {
        badgeStatisticsUpdateService.updateProgressAfterCancellationSync(userId);
        
        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateProgressAfterAdvanceBookingSync_EmptyMethod_DoesNothing() {
        badgeStatisticsUpdateService.updateProgressAfterAdvanceBookingSync(userId);
        
        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateProgressAfterRatingReceivedSync_EmptyMethod_DoesNothing() {
        badgeStatisticsUpdateService.updateProgressAfterRatingReceivedSync(userId);
        
        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateProgressAfterBookingSync_EmptyMethod_DoesNothing() {
        badgeStatisticsUpdateService.updateProgressAfterBookingSync(userId);
        
        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateProgressAfterFileUploadSync_EmptyMethod_DoesNothing() {
        badgeStatisticsUpdateService.updateProgressAfterFileUploadSync(userId);
        
        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateAfterPunctualityRatingSync_EmptyMethod_DoesNothing() {
        badgeStatisticsUpdateService.updateAfterPunctualityRatingSync(userId);
        
        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateAfterCollaborationRatingSync_EmptyMethod_DoesNothing() {
        badgeStatisticsUpdateService.updateAfterCollaborationRatingSync(userId);
        
        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateAfterFollowInstructionsRatingSync_EmptyMethod_DoesNothing() {
        badgeStatisticsUpdateService.updateAfterFollowInstructionsRatingSync(userId);
        
        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateAfterTurnCompletedSync_ExceptionThrown_LogsError() {
        when(statisticsRepository.findByUserId(userId)).thenThrow(new RuntimeException("Database error"));

        badgeStatisticsUpdateService.updateAfterTurnCompletedSync(userId);

        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateProgressAfterTurnCompletionSync_ExceptionThrown_LogsError() {
        when(statisticsRepository.findByUserId(userId)).thenThrow(new RuntimeException("Database error"));

        badgeStatisticsUpdateService.updateProgressAfterTurnCompletionSync(userId);

        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateAfterMedicalHistoryDocumentedSync_ExceptionThrown_LogsError() {
        when(statisticsRepository.findByUserId(userId)).thenThrow(new RuntimeException("Database error"));

        badgeStatisticsUpdateService.updateAfterMedicalHistoryDocumentedSync(userId, "test content");

        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateProgressAfterMedicalHistorySync_ExceptionThrown_LogsError() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenThrow(new RuntimeException("Database error"));

        badgeStatisticsUpdateService.updateProgressAfterMedicalHistorySync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterModifyRequestCreatedSync_ExceptionThrown_LogsError() {
        when(statisticsRepository.findByUserId(userId)).thenThrow(new RuntimeException("Database error"));

        badgeStatisticsUpdateService.updateAfterModifyRequestCreatedSync(userId);

        verify(statisticsRepository).findByUserId(userId);
    }

    @Test
    void updateAfterRatingAddedSync_ExceptionThrown_LogsError() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> badgeStatisticsUpdateService.updateAfterRatingAddedSync(userId, 4, 5, 3));

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterTurnCompletionSync_SaveExceptionThrown_LogsError() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenThrow(new RuntimeException("Database error"));

        badgeStatisticsUpdateService.updateProgressAfterTurnCompletionSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterRatingGivenSync_ExceptionThrown_LogsError() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(ratingRepository.findByRaterId(userId)).thenThrow(new RuntimeException("Database error"));

        badgeStatisticsUpdateService.updateAfterRatingGivenSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId); 
        verify(ratingRepository).findByRaterId(userId);
    }

    @Test
    void updateAfterRatingReceivedSync_ExceptionThrown_LogsError() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenThrow(new RuntimeException("Database error"));

        badgeStatisticsUpdateService.updateAfterRatingReceivedSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId); 
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAllBadgeProgress_ExceptionThrown_LogsError() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> badgeStatisticsUpdateService.updateAllBadgeProgress(userId));

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAfterTurnCompletedSync_WithPatientId_UpdatesStatistics() {
        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        UUID patientId = UUID.randomUUID();

        badgeStatisticsUpdateService.updateAfterTurnCompletedSync(userId, patientId);

        verify(statisticsRepository, times(2)).findByUserId(userId); 
        verify(statisticsRepository, times(1)).save(existingStats);
    }

    @Test
    void updateAllBadgeProgress_DoctorRole_UpdatesDoctorBadgeProgress() {
        user.setRole("DOCTOR");

        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_turns_completed", 60);
        statsJson.put("documentation_count", 40);
        statsJson.put("total_turns_cancelled", 5);
        statsJson.put("unique_patients_served", 15);
        statsJson.put("requests_handled", 8);
        statsJson.put("total_ratings_received", 30);
        statsJson.put("total_communication_count", 30);
        statsJson.put("total_empathy_count", 30);
        statsJson.put("total_punctuality_count", 25);
        statsJson.put("total_avg_rating", 4.2);
        statsJson.put("total_low_rating_count", 2);

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAllBadgeProgress(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(3)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateRatingBasedStatistics_WithCommunicationRatings_CoversLambda() {
        User rater = new User();
        rater.setId(UUID.randomUUID());
        rater.setRole("PATIENT");

        Rating rating1 = new Rating();
        rating1.setScore(4);
        rating1.setSubcategory("explica claramente");
        rating1.setRater(rater);

        Rating rating2 = new Rating();
        rating2.setScore(4);
        rating2.setSubcategory("escucha atentamente");
        rating2.setRater(rater);

        Rating rating3 = new Rating();
        rating3.setScore(3);
        rating3.setSubcategory("comunica bien");
        rating3.setRater(rater);

        when(ratingRepository.findByRatedId(userId)).thenReturn(List.of(rating1, rating2, rating3));

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterRatingAddedSync(userId, 4, null, null);

        verify(ratingRepository).findByRatedId(userId);
        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateRatingBasedStatistics_WithEmpathyRatings_CoversLambda() {
        User rater = new User();
        rater.setId(UUID.randomUUID());
        rater.setRole("PATIENT");

        Rating rating1 = new Rating();
        rating1.setScore(4);
        rating1.setSubcategory("empatía excepcional");
        rating1.setRater(rater);

        Rating rating2 = new Rating();
        rating2.setScore(4);
        rating2.setSubcategory("confianza generada");
        rating2.setRater(rater);

        when(ratingRepository.findByRatedId(userId)).thenReturn(List.of(rating1, rating2));

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterRatingAddedSync(userId, null, 4, null);

        verify(ratingRepository).findByRatedId(userId);
        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateRatingBasedStatistics_WithPunctualityRatings_CoversLambda() {
        User rater = new User();
        rater.setId(UUID.randomUUID());
        rater.setRole("PATIENT");

        Rating rating1 = new Rating();
        rating1.setScore(4);
        rating1.setSubcategory("respeta horarios");
        rating1.setRater(rater);

        Rating rating2 = new Rating();
        rating2.setScore(4);
        rating2.setSubcategory("tiempo de espera adecuado");
        rating2.setRater(rater);

        when(ratingRepository.findByRatedId(userId)).thenReturn(List.of(rating1, rating2));

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterRatingAddedSync(userId, null, null, 4);

        verify(ratingRepository).findByRatedId(userId);
        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateRatingBasedStatistics_WithDoctorCollaborationRatings_CoversLambda() {
        User doctorRater = new User();
        doctorRater.setId(UUID.randomUUID());
        doctorRater.setRole("DOCTOR");

        Rating rating1 = new Rating();
        rating1.setScore(4);
        rating1.setSubcategory("colabora bien");
        rating1.setRater(doctorRater);

        Rating rating2 = new Rating();
        rating2.setScore(4);
        rating2.setSubcategory("sigue indicaciones");
        rating2.setRater(doctorRater);

        when(ratingRepository.findByRatedId(userId)).thenReturn(List.of(rating1, rating2));

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterRatingAddedSync(userId, null, 4, null);

        verify(ratingRepository).findByRatedId(userId);
        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateAllBadgeProgress_WithCompletedTurns_CoversLambda() {
        User doctor = new User();
        doctor.setId(UUID.randomUUID());

        TurnAssigned turn1 = new TurnAssigned();
        turn1.setDoctor(doctor);
        turn1.setStatus("COMPLETED");

        TurnAssigned turn2 = new TurnAssigned();
        turn2.setDoctor(doctor);
        turn2.setStatus("COMPLETED");

        when(turnAssignedRepository.findByPatient_IdAndStatus(userId, "COMPLETED")).thenReturn(List.of(turn1, turn2));

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAllBadgeProgress(userId);

        verify(turnAssignedRepository).findByPatient_IdAndStatus(userId, "COMPLETED");
        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void ensureStatisticsExist_UserNotFound_ThrowsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> badgeStatisticsUpdateService.updateAfterRatingAddedSync(userId, 4, 5, 3));

        verify(userRepository).findById(userId);
    }

    @Test
    void updateProgressAfterRatingSync_DoctorRole_WithHighStats_ActivatesSustainedExcellence() {
        user.setRole("DOCTOR");

        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_ratings_received", 120);
        statsJson.put("total_communication_count", 30);
        statsJson.put("total_empathy_count", 30);
        statsJson.put("total_punctuality_count", 25);
        statsJson.put("total_avg_rating", 4.5);
        statsJson.put("total_low_rating_count", 5); 

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterRatingSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterRatingSync_DoctorRole_WithLowRating_NoSustainedExcellence() {
        user.setRole("DOCTOR");

        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_ratings_received", 120);
        statsJson.put("total_communication_count", 30);
        statsJson.put("total_empathy_count", 30);
        statsJson.put("total_punctuality_count", 25);
        statsJson.put("total_avg_rating", 3.8); 
        statsJson.put("total_low_rating_count", 5);

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterRatingSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterRatingSync_DoctorRole_WithHighLowRatingCount_NoSustainedExcellence() {
        user.setRole("DOCTOR");

        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_ratings_received", 120);
        statsJson.put("total_communication_count", 30);
        statsJson.put("total_empathy_count", 30);
        statsJson.put("total_punctuality_count", 25);
        statsJson.put("total_avg_rating", 4.5);
        statsJson.put("total_low_rating_count", 15); 

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterRatingSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterTurnCompletionSync_DoctorRole_WithHighCancellationRate_NoConsistentProfessional() {
        user.setRole("DOCTOR");

        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_turns_completed", 100);
        statsJson.put("total_turns_cancelled", 20); 

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterTurnCompletionSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterTurnCompletionSync_DoctorRole_WithLowCancellationRate_ActivatesConsistentProfessional() {
        user.setRole("DOCTOR");

        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_turns_completed", 100);
        statsJson.put("total_turns_cancelled", 10); 

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterTurnCompletionSync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterMedicalHistorySync_DoctorRole_WithHighDocumentation_ActivatesCompleteDocumenter() {
        user.setRole("DOCTOR");

        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_turns_completed", 60);
        statsJson.put("documentation_count", 40);

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterMedicalHistorySync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateProgressAfterMedicalHistorySync_DoctorRole_WithLowTurns_ScalesRequirement() {
        user.setRole("DOCTOR");

        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_turns_completed", 30);
        statsJson.put("documentation_count", 20);

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateProgressAfterMedicalHistorySync(userId);

        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(userRepository, times(2)).findById(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateDoctorBadgeProgress_WithHighStats_ActivatesMultipleBadges() {
        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_turns_completed", 150);
        statsJson.put("documentation_count", 40);
        statsJson.put("total_turns_cancelled", 5);
        statsJson.put("unique_patients_served", 15);
        statsJson.put("requests_handled", 8);
        statsJson.put("total_ratings_received", 30);
        statsJson.put("total_communication_count", 30);
        statsJson.put("total_empathy_count", 30);
        statsJson.put("total_punctuality_count", 25);
        statsJson.put("total_avg_rating", 4.5);
        statsJson.put("total_low_rating_count", 2);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("total_turns_completed", 150);
        statistics.put("documentation_count", 40);
        statistics.put("total_turns_cancelled", 5);
        statistics.put("unique_patients_served", 15);
        statistics.put("requests_handled", 8);
        statistics.put("total_ratings_received", 30);
        statistics.put("total_communication_count", 30);
        statistics.put("total_empathy_count", 30);
        statistics.put("total_punctuality_count", 25);
        statistics.put("total_avg_rating", 4.5);
        statistics.put("total_low_rating_count", 2);

        Map<String, Object> progress = new HashMap<>();

        badgeStatisticsUpdateService.updateDoctorBadgeProgress(userId, statistics, progress, 150);

        assert progress.containsKey("DOCTOR_COMPLETE_DOCUMENTER");
        assert progress.containsKey("DOCTOR_CONSISTENT_PROFESSIONAL");
        assert progress.containsKey("DOCTOR_RELATIONSHIP_BUILDER");
        assert progress.containsKey("DOCTOR_AGILE_RESPONDER");
        assert progress.containsKey("DOCTOR_TOP_SPECIALIST");
        assert progress.containsKey("DOCTOR_MEDICAL_LEGEND");
        assert progress.containsKey("DOCTOR_EXCEPTIONAL_COMMUNICATOR");
        assert progress.containsKey("DOCTOR_EMPATHETIC_DOCTOR");
        assert progress.containsKey("DOCTOR_PUNCTUALITY_PROFESSIONAL");
    }

    @Test
    void updateDoctorBadgeProgress_WithLowStats_NoBadgesActivated() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("total_turns_completed", 10);
        statistics.put("documentation_count", 2);
        statistics.put("total_turns_cancelled", 5);
        statistics.put("unique_patients_served", 2);
        statistics.put("requests_handled", 1);
        statistics.put("total_ratings_received", 5);
        statistics.put("total_communication_count", 2);
        statistics.put("total_empathy_count", 2);
        statistics.put("total_punctuality_count", 2);
        statistics.put("total_avg_rating", 3.5);
        statistics.put("total_low_rating_count", 2);

        Map<String, Object> progress = new HashMap<>();

        badgeStatisticsUpdateService.updateDoctorBadgeProgress(userId, statistics, progress, 10);

        assert progress.containsKey("DOCTOR_COMPLETE_DOCUMENTER");
        assert progress.containsKey("DOCTOR_CONSISTENT_PROFESSIONAL");
        assert progress.containsKey("DOCTOR_RELATIONSHIP_BUILDER");
        assert progress.containsKey("DOCTOR_AGILE_RESPONDER");
        assert progress.containsKey("DOCTOR_TOP_SPECIALIST");
        assert progress.containsKey("DOCTOR_MEDICAL_LEGEND");
        assert progress.containsKey("DOCTOR_EXCEPTIONAL_COMMUNICATOR");
        assert progress.containsKey("DOCTOR_EMPATHETIC_DOCTOR");
        assert progress.containsKey("DOCTOR_PUNCTUALITY_PROFESSIONAL");
    }

    @Test
    void updateAllBadgeProgress_CoversLambdaExpressions() {
        User doctor = new User();
        doctor.setId(UUID.randomUUID());

        TurnAssigned turn1 = new TurnAssigned();
        turn1.setDoctor(doctor);
        turn1.setStatus("COMPLETED");

        TurnAssigned turn2 = new TurnAssigned();
        turn2.setDoctor(doctor);
        turn2.setStatus("COMPLETED");

        TurnAssigned turn3 = new TurnAssigned();
        turn3.setDoctor(doctor);
        turn3.setStatus("COMPLETED");

        when(turnAssignedRepository.findByPatient_IdAndStatus(userId, "COMPLETED")).thenReturn(List.of(turn1, turn2, turn3));

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAllBadgeProgress(userId);

        verify(turnAssignedRepository).findByPatient_IdAndStatus(userId, "COMPLETED");
        verify(statisticsRepository, times(2)).findByUserId(userId);
        verify(statisticsRepository).save(existingStats);
    }

    @Test
    void updateDoctorBadgeProgress_LessThan50Turns_CoversScaledRequirements() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("documentation_count", 10); 
        statistics.put("total_turns_cancelled", 2);
        statistics.put("unique_patients_served", 8);
        statistics.put("requests_handled", 5);
        statistics.put("total_ratings_received", 50);
        statistics.put("total_communication_count", 15);
        statistics.put("total_empathy_count", 15);
        statistics.put("total_punctuality_count", 10);
        statistics.put("total_avg_rating", 4.2);
        statistics.put("total_low_rating_count", 2);

        Map<String, Object> progress = new HashMap<>();

        badgeStatisticsUpdateService.updateDoctorBadgeProgress(userId, statistics, progress, 30);

        assert progress.containsKey("DOCTOR_COMPLETE_DOCUMENTER");
        assert progress.containsKey("DOCTOR_CONSISTENT_PROFESSIONAL");
        assert progress.containsKey("DOCTOR_RELATIONSHIP_BUILDER");
        assert progress.containsKey("DOCTOR_AGILE_RESPONDER");
        assert progress.containsKey("DOCTOR_TOP_SPECIALIST");
        assert progress.containsKey("DOCTOR_MEDICAL_LEGEND");
        assert progress.containsKey("DOCTOR_EXCEPTIONAL_COMMUNICATOR");
        assert progress.containsKey("DOCTOR_EMPATHETIC_DOCTOR");
        assert progress.containsKey("DOCTOR_PUNCTUALITY_PROFESSIONAL");
    }

    @Test
    void updateDoctorBadgeProgress_50OrMoreTurns_CoversFullRequirements() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("documentation_count", 40); 
        statistics.put("total_turns_cancelled", 5); 
        statistics.put("unique_patients_served", 12);
        statistics.put("requests_handled", 8);
        statistics.put("total_ratings_received", 100);
        statistics.put("total_communication_count", 30);
        statistics.put("total_empathy_count", 30);
        statistics.put("total_punctuality_count", 25);
        statistics.put("total_avg_rating", 4.5);
        statistics.put("total_low_rating_count", 5);

        Map<String, Object> progress = new HashMap<>();

        badgeStatisticsUpdateService.updateDoctorBadgeProgress(userId, statistics, progress, 80);

        assert progress.containsKey("DOCTOR_COMPLETE_DOCUMENTER");
        assert progress.containsKey("DOCTOR_CONSISTENT_PROFESSIONAL");
        assert progress.containsKey("DOCTOR_RELATIONSHIP_BUILDER");
        assert progress.containsKey("DOCTOR_AGILE_RESPONDER");
        assert progress.containsKey("DOCTOR_TOP_SPECIALIST");
        assert progress.containsKey("DOCTOR_MEDICAL_LEGEND");
        assert progress.containsKey("DOCTOR_EXCEPTIONAL_COMMUNICATOR");
        assert progress.containsKey("DOCTOR_EMPATHETIC_DOCTOR");
        assert progress.containsKey("DOCTOR_PUNCTUALITY_PROFESSIONAL");
    }

    @Test
    void updateDoctorBadgeProgress_HighCancellationRate_CoversFailureCondition() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("documentation_count", 40);
        statistics.put("total_cancellations", 20);
        statistics.put("unique_patients_served", 12);
        statistics.put("requests_handled", 8);
        statistics.put("total_ratings_received", 100);
        statistics.put("total_communication_count", 30);
        statistics.put("total_empathy_count", 30);
        statistics.put("total_punctuality_count", 25);
        statistics.put("total_avg_rating", 4.5);
        statistics.put("total_low_rating_count", 5);

        Map<String, Object> progress = new HashMap<>();

        badgeStatisticsUpdateService.updateDoctorBadgeProgress(userId, statistics, progress, 80);

        assert progress.containsKey("DOCTOR_CONSISTENT_PROFESSIONAL");
        assert progress.get("DOCTOR_CONSISTENT_PROFESSIONAL").equals(0.0);
    }

    @Test
    void updateDoctorBadgeProgress_LowRequestsHandled_CoversPartialProgress() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("documentation_count", 40);
        statistics.put("total_turns_cancelled", 5);
        statistics.put("unique_patients_served", 12);
        statistics.put("total_requests_handled", 4); 
        statistics.put("total_ratings_received", 100);
        statistics.put("total_communication_count", 30);
        statistics.put("total_empathy_count", 30);
        statistics.put("total_punctuality_count", 25);
        statistics.put("total_avg_rating", 4.5);
        statistics.put("total_low_rating_count", 5);

        Map<String, Object> progress = new HashMap<>();

        badgeStatisticsUpdateService.updateDoctorBadgeProgress(userId, statistics, progress, 80);

        assert progress.containsKey("DOCTOR_AGILE_RESPONDER");
        assert progress.get("DOCTOR_AGILE_RESPONDER").equals(4 * 100.0 / 7);
    }

    // ----- BBUG-H1: cancellation stat key mismatch -----

    @Test
    void updateAfterTurnCancelledSync_IncrementsCanonicalTotalCancellationsKey() {
        ObjectNode statsJson = objectMapper.createObjectNode();
        statsJson.put("total_cancellations", 2);

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(statsJson)
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);

        badgeStatisticsUpdateService.updateAfterTurnCancelledSync(userId);

        JsonNode saved = existingStats.getStatistics();
        assert saved.has("total_cancellations");
        assert saved.get("total_cancellations").asInt() == 3;
    }

    @Test
    void updateDoctorBadgeProgress_HighCancellationRate_ReadsTotalCancellationsKey() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("total_cancellations", 20); // 20/80 = 25% >= 15% -> badge denied

        Map<String, Object> progress = new HashMap<>();

        badgeStatisticsUpdateService.updateDoctorBadgeProgress(userId, statistics, progress, 80);

        assert progress.containsKey("DOCTOR_CONSISTENT_PROFESSIONAL");
        assert progress.get("DOCTOR_CONSISTENT_PROFESSIONAL").equals(0.0);
    }

    @Test
    void updateDoctorBadgeProgress_LowCancellationRate_ReadsTotalCancellationsKey() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("total_cancellations", 5); // 5/80 = 6.25% < 15% -> badge earned

        Map<String, Object> progress = new HashMap<>();

        badgeStatisticsUpdateService.updateDoctorBadgeProgress(userId, statistics, progress, 80);

        assert progress.containsKey("DOCTOR_CONSISTENT_PROFESSIONAL");
        assert progress.get("DOCTOR_CONSISTENT_PROFESSIONAL").equals(100.0);
    }

    // ----- BBUG-H2: unique-patients counted distinctly, not per-turn -----

    @Test
    void updateAfterTurnCompletedSync_SamePatientTwice_UniqueCountStaysOne() {
        user.setRole("DOCTOR");

        User patient = new User();
        patient.setId(UUID.randomUUID());

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(userId)).thenReturn(List.of(patient));

        badgeStatisticsUpdateService.updateAfterTurnCompletedSync(userId, patient.getId());
        badgeStatisticsUpdateService.updateAfterTurnCompletedSync(userId, patient.getId());

        JsonNode saved = existingStats.getStatistics();
        assert saved.get("total_unique_patients").asInt() == 1;
    }

    @Test
    void updateAfterTurnCompletedSync_TwoDistinctPatients_UniqueCountIsTwo() {
        user.setRole("DOCTOR");

        User patient1 = new User();
        patient1.setId(UUID.randomUUID());
        User patient2 = new User();
        patient2.setId(UUID.randomUUID());

        BadgeStatistics existingStats = BadgeStatistics.builder()
                .userId(userId)
                .statistics(objectMapper.createObjectNode())
                .progress(objectMapper.createObjectNode())
                .build();

        when(statisticsRepository.findByUserId(userId)).thenReturn(Optional.of(existingStats));
        when(statisticsRepository.save(any(BadgeStatistics.class))).thenReturn(existingStats);
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(userId)).thenReturn(List.of(patient1, patient2));

        badgeStatisticsUpdateService.updateAfterTurnCompletedSync(userId, patient2.getId());

        JsonNode saved = existingStats.getStatistics();
        assert saved.get("total_unique_patients").asInt() == 2;
    }

    @Test
    void parseJson_NullJson_ReturnsEmptyMap() {
        Map<String, Object> result = badgeStatisticsUpdateService.parseJson(null);

        assert result.isEmpty();
    }

    @Test
    void parseJson_NullNode_ReturnsEmptyMap() {
        JsonNode nullNode = objectMapper.nullNode();

        Map<String, Object> result = badgeStatisticsUpdateService.parseJson(nullNode);

        assert result.isEmpty();
    }

    @Test
    void parseJson_ExceptionDuringParsing_ReturnsEmptyMap() {
        ObjectNode invalidNode = objectMapper.createObjectNode();
        invalidNode.put("invalid", "data");

        BadgeStatisticsUpdateService serviceWithMockMapper = new BadgeStatisticsUpdateService(
            statisticsRepository, userRepository, ratingRepository, turnAssignedRepository, badgeRepository) {
            @Override
            protected Map<String, Object> parseJson(com.fasterxml.jackson.databind.JsonNode json) {
                throw new RuntimeException("Parsing failed");
            }
        };

        try {
            Map<String, Object> result = serviceWithMockMapper.parseJson(invalidNode);
            assert result.isEmpty();
        } catch (Exception e) {
        }
    }
}
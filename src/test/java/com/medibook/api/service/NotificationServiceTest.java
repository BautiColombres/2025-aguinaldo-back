package com.medibook.api.service;

import com.medibook.api.entity.Notification;
import com.medibook.api.entity.NotificationType;
import com.medibook.api.entity.User;
import com.medibook.api.repository.NotificationRepository;
import com.medibook.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setName("Test");
        testUser.setSurname("User");
        testUser.setDni(12345678L);

        testNotification = new Notification();
        testNotification.setId(UUID.randomUUID());
        testNotification.setUser(testUser);
        testNotification.setType(NotificationType.TURN_CANCELLED);
        testNotification.setRelatedEntityId(UUID.randomUUID());
        testNotification.setMessage("Test notification");
        testNotification.setRead(false);
    }

    @Test
    void testCreateNotification_Success() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.createNotification(
                testUser.getId(), NotificationType.TURN_CANCELLED, testNotification.getRelatedEntityId(), "Test message");

        assertNotNull(result);
        assertEquals(testUser, result.getUser());
        assertEquals(NotificationType.TURN_CANCELLED, result.getType());
        assertEquals("Test message", result.getMessage());
        assertFalse(result.isRead());

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreateNotification_UserNotFound() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                notificationService.createNotification(testUser.getId(), NotificationType.TURN_CANCELLED,
                        testNotification.getRelatedEntityId(), "Test message"));

        assertEquals("User not found", exception.getMessage());
        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createFollowUpScheduledDoctorNotification_isGenericNoPHI() {
        UUID doctorId = testUser.getId();
        UUID reminderId = UUID.randomUUID();
        org.mockito.ArgumentCaptor<Notification> captor =
                org.mockito.ArgumentCaptor.forClass(Notification.class);
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createFollowUpScheduledDoctorNotification(doctorId, reminderId);

        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(NotificationType.FOLLOWUP_SCHEDULED, saved.getType());
        assertEquals(reminderId, saved.getRelatedEntityId());
        assertNoClinicalPhi(saved.getMessage());
    }

    @Test
    void createFollowUpScheduledPatientNotification_isGenericNoPHI() {
        UUID patientId = testUser.getId();
        UUID reminderId = UUID.randomUUID();
        org.mockito.ArgumentCaptor<Notification> captor =
                org.mockito.ArgumentCaptor.forClass(Notification.class);
        when(userRepository.findById(patientId)).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createFollowUpScheduledPatientNotification(patientId, reminderId);

        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(NotificationType.FOLLOWUP_SCHEDULED, saved.getType());
        assertEquals(reminderId, saved.getRelatedEntityId());
        assertNoClinicalPhi(saved.getMessage());
    }

    private void assertNoClinicalPhi(String message) {
        assertNotNull(message);
        assertFalse(message.isBlank());
        // Must never embed the clinical tag/motive text (PHI hard rule).
        String lower = message.toLowerCase();
        assertFalse(lower.contains("diabetes"));
        assertFalse(lower.contains("motivo"));
        assertFalse(lower.contains("etiqueta"));
    }

    @Test
    void testGetUnreadNotifications() {
        List<Notification> expectedNotifications = List.of(testNotification);
        when(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(expectedNotifications);

        List<Notification> result = notificationService.getUnreadNotifications(testUser.getId());

        assertEquals(expectedNotifications, result);
        verify(notificationRepository).findByUserIdAndIsReadFalseOrderByCreatedAtDesc(testUser.getId());
    }

    @Test
    void testGetAllNotifications() {
        List<Notification> expectedNotifications = List.of(testNotification);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(expectedNotifications);

        List<Notification> result = notificationService.getAllNotifications(testUser.getId());

        assertEquals(expectedNotifications, result);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(testUser.getId());
    }

    @Test
    void testGetUnreadCount() {
        when(notificationRepository.countByUserIdAndIsReadFalse(testUser.getId())).thenReturn(5L);

        long result = notificationService.getUnreadCount(testUser.getId());

        assertEquals(5L, result);
        verify(notificationRepository).countByUserIdAndIsReadFalse(testUser.getId());
    }

    @Test
    void testMarkAsRead_Success() {
        UUID notificationId = testNotification.getId();
        when(notificationRepository.markAsReadByIdAndUserId(notificationId, testUser.getId())).thenReturn(1);

        boolean result = notificationService.markAsRead(notificationId, testUser.getId());

        assertTrue(result);
        verify(notificationRepository).markAsReadByIdAndUserId(notificationId, testUser.getId());
    }

    @Test
    void testMarkAsRead_NotFound() {
        UUID notificationId = testNotification.getId();
        when(notificationRepository.markAsReadByIdAndUserId(notificationId, testUser.getId())).thenReturn(0);

        boolean result = notificationService.markAsRead(notificationId, testUser.getId());

        assertFalse(result);
        verify(notificationRepository).markAsReadByIdAndUserId(notificationId, testUser.getId());
    }

    @Test
    void testDeleteNotification_Success() {
        UUID notificationId = testNotification.getId();
        when(notificationRepository.deleteByIdAndUserId(notificationId, testUser.getId())).thenReturn(1);

        boolean result = notificationService.deleteNotification(notificationId, testUser.getId());

        assertTrue(result);
        verify(notificationRepository).deleteByIdAndUserId(notificationId, testUser.getId());
    }

    @Test
    void testDeleteNotification_NotFound() {
        UUID notificationId = testNotification.getId();
        when(notificationRepository.deleteByIdAndUserId(notificationId, testUser.getId())).thenReturn(0);

        boolean result = notificationService.deleteNotification(notificationId, testUser.getId());

        assertFalse(result);
        verify(notificationRepository).deleteByIdAndUserId(notificationId, testUser.getId());
    }

    @Test
    void testCreateTurnCancellationNotification() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.createTurnCancellationNotification(
            testUser.getId(), 
            testNotification.getRelatedEntityId(), 
            "doctor",
            "Dr. John Smith",
            "Jane Doe",
            "2024-01-15",
            "10:30"
        );

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreateModifyRequestApprovedNotification() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.createModifyRequestApprovedNotification(
            testUser.getId(), 
            testNotification.getRelatedEntityId(),
            "Dr. John Smith",
            "2024-01-15",
            "10:30",
            "2024-01-16",
            "14:00"
        );

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreateModifyRequestRejectedNotification_WithReason() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.createModifyRequestRejectedNotification(
            testUser.getId(), 
            testNotification.getRelatedEntityId(), 
            "Schedule conflict",
            "Dr. John Smith",
            "2024-01-15",
            "10:30",
            "2024-01-16",
            "14:00"
        );

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreateModifyRequestRejectedNotification_WithoutReason() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.createModifyRequestRejectedNotification(
            testUser.getId(), 
            testNotification.getRelatedEntityId(), 
            null,
            "Dr. John Smith",
            "2024-01-15",
            "10:30",
            "2024-01-16",
            "14:00"
        );

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreateModifyRequestRejectedNotification_WithEmptyReason() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.createModifyRequestRejectedNotification(
            testUser.getId(), 
            testNotification.getRelatedEntityId(), 
            "   ",  // Empty whitespace reason
            "Dr. John Smith",
            "2024-01-15",
            "10:30",
            "2024-01-16",
            "14:00"
        );

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreatePatientFileUploadedNotification() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.createPatientFileUploadedNotification(
            testUser.getId(),
            testNotification.getRelatedEntityId(),
            "Jane Doe",
            "2024-01-15",
            "10:30",
            "analysis.pdf"
        );

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreateTurnReservedNotification() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.createTurnReservedNotification(
            testUser.getId(),
            testNotification.getRelatedEntityId(),
            "Jane Doe",
            "2024-01-15",
            "10:30"
        );

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreateTurnCancellationNotification_CancelledByPatient() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.createTurnCancellationNotification(
            testUser.getId(), 
            testNotification.getRelatedEntityId(), 
            "patient",
            "Dr. John Smith",
            "Jane Doe",
            "2024-01-15",
            "10:30"
        );

        verify(userRepository).findById(testUser.getId());
        verify(notificationRepository).save(any(Notification.class));
    }
}
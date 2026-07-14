package com.medibook.api.service;

import com.medibook.api.entity.Notification;
import com.medibook.api.entity.NotificationType;
import com.medibook.api.entity.User;
import com.medibook.api.repository.NotificationRepository;
import com.medibook.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public Notification createNotification(UUID userId, NotificationType type,
                                         UUID relatedEntityId, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .relatedEntityId(relatedEntityId)
                .message(message)
                .isRead(false)
                .build();

        return notificationRepository.save(notification);
    }

    public List<Notification> getUnreadNotifications(UUID userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getAllNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public boolean markAsRead(UUID notificationId, UUID userId) {
        int updatedRows = notificationRepository.markAsReadByIdAndUserId(notificationId, userId);
        return updatedRows > 0;
    }

    public boolean deleteNotification(UUID notificationId, UUID userId) {
        int deletedRows = notificationRepository.deleteByIdAndUserId(notificationId, userId);
        return deletedRows > 0;
    }

    public void createTurnCancellationNotification(UUID userId, UUID turnId, String cancelledBy,
                                                   String doctorName, String patientName, 
                                                   String appointmentDate, String appointmentTime) {
        String whoSuffix = "patient".equals(cancelledBy) ? "el paciente" : "el médico";
        String message = String.format(
            "Su turno con %s %s para el %s a las %s ha sido cancelado por %s",
            "doctor".equals(cancelledBy) ? "el Dr." : "",
            "doctor".equals(cancelledBy) ? doctorName : patientName,
            appointmentDate,
            appointmentTime,
            whoSuffix
        );
        createNotification(userId, NotificationType.TURN_CANCELLED, turnId, message);
    }

    public void createModifyRequestApprovedNotification(UUID userId, UUID requestId,
                                                       String doctorName, String oldDate, String oldTime,
                                                       String newDate, String newTime) {
        String message = String.format(
            "Su solicitud de modificación de turno con el Dr. %s ha sido aprobada. " +
            "Turno reprogramado del %s a las %s al %s a las %s",
            doctorName, oldDate, oldTime, newDate, newTime
        );
        createNotification(userId, NotificationType.MODIFY_REQUEST_APPROVED, requestId, message);
    }

    public void createModifyRequestRejectedNotification(UUID userId, UUID requestId, String reason,
                                                       String doctorName, String currentDate, String currentTime,
                                                       String requestedDate, String requestedTime) {
        String message = String.format(
            "Su solicitud de modificación de turno con el Dr. %s ha sido rechazada. " +
            "Solicitó cambiar del %s a las %s al %s a las %s",
            doctorName, currentDate, currentTime, requestedDate, requestedTime
        );
        if (reason != null && !reason.trim().isEmpty()) {
            message += ". Motivo: " + reason;
        }
        createNotification(userId, NotificationType.MODIFY_REQUEST_REJECTED, requestId, message);
    }

    public void createPatientFileUploadedNotification(UUID doctorId, UUID turnId, String patientName,
                                                     String appointmentDate, String appointmentTime, String fileName) {
        String message = String.format(
            "El paciente %s ha subido un archivo para su turno del %s a las %s",
            patientName, appointmentDate, appointmentTime
        );
        createNotification(doctorId, NotificationType.PATIENT_FILE_UPLOADED, turnId, message);
    }

    public void createTurnReservedNotification(UUID doctorId, UUID turnId, String patientName,
                                              String appointmentDate, String appointmentTime) {
        String message = String.format(
            "El paciente %s ha agendado un turno para el %s a las %s",
            patientName, appointmentDate, appointmentTime
        );
        createNotification(doctorId, NotificationType.TURN_RESERVED, turnId, message);
    }

    /**
     * One-shot follow-up reminder notification for the DOCTOR.
     * HARD PHI RULE: generic / id-only copy — it MUST NOT embed the clinical
     * tag/motive.
     */
    public void createFollowUpScheduledDoctorNotification(UUID doctorId, UUID reminderId) {
        createNotification(doctorId, NotificationType.FOLLOWUP_SCHEDULED, reminderId,
            "Recordatorio de control agendado para un paciente");
    }

    /**
     * One-shot follow-up reminder notification for the PATIENT.
     * HARD PHI RULE: generic / id-only copy — it MUST NOT embed the clinical
     * tag/motive.
     */
    public void createFollowUpScheduledPatientNotification(UUID patientId, UUID reminderId) {
        createNotification(patientId, NotificationType.FOLLOWUP_SCHEDULED, reminderId,
            "Tu profesional te recomienda un control");
    }
}
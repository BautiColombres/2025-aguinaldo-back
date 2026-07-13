package com.medibook.api.security;

import com.medibook.api.entity.User;
import com.medibook.api.repository.TurnAssignedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Custom security-expression bean used from {@code @PreAuthorize}.
 * Encapsulates the doctor &harr; patient relationship lookup that cannot be
 * expressed inline in SpEL.
 *
 * Authorization rules for reading a patient's medical history:
 * <ul>
 *   <li>ADMIN: always allowed.</li>
 *   <li>PATIENT: allowed only for their own history.</li>
 *   <li>DOCTOR: allowed only when an active (non-cancelled / non-no-show)
 *       relationship with the patient exists.</li>
 * </ul>
 */
@Component("medAuthz")
@RequiredArgsConstructor
public class MedicalHistoryAuthorization {

    private final TurnAssignedRepository turnAssignedRepository;

    public boolean canRead(Authentication authentication, UUID patientId) {
        if (authentication == null || patientId == null
                || !(authentication.getPrincipal() instanceof User principal)) {
            return false;
        }

        String role = principal.getRole();
        if ("ADMIN".equals(role)) {
            return true;
        }
        if ("PATIENT".equals(role)) {
            return patientId.equals(principal.getId());
        }
        if ("DOCTOR".equals(role)) {
            return hasActiveRelationship(principal.getId(), patientId);
        }
        return false;
    }

    /**
     * Service-layer check: a doctor may only read a patient's history when an
     * active relationship exists.
     */
    public boolean doctorHasActiveRelationship(UUID doctorId, UUID patientId) {
        return hasActiveRelationship(doctorId, patientId);
    }

    private boolean hasActiveRelationship(UUID doctorId, UUID patientId) {
        if (doctorId == null || patientId == null) {
            return false;
        }
        return turnAssignedRepository.existsActiveRelationship(doctorId, patientId);
    }
}

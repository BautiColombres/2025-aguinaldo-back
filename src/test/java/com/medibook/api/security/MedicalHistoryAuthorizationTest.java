package com.medibook.api.security;

import com.medibook.api.entity.User;
import com.medibook.api.repository.TurnAssignedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicalHistoryAuthorizationTest {

    @Mock
    private TurnAssignedRepository turnAssignedRepository;

    @InjectMocks
    private MedicalHistoryAuthorization medAuthz;

    private UUID patientId;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
    }

    private Authentication authFor(UUID id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void canRead_nullAuthentication_returnsFalse() {
        assertFalse(medAuthz.canRead(null, patientId));
    }

    @Test
    void canRead_patientReadingOwnHistory_returnsTrue() {
        Authentication auth = authFor(patientId, "PATIENT");

        assertTrue(medAuthz.canRead(auth, patientId));
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void canRead_patientReadingOtherHistory_returnsFalse() {
        Authentication auth = authFor(UUID.randomUUID(), "PATIENT");

        assertFalse(medAuthz.canRead(auth, patientId));
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void canRead_admin_returnsTrue() {
        Authentication auth = authFor(UUID.randomUUID(), "ADMIN");

        assertTrue(medAuthz.canRead(auth, patientId));
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void canRead_relatedDoctor_returnsTrue() {
        UUID doctorId = UUID.randomUUID();
        Authentication auth = authFor(doctorId, "DOCTOR");
        when(turnAssignedRepository.existsActiveRelationship(doctorId, patientId)).thenReturn(true);

        assertTrue(medAuthz.canRead(auth, patientId));
        verify(turnAssignedRepository).existsActiveRelationship(doctorId, patientId);
    }

    @Test
    void canRead_unrelatedDoctor_returnsFalse() {
        UUID doctorId = UUID.randomUUID();
        Authentication auth = authFor(doctorId, "DOCTOR");
        when(turnAssignedRepository.existsActiveRelationship(doctorId, patientId)).thenReturn(false);

        assertFalse(medAuthz.canRead(auth, patientId));
        verify(turnAssignedRepository).existsActiveRelationship(doctorId, patientId);
    }
}

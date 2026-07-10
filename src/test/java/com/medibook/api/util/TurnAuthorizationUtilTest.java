package com.medibook.api.util;

import com.medibook.api.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TurnAuthorizationUtilTest {

    private User patientUser;
    private User doctorUser;
    private User adminUser;
    private UUID patientId;
    private UUID doctorId;

    @Test
    void constructor_CanBeInstantiated() {
        // Test constructor coverage for utility class
        TurnAuthorizationUtil util = new TurnAuthorizationUtil();
        assertNotNull(util);
    }

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();

        patientUser = new User();
        patientUser.setId(patientId);
        patientUser.setRole("PATIENT");
        patientUser.setStatus("ACTIVE");

        doctorUser = new User();
        doctorUser.setId(doctorId);
        doctorUser.setRole("DOCTOR");
        doctorUser.setStatus("ACTIVE");

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setRole("ADMIN");
        adminUser.setStatus("ACTIVE");
    }

    @Test
    void validatePatientTurnCreation_ValidPatient_ReturnsNull() {
        ResponseEntity<Object> result = TurnAuthorizationUtil.validatePatientTurnCreation(patientUser, patientId);
        assertNull(result);
    }

    @Test
    void validatePatientTurnCreation_NotPatient_ReturnsError() {
        ResponseEntity<Object> result = TurnAuthorizationUtil.validatePatientTurnCreation(doctorUser, patientId);
        
        assertNotNull(result);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void validatePatientTurnCreation_DifferentPatientId_ReturnsError() {
        UUID differentPatientId = UUID.randomUUID();
        
        ResponseEntity<Object> result = TurnAuthorizationUtil.validatePatientTurnCreation(patientUser, differentPatientId);
        
        assertNotNull(result);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void validateDoctorTurnCreation_ValidDoctor_ReturnsNull() {
        ResponseEntity<Object> result = TurnAuthorizationUtil.validateDoctorTurnCreation(doctorUser, doctorId);
        assertNull(result);
    }

    @Test
    void validateDoctorTurnCreation_NotDoctor_ReturnsError() {
        ResponseEntity<Object> result = TurnAuthorizationUtil.validateDoctorTurnCreation(patientUser, doctorId);
        
        assertNotNull(result);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void validateDoctorTurnCreation_DifferentDoctorId_ReturnsError() {
        UUID differentDoctorId = UUID.randomUUID();
        
        ResponseEntity<Object> result = TurnAuthorizationUtil.validateDoctorTurnCreation(doctorUser, differentDoctorId);
        
        assertNotNull(result);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void validateDoctorTurnAccess_ValidDoctor_ReturnsNull() {
        ResponseEntity<Object> result = TurnAuthorizationUtil.validateDoctorTurnAccess(doctorUser, doctorId);
        assertNull(result);
    }

    @Test
    void validateDoctorTurnAccess_NotDoctor_ReturnsError() {
        ResponseEntity<Object> result = TurnAuthorizationUtil.validateDoctorTurnAccess(patientUser, doctorId);
        
        assertNotNull(result);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void validateDoctorTurnAccess_DifferentDoctorId_ReturnsError() {
        UUID differentDoctorId = UUID.randomUUID();
        
        ResponseEntity<Object> result = TurnAuthorizationUtil.validateDoctorTurnAccess(doctorUser, differentDoctorId);
        
        assertNotNull(result);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void validatePatientTurnAccess_ValidPatient_ReturnsNull() {
        ResponseEntity<Object> result = TurnAuthorizationUtil.validatePatientTurnAccess(patientUser, patientId);
        assertNull(result);
    }

    @Test
    void validatePatientTurnAccess_NotPatient_ReturnsError() {
        ResponseEntity<Object> result = TurnAuthorizationUtil.validatePatientTurnAccess(doctorUser, patientId);
        
        assertNotNull(result);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void validatePatientTurnAccess_DifferentPatientId_ReturnsError() {
        UUID differentPatientId = UUID.randomUUID();
        
        ResponseEntity<Object> result = TurnAuthorizationUtil.validatePatientTurnAccess(patientUser, differentPatientId);
        
        assertNotNull(result);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }
}
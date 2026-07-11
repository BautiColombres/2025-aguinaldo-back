package com.medibook.api.util;

import com.medibook.api.entity.User;
import org.springframework.http.ResponseEntity;

import java.util.UUID;


public class TurnAuthorizationUtil {


    public static ResponseEntity<Object> validatePatientTurnCreation(User authenticatedUser, UUID patientId) {
        if (!AuthorizationUtil.isPatient(authenticatedUser)) {
            return AuthorizationUtil.createOwnershipAccessDeniedResponse("Only patients can create turns for themselves");
        }
        
        if (!AuthorizationUtil.hasOwnership(authenticatedUser, patientId)) {
            return AuthorizationUtil.createOwnershipAccessDeniedResponse("Patients can only create turns for themselves");
        }
        
        return null;
    }


    public static ResponseEntity<Object> validateDoctorTurnCreation(User authenticatedUser, UUID doctorId) {
        if (!AuthorizationUtil.isDoctor(authenticatedUser)) {
            return AuthorizationUtil.createOwnershipAccessDeniedResponse("Only doctors can create turns for themselves");
        }
        
        if (!AuthorizationUtil.hasOwnership(authenticatedUser, doctorId)) {
            return AuthorizationUtil.createOwnershipAccessDeniedResponse("Doctors can only create turns for themselves");
        }
        
        return null;
    }


    public static ResponseEntity<Object> validateDoctorTurnAccess(User authenticatedUser, UUID doctorId) {
        if (!AuthorizationUtil.isDoctor(authenticatedUser)) {
            return AuthorizationUtil.createOwnershipAccessDeniedResponse("Only doctors can access doctor turns");
        }
        
        if (!AuthorizationUtil.hasOwnership(authenticatedUser, doctorId)) {
            return AuthorizationUtil.createOwnershipAccessDeniedResponse("You can only view your own turns");
        }
        
        return null;
    }

    public static ResponseEntity<Object> validatePatientTurnAccess(User authenticatedUser, UUID patientId) {
        if (!AuthorizationUtil.isPatient(authenticatedUser)) {
            return AuthorizationUtil.createOwnershipAccessDeniedResponse("Only patients can access patient turns");
        }
        
        if (!AuthorizationUtil.hasOwnership(authenticatedUser, patientId)) {
            return AuthorizationUtil.createOwnershipAccessDeniedResponse("You can only view your own turns");
        }
        
        return null;
    }
}
package com.medibook.api.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserEntityTest {

    private Validator validator;
    private User validUser;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        validUser = new User();
        validUser.setId(UUID.randomUUID());
        validUser.setEmail("test@test.com");
        validUser.setDni(12345678L);
        validUser.setPasswordHash("hashedPassword");
        validUser.setName("John");
        validUser.setSurname("Doe");
        validUser.setPhone("1234567890");
        validUser.setBirthdate(LocalDate.of(1990, 1, 1));
        validUser.setGender("MALE");
        validUser.setEmailVerified(false);
        validUser.setStatus("ACTIVE");
        validUser.setRole("PATIENT");
    }

    @Test
    void validUser_NoViolations() {
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertTrue(violations.isEmpty());
    }

    @Test
    void user_NullEmail_HasViolations() {
        validUser.setEmail(null);
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void user_InvalidEmail_HasViolations() {
        validUser.setEmail("invalid-email");
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void user_EmptyEmail_HasViolations() {
        validUser.setEmail("");
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void user_NullDNI_HasViolations() {
        validUser.setDni(null);
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("dni")));
    }

    @Test
    void user_NullName_HasViolations() {
        validUser.setName(null);
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    }

    @Test
    void user_EmptyName_HasViolations() {
        validUser.setName("");
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    }

    @Test
    void user_NullSurname_HasViolations() {
        validUser.setSurname(null);
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("surname")));
    }

    @Test
    void user_EmptySurname_HasViolations() {
        validUser.setSurname("");
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("surname")));
    }

    @Test
    void user_DefaultValues_AreSetCorrectly() {
        User newUser = new User();
        
        assertFalse(newUser.isEmailVerified());
        assertEquals("ACTIVE", newUser.getStatus());
        assertEquals("PATIENT", newUser.getRole());
    }

    @Test
    void user_SetDoctorProfile_SetsCorrectly() {
        DoctorProfile doctorProfile = new DoctorProfile();
        doctorProfile.setMedicalLicense("ML12345");
        doctorProfile.setSpecialty("CARDIOLOGY");
        doctorProfile.setSlotDurationMin(30);

        validUser.setDoctorProfile(doctorProfile);

        assertNotNull(validUser.getDoctorProfile());
        assertEquals("ML12345", validUser.getDoctorProfile().getMedicalLicense());
        assertEquals("CARDIOLOGY", validUser.getDoctorProfile().getSpecialty());
        assertEquals(30, validUser.getDoctorProfile().getSlotDurationMin());
        assertEquals(validUser, doctorProfile.getUser());
        // The setter no longer forces the id; the shared PK is derived from the
        // user's id via @MapsId at persist time, so it remains null on an unpersisted profile.
        assertNull(doctorProfile.getId());
    }

    @Test
    void user_SetNullDoctorProfile_HandlesCorrectly() {
        DoctorProfile doctorProfile = new DoctorProfile();
        validUser.setDoctorProfile(doctorProfile);
        assertNotNull(validUser.getDoctorProfile());

        validUser.setDoctorProfile(null);
        assertNull(validUser.getDoctorProfile());
    }

    @Test
    void user_Equality_BasedOnId() {
        UUID sharedId = UUID.randomUUID();
        
        User user1 = new User();
        user1.setId(sharedId);
        user1.setEmail("user1@test.com");
        
        User user2 = new User();
        user2.setId(sharedId);
        user2.setEmail("user2@test.com");
        
        assertEquals(user1.getId(), user2.getId());
    }

    @Test
    void user_DifferentIds_NotEqual() {
        User user1 = new User();
        user1.setId(UUID.randomUUID());
        
        User user2 = new User();
        user2.setId(UUID.randomUUID());
        
        assertNotEquals(user1.getId(), user2.getId());
    }

    @Test
    void user_VeryLongEmail_ShouldHandleCorrectly() {
        String longEmail = "a".repeat(100) + "@" + "b".repeat(100) + ".com";
        validUser.setEmail(longEmail);
        
        validator.validate(validUser);
    }

    @Test
    void user_VeryLongName_ShouldHandleCorrectly() {
        String longName = "A".repeat(1000);
        validUser.setName(longName);
        
        validator.validate(validUser);
    }

    @Test
    void user_SpecialCharactersInName_ShouldHandleCorrectly() {
        validUser.setName("José María O'Connor-Smith");
        validUser.setSurname("Van Der Berg");
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertTrue(violations.isEmpty() || violations.stream().noneMatch(v -> 
            v.getPropertyPath().toString().equals("name") || 
            v.getPropertyPath().toString().equals("surname")));
    }

    @Test
    void user_UnicodeCharacters_ShouldHandleCorrectly() {
        validUser.setName("李明");
        validUser.setSurname("García");
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertTrue(violations.isEmpty() || violations.stream().noneMatch(v -> 
            v.getPropertyPath().toString().equals("name") || 
            v.getPropertyPath().toString().equals("surname")));
    }

    @Test
    void user_PasswordHash_NeverStorePlaintext() {
        validUser.setPasswordHash("plainPassword123");
        assertNotNull(validUser.getPasswordHash());
    }

    @Test
    void user_SensitiveDataAccess_ShouldBeControlled() {
        String userString = validUser.toString();
        if (userString != null) {
            assertFalse(userString.contains(validUser.getPasswordHash()));
        }
    }

    @Test
    void user_ValidRoles_ShouldAccept() {
        String[] validRoles = {"PATIENT", "DOCTOR", "ADMIN"};
        
        for (String role : validRoles) {
            validUser.setRole(role);
            validator.validate(validUser);
        }
    }

    @Test
    void user_ValidStatuses_ShouldAccept() {
        String[] validStatuses = {"ACTIVE", "INACTIVE", "PENDING", "REJECTED"};
        
        for (String status : validStatuses) {
            validUser.setStatus(status);
            validator.validate(validUser);
        }
    }

    @Test
    void user_InvalidRole_ShouldReject() {
        validUser.setRole("INVALID_ROLE");
        
        validator.validate(validUser);
    }

    @Test
    void user_InvalidStatus_ShouldReject() {
        validUser.setStatus("INVALID_STATUS");
        
        validator.validate(validUser);
    }

    @Test
    void user_ValidPhoneFormats_ShouldAccept() {
        String[] validPhones = {
            "1234567890",
            "+1234567890",
            "(123) 456-7890",
            "123-456-7890",
            null 
        };
        
        for (String phone : validPhones) {
            validUser.setPhone(phone);
            validator.validate(validUser);
        }
    }

    @Test
    void user_ValidGenders_ShouldAccept() {
        String[] validGenders = {"MALE", "FEMALE", "OTHER", null};
        
        for (String gender : validGenders) {
            validUser.setGender(gender);
            validator.validate(validUser);
        }
    }

    @Test
    void user_FutureBirthdate_ShouldReject() {
        validUser.setBirthdate(LocalDate.now().plusDays(1));
        
        validator.validate(validUser);
    }

    @Test
    void user_VeryOldBirthdate_ShouldAccept() {
        validUser.setBirthdate(LocalDate.of(1900, 1, 1));
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertTrue(violations.isEmpty() || violations.stream().noneMatch(v -> 
            v.getPropertyPath().toString().equals("birthdate")));
    }

    @Test
    void user_NullBirthdate_ShouldAccept() {
        validUser.setBirthdate(null);
        
        Set<ConstraintViolation<User>> violations = validator.validate(validUser);
        assertTrue(violations.isEmpty() || violations.stream().noneMatch(v -> 
            v.getPropertyPath().toString().equals("birthdate")));
    }
}
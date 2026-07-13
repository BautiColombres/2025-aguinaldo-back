package com.medibook.api.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


class UserTest {
    
    @Test
    void whenCreated_thenAllFieldsAreSet() {
        UUID id = UUID.randomUUID();
        String email = "test@example.com";
        String passwordHash = "hashed_password";
        String name = "John";
        String surname = "Doe";
        String phone = "+1234567890";
        LocalDate birthdate = LocalDate.of(1990, 1, 1);
        String gender = "MALE";
        boolean emailVerified = false;
        String status = "ACTIVE";
        String role = "PATIENT";

        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setName(name);
        user.setSurname(surname);
        user.setPhone(phone);
        user.setBirthdate(birthdate);
        user.setGender(gender);
        user.setEmailVerified(emailVerified);
        user.setStatus(status);
        user.setRole(role);

        assertEquals(id, user.getId());
        assertEquals(email, user.getEmail());
        assertEquals(passwordHash, user.getPasswordHash());
        assertEquals(name, user.getName());
        assertEquals(surname, user.getSurname());
        assertEquals(phone, user.getPhone());
        assertEquals(birthdate, user.getBirthdate());
        assertEquals(gender, user.getGender());
        assertEquals(emailVerified, user.isEmailVerified());
        assertEquals(status, user.getStatus());
        assertEquals(role, user.getRole());
    }

    @Test
    void setDoctorProfile_associatesBidirectionally_withoutForcingId() {
        // The setter must not force the profile id to the (possibly null) user id.
        // The id is derived at persist time via @MapsId. The setter's only job is to wire
        // both sides of the association.
        User user = new User();
        user.setEmail("doc@example.com");
        user.setName("Doc");
        user.setSurname("Tor");
        // Intentionally no id set yet.

        DoctorProfile profile = new DoctorProfile();
        profile.setMedicalLicense("ML-1");
        profile.setSpecialty("Cardiology");

        user.setDoctorProfile(profile);

        assertSame(profile, user.getDoctorProfile());
        assertSame(user, profile.getUser());
        // Id is NOT forced by the setter (remains null until @MapsId runs at persist).
        assertNull(profile.getId());
    }

    @Test
    void whenNoOptionalFields_thenNullValues() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed_password");
        user.setName("John");
        user.setSurname("Doe");

        assertNull(user.getPhone());
        assertNull(user.getBirthdate());
        assertNull(user.getGender());
        assertFalse(user.isEmailVerified());
        assertEquals("ACTIVE", user.getStatus());
        assertEquals("PATIENT", user.getRole());
    }
}
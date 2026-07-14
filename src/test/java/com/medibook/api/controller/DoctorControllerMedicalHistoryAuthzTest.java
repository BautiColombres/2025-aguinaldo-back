package com.medibook.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.repository.MedicalHistoryRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/doctors/{doctorId}/patients/{patientId}/medical-history must
 * enforce explicit PHI authorization (via @medAuthz), not just query data-scoping.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DoctorControllerMedicalHistoryAuthzTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TurnAssignedRepository turnAssignedRepository;
    @Autowired private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User patient;
    private User relatedDoctor;
    private User unrelatedDoctor;
    private User admin;

    private String relatedDoctorToken;
    private String unrelatedDoctorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        patient = createUser("dc-patient@example.com", 20000001L, "PATIENT");
        relatedDoctor = createUser("dc-related-doctor@example.com", 20000002L, "DOCTOR");
        unrelatedDoctor = createUser("dc-unrelated-doctor@example.com", 20000003L, "DOCTOR");
        admin = createUser("dc-admin@example.com", 20000004L, "ADMIN");

        TurnAssigned turn = turnAssignedRepository.save(TurnAssigned.builder()
                .doctor(relatedDoctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build());

        medicalHistoryRepository.save(MedicalHistory.builder()
                .doctor(relatedDoctor)
                .patient(patient)
                .turn(turn)
                .content("Confidential note")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        relatedDoctorToken = getAuthToken(relatedDoctor.getEmail());
        unrelatedDoctorToken = getAuthToken(unrelatedDoctor.getEmail());
        adminToken = getAuthToken(admin.getEmail());
    }

    @Test
    void relatedDoctor_returns200() throws Exception {
        mockMvc.perform(get("/api/doctors/" + relatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/medical-history")
                .header("Authorization", "Bearer " + relatedDoctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedDoctor_returns403_viaMedAuthz() throws Exception {
        // Unrelated doctor querying their own doctorId but for a patient they have no
        // relationship with: previously this returned 200 with an empty list (data-scoping).
        // It must now be a 403 (explicit authorization denial).
        mockMvc.perform(get("/api/doctors/" + unrelatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/medical-history")
                .header("Authorization", "Bearer " + unrelatedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorSpoofingOtherDoctorId_returns403() throws Exception {
        // related doctor token but requesting under unrelatedDoctor's id -> self-check fails
        mockMvc.perform(get("/api/doctors/" + unrelatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/medical-history")
                .header("Authorization", "Bearer " + relatedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_returns200() throws Exception {
        mockMvc.perform(get("/api/doctors/" + relatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/medical-history")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/doctors/" + relatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/medical-history"))
                .andExpect(status().is4xxClientError());
    }

    private User createUser(String email, long dni, String role) {
        User user = new User();
        user.setEmail(email);
        user.setDni(dni);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setName("Test");
        user.setSurname("User");
        user.setPhone("1234567890");
        user.setBirthdate(LocalDate.of(1990, 1, 1));
        user.setGender("MALE");
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private String getAuthToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}

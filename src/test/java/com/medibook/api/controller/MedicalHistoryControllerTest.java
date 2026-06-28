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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MedicalHistoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TurnAssignedRepository turnAssignedRepository;
    @Autowired private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User patient;
    private User otherPatient;
    private User relatedDoctor;
    private User unrelatedDoctor;
    private User admin;

    private String patientToken;
    private String otherPatientToken;
    private String relatedDoctorToken;
    private String unrelatedDoctorToken;
    private String adminToken;

    private UUID historyId;

    @BeforeEach
    void setUp() throws Exception {
        patient = createUser("mh-patient@example.com", 10000001L, "PATIENT");
        otherPatient = createUser("mh-other-patient@example.com", 10000002L, "PATIENT");
        relatedDoctor = createUser("mh-related-doctor@example.com", 10000003L, "DOCTOR");
        unrelatedDoctor = createUser("mh-unrelated-doctor@example.com", 10000004L, "DOCTOR");
        admin = createUser("mh-admin@example.com", 10000005L, "ADMIN");

        // related doctor has a COMPLETED (active) turn with the patient
        TurnAssigned turn = turnAssignedRepository.save(TurnAssigned.builder()
                .doctor(relatedDoctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build());

        MedicalHistory history = medicalHistoryRepository.save(MedicalHistory.builder()
                .doctor(relatedDoctor)
                .patient(patient)
                .turn(turn)
                .content("Confidential note")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        historyId = history.getId();

        patientToken = getAuthToken(patient.getEmail());
        otherPatientToken = getAuthToken(otherPatient.getEmail());
        relatedDoctorToken = getAuthToken(relatedDoctor.getEmail());
        unrelatedDoctorToken = getAuthToken(unrelatedDoctor.getEmail());
        adminToken = getAuthToken(admin.getEmail());
    }

    // ---- list endpoint /patient/{patientId} ----

    @Test
    void list_relatedDoctor_returns200() throws Exception {
        mockMvc.perform(get("/api/medical-history/patient/" + patient.getId())
                .header("Authorization", "Bearer " + relatedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Confidential note"));
    }

    @Test
    void list_unrelatedDoctor_returns403() throws Exception {
        mockMvc.perform(get("/api/medical-history/patient/" + patient.getId())
                .header("Authorization", "Bearer " + unrelatedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_patientReadingOwn_returns200() throws Exception {
        mockMvc.perform(get("/api/medical-history/patient/" + patient.getId())
                .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    @Test
    void list_patientReadingOther_returns403() throws Exception {
        mockMvc.perform(get("/api/medical-history/patient/" + patient.getId())
                .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/medical-history/patient/" + patient.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void list_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/medical-history/patient/" + patient.getId()))
                .andExpect(status().is4xxClientError());
    }

    // ---- single entry endpoint /{historyId} ----

    @Test
    void byId_relatedDoctor_returns200() throws Exception {
        mockMvc.perform(get("/api/medical-history/" + historyId)
                .header("Authorization", "Bearer " + relatedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(historyId.toString()));
    }

    @Test
    void byId_unrelatedDoctor_returns403() throws Exception {
        mockMvc.perform(get("/api/medical-history/" + historyId)
                .header("Authorization", "Bearer " + unrelatedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void byId_patientReadingOwn_returns200() throws Exception {
        mockMvc.perform(get("/api/medical-history/" + historyId)
                .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    @Test
    void byId_otherPatient_returns403() throws Exception {
        mockMvc.perform(get("/api/medical-history/" + historyId)
                .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

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
        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}

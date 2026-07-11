package com.medibook.api.security;

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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consolidated authorization matrix (BLOCKING CI gate) covering
 * anonymous / patient / doctor-related / doctor-unrelated / admin across the
 * PHI- and IDOR-sensitive endpoints touched by P0/P1:
 * medical-history (P0), doctor medical-history (NEW-H-A), badge (BSEC-M-5),
 * admin-register (BSEC-C-1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthorizationMatrixTest {

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

    @BeforeEach
    void setUp() throws Exception {
        patient = createUser("am-patient@example.com", 50000001L, "PATIENT");
        otherPatient = createUser("am-other-patient@example.com", 50000002L, "PATIENT");
        relatedDoctor = createUser("am-related-doctor@example.com", 50000003L, "DOCTOR");
        unrelatedDoctor = createUser("am-unrelated-doctor@example.com", 50000004L, "DOCTOR");
        admin = createUser("am-admin@example.com", 50000005L, "ADMIN");

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

        patientToken = getAuthToken(patient.getEmail());
        otherPatientToken = getAuthToken(otherPatient.getEmail());
        relatedDoctorToken = getAuthToken(relatedDoctor.getEmail());
        unrelatedDoctorToken = getAuthToken(unrelatedDoctor.getEmail());
        adminToken = getAuthToken(admin.getEmail());
    }

    // ===== medical-history list (P0) =====

    @Test
    void medicalHistoryList_matrix() throws Exception {
        String path = "/api/medical-history/patient/" + patient.getId();
        expect(get(path), null, 401, 403);                          // anonymous
        expect(get(path), patientToken, 200);                       // patient-self
        expect(get(path), otherPatientToken, 403);                  // other patient
        expect(get(path), relatedDoctorToken, 200);                 // doctor-related
        expect(get(path), unrelatedDoctorToken, 403);               // doctor-unrelated
        expect(get(path), adminToken, 200);                         // admin
    }

    // ===== doctor patient medical-history (NEW-H-A) =====

    @Test
    void doctorPatientMedicalHistory_matrix() throws Exception {
        String relatedPath = "/api/doctors/" + relatedDoctor.getId()
                + "/patients/" + patient.getId() + "/medical-history";
        String unrelatedPath = "/api/doctors/" + unrelatedDoctor.getId()
                + "/patients/" + patient.getId() + "/medical-history";

        expect(get(relatedPath), null, 401, 403);                   // anonymous
        expect(get(relatedPath), relatedDoctorToken, 200);          // doctor-related
        expect(get(unrelatedPath), unrelatedDoctorToken, 403);      // doctor-unrelated
        expect(get(relatedPath), patientToken, 403);                // patient (not a doctor)
        expect(get(relatedPath), adminToken, 200);                  // admin
    }

    // ===== badge IDOR (BSEC-M-5) =====

    @Test
    void badgeRead_matrix() throws Exception {
        String path = "/api/badges/" + patient.getId();
        expect(get(path), null, 401, 403);                          // anonymous
        expect(get(path), patientToken, 200);                       // owner
        expect(get(path), otherPatientToken, 403);                  // foreign userId
        expect(get(path), adminToken, 200);                         // admin
    }

    @Test
    void badgeEvaluateWrite_matrix() throws Exception {
        String path = "/api/badges/" + patient.getId() + "/evaluate";
        expect(post(path), null, 401, 403);                         // anonymous
        expect(post(path), patientToken, 200);                      // owner
        expect(post(path), otherPatientToken, 403);                 // foreign userId
        expect(post(path), adminToken, 200);                        // admin
    }

    // ===== admin register (BSEC-C-1) =====

    @Test
    void adminRegister_matrix() throws Exception {
        String path = "/api/auth/register/admin";
        // Valid body so @Valid (400) does not pre-empt the @PreAuthorize (403) authz check.
        String body = "{\"email\":\"new-admin@example.com\",\"password\":\"Password123\","
                + "\"dni\":59999999,\"name\":\"New\",\"surname\":\"Admin\","
                + "\"phone\":\"1234567890\",\"birthdate\":\"1990-01-01\",\"gender\":\"MALE\"}";
        expect(post(path).contentType(MediaType.APPLICATION_JSON).content(body), null, 401, 403);
        expect(post(path).contentType(MediaType.APPLICATION_JSON).content(body), patientToken, 403);
        expect(post(path).contentType(MediaType.APPLICATION_JSON).content(body), relatedDoctorToken, 403);
    }

    // ---- helpers ----

    private void expect(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                        String token, int... allowedStatuses) throws Exception {
        RequestBuilder built = token == null ? req : req.header("Authorization", "Bearer " + token);
        int actual = mockMvc.perform(built).andReturn().getResponse().getStatus();
        for (int s : allowedStatuses) {
            if (actual == s) {
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail(
                "Expected one of " + java.util.Arrays.toString(allowedStatuses)
                        + " but got " + actual);
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

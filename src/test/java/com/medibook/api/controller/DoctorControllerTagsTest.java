package com.medibook.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.AuditLog;
import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import com.medibook.api.repository.AuditLogRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/doctors/{doctorId}/patients/{patientId}/tags — coarse gate
 * hasRole('DOCTOR'); fine ownership + DENY audit enforced in the service.
 * Also covers tags carried on create/update and tag validation (400).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DoctorControllerTagsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TurnAssignedRepository turnAssignedRepository;
    @Autowired private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User patient;
    private User relatedDoctor;
    private User unrelatedDoctor;
    private User admin;
    private TurnAssigned emptyTurn;

    private String relatedDoctorToken;
    private String unrelatedDoctorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        patient = createUser("tags-patient@example.com", 40000001L, "PATIENT");
        relatedDoctor = createUser("tags-related-doctor@example.com", 40000002L, "DOCTOR");
        unrelatedDoctor = createUser("tags-unrelated-doctor@example.com", 40000003L, "DOCTOR");
        admin = createUser("tags-admin@example.com", 40000004L, "ADMIN");

        // A completed turn with a tagged medical history -> establishes the
        // relationship AND gives the frequency query something to count.
        TurnAssigned turn = turnAssignedRepository.save(TurnAssigned.builder()
                .doctor(relatedDoctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(2))
                .status("COMPLETED")
                .build());

        Set<String> tags = new HashSet<>(List.of("diabetes", "gripe"));
        medicalHistoryRepository.save(MedicalHistory.builder()
                .doctor(relatedDoctor)
                .patient(patient)
                .turn(turn)
                .content("note")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tags(tags)
                .build());

        // A second turn without a history — used for the create-with-tags test.
        emptyTurn = turnAssignedRepository.save(TurnAssigned.builder()
                .doctor(relatedDoctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build());

        relatedDoctorToken = getAuthToken(relatedDoctor.getEmail());
        unrelatedDoctorToken = getAuthToken(unrelatedDoctor.getEmail());
        adminToken = getAuthToken(admin.getEmail());
    }

    @Test
    void relatedDoctor_getTags_returns200WithCounts() throws Exception {
        mockMvc.perform(get("/api/doctors/" + relatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/tags")
                .header("Authorization", "Bearer " + relatedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.tag == 'diabetes')].count").value(1))
                .andExpect(jsonPath("$[?(@.tag == 'gripe')].count").value(1));
    }

    @Test
    void unrelatedDoctor_getTags_returns403_andWritesDenyAudit() throws Exception {
        mockMvc.perform(get("/api/doctors/" + unrelatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/tags")
                .header("Authorization", "Bearer " + unrelatedDoctorToken))
                .andExpect(status().isForbidden());

        List<AuditLog> audits = auditLogRepository
                .findBySubjectPatientIdOrderByTimestampDesc(patient.getId());
        assertTrue(audits.stream().anyMatch(a ->
                        a.getAction() == AuditAction.READ
                                && a.getOutcome() == AuditOutcome.DENY
                                && "MEDICAL_HISTORY".equals(a.getResourceType())
                                && unrelatedDoctor.getId().equals(a.getActorUserId())),
                "Expected a READ/DENY audit row for the unrelated doctor");
    }

    @Test
    void doctorSpoofingOtherDoctorId_returns403() throws Exception {
        // related doctor's token but querying under the unrelated doctor's id -> not owner
        mockMvc.perform(get("/api/doctors/" + unrelatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/tags")
                .header("Authorization", "Bearer " + relatedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_getTags_returns401() throws Exception {
        mockMvc.perform(get("/api/doctors/" + relatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/tags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_getTags_returns403_coarseGateIsDoctorOnly() throws Exception {
        // The endpoint is per-doctor scoped and coarse-gated to DOCTOR only;
        // admin has no DOCTOR role so it is rejected at the gate.
        mockMvc.perform(get("/api/doctors/" + relatedDoctor.getId()
                        + "/patients/" + patient.getId() + "/tags")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithTags_returnsNormalizedTagsInResponse() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "turnId", emptyTurn.getId().toString(),
                "content", "consultation",
                "tags", List.of("  Diabetes ", "diabetes", "Control-Anual")));

        mockMvc.perform(post("/api/doctors/" + relatedDoctor.getId() + "/medical-history")
                .header("Authorization", "Bearer " + relatedDoctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(2));
    }

    @Test
    void createWithTooManyTags_returns400() throws Exception {
        List<String> tooMany = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> "tag" + i)
                .toList();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "turnId", emptyTurn.getId().toString(),
                "content", "consultation",
                "tags", tooMany));

        mockMvc.perform(post("/api/doctors/" + relatedDoctor.getId() + "/medical-history")
                .header("Authorization", "Bearer " + relatedDoctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithTooLongTag_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "turnId", emptyTurn.getId().toString(),
                "content", "consultation",
                "tags", List.of("a".repeat(51))));

        mockMvc.perform(post("/api/doctors/" + relatedDoctor.getId() + "/medical-history")
                .header("Authorization", "Bearer " + relatedDoctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithDisallowedCharacter_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "turnId", emptyTurn.getId().toString(),
                "content", "consultation",
                "tags", List.of("bad$tag")));

        mockMvc.perform(post("/api/doctors/" + relatedDoctor.getId() + "/medical-history")
                .header("Authorization", "Bearer " + relatedDoctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
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
        JsonNode node = objectMapper.readTree(response);
        return node.get("accessToken").asText();
    }
}

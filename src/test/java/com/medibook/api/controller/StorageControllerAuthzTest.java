package com.medibook.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Generic storage endpoints are cross-tenant; path traversal +
 * unescaped error reflection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorageControllerAuthzTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TurnAssignedRepository turnAssignedRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User ownerPatient;
    private User otherPatient;
    private User doctor;
    private User admin;

    private String ownerToken;
    private String otherToken;
    private String doctorToken;
    private String adminToken;

    private TurnAssigned turn;

    @BeforeEach
    void setUp() throws Exception {
        ownerPatient = createUser("st-owner@example.com", 40000001L, "PATIENT");
        otherPatient = createUser("st-other@example.com", 40000002L, "PATIENT");
        doctor = createUser("st-doctor@example.com", 40000003L, "DOCTOR");
        admin = createUser("st-admin@example.com", 40000004L, "ADMIN");

        turn = turnAssignedRepository.save(TurnAssigned.builder()
                .doctor(doctor)
                .patient(ownerPatient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build());

        ownerToken = getAuthToken(ownerPatient.getEmail());
        otherToken = getAuthToken(otherPatient.getEmail());
        doctorToken = getAuthToken(doctor.getEmail());
        adminToken = getAuthToken(admin.getEmail());
    }

    // ---- generic delete endpoint must NOT be usable cross-tenant ----

    @Test
    void genericDelete_byDoctor_isForbidden() throws Exception {
        // generic /delete is now admin-only; a doctor must not delete arbitrary files
        mockMvc.perform(delete("/api/storage/delete/archivosTurnos/somefile.pdf")
                .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void genericDelete_byPatient_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/storage/delete/archivosTurnos/somefile.pdf")
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void genericGetUrl_byPatient_isForbidden() throws Exception {
        mockMvc.perform(get("/api/storage/url/archivosTurnos/somefile.pdf")
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void genericUpload_byPatient_isForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "data".getBytes());
        mockMvc.perform(multipart("/api/storage/upload")
                .file(file)
                .param("bucket", "archivosTurnos")
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    // ---- disallowed bucket rejected for admin-only generic endpoints ----

    @Test
    void genericDelete_disallowedBucket_isRejected() throws Exception {
        mockMvc.perform(delete("/api/storage/delete/secret-bucket/file.pdf")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    // ---- turn-file ownership ----

    @Test
    void deleteTurnFile_byNonOwnerPatient_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/storage/delete-turn-file/" + turn.getId())
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadTurnFile_byNonOwnerPatient_isForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "data".getBytes());
        mockMvc.perform(multipart("/api/storage/upload-turn-file")
                .file(file)
                .param("turnId", turn.getId().toString())
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    // ---- error bodies are valid JSON DTOs (no raw exception reflection) ----

    @Test
    void deleteTurnFile_ownerWithNoFile_returnsValidJsonErrorDto() throws Exception {
        String body = mockMvc.perform(delete("/api/storage/delete-turn-file/" + turn.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn().getResponse().getContentAsString();

        // must parse as valid JSON (the old hand-built body broke on quotes) and expose a DTO shape
        JsonNode node = assertDoesNotThrow(() -> objectMapper.readTree(body));
        org.junit.jupiter.api.Assertions.assertTrue(
                node.has("message") || node.has("error"),
                "error response must be a serialized DTO, got: " + body);
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

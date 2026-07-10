package com.medibook.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.User;
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
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BSEC-M-1 — proves bean validation (@Valid) is genuinely applied to the
 * {@code RatingController} request body.
 *
 * <p>NOTE (why this test used to be false-green): {@code TurnAssignedService.addRating}
 * itself rejects out-of-range/null scores AND every case here posts to a random,
 * non-existent turn. So a plain {@code status().isBadRequest()} assertion passed even
 * WITHOUT {@code @Valid} — the 400 came from the service catch block, not from bean
 * validation. To genuinely prove validation runs, we assert the resolved exception is a
 * {@link MethodArgumentNotValidException}: that only appears when {@code @Valid} rejects
 * the body during argument binding (before the controller body / service is reached).
 * Without {@code @Valid} there is no resolved exception, so these assertions fail — true TDD.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RatingControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String patientToken;

    @BeforeEach
    void setUp() throws Exception {
        User patient = createTestPatient();
        patientToken = getAuthToken(patient.getEmail(), "password123");
    }

    @Test
    void rateTurn_ScoreAboveMax_rejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/ratings/turns/" + UUID.randomUUID() + "/rate")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertInstanceOf(
                        MethodArgumentNotValidException.class, result.getResolvedException(),
                        "score=6 must be rejected by @Valid bean validation, not by the service catch"));
    }

    @Test
    void rateTurn_ScoreBelowMin_rejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/ratings/turns/" + UUID.randomUUID() + "/rate")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertInstanceOf(
                        MethodArgumentNotValidException.class, result.getResolvedException(),
                        "score=0 must be rejected by @Valid bean validation, not by the service catch"));
    }

    @Test
    void rateTurn_MissingScore_rejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/ratings/turns/" + UUID.randomUUID() + "/rate")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertInstanceOf(
                        MethodArgumentNotValidException.class, result.getResolvedException(),
                        "null score must be rejected by @Valid bean validation, not by the service catch"));
    }

    /**
     * Positive case: a valid in-range score must NOT be rejected by bean validation.
     * The body passes {@code @Valid}, so no {@link MethodArgumentNotValidException} is
     * raised and the request reaches the service (which then returns 400 "Turn not found"
     * because the turn id is random). This guards against @Valid over-rejecting valid input.
     */
    @Test
    void rateTurn_ValidScore_passesBeanValidationAndReachesService() throws Exception {
        mockMvc.perform(post("/api/ratings/turns/" + UUID.randomUUID() + "/rate")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":4}"))
                .andExpect(result -> assertFalse(
                        result.getResolvedException() instanceof MethodArgumentNotValidException,
                        "a valid in-range score (4) must not be rejected by bean validation"));
    }

    private User createTestPatient() {
        User patient = new User();
        patient.setEmail("rating-patient@example.com");
        patient.setDni(22345678L);
        patient.setPasswordHash(passwordEncoder.encode("password123"));
        patient.setName("John");
        patient.setSurname("Doe");
        patient.setPhone("1234567890");
        patient.setBirthdate(LocalDate.of(1990, 1, 1));
        patient.setGender("MALE");
        patient.setRole("PATIENT");
        patient.setStatus("ACTIVE");
        patient.setEmailVerified(true);
        return userRepository.save(patient);
    }

    private String getAuthToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }
}

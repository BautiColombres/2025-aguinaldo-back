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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String patientToken;

    @BeforeEach
    void setUp() throws Exception {
        createUser("admin@example.com", 11111111L, "ADMIN");
        createUser("existing-patient@example.com", 22222222L, "PATIENT");

        adminToken = getAuthToken("admin@example.com", "password123");
        patientToken = getAuthToken("existing-patient@example.com", "password123");
    }

    private String adminRegisterBody(String email, long dni) {
        return "{"
                + "\"email\":\"" + email + "\","
                + "\"dni\":" + dni + ","
                + "\"password\":\"Password123\","
                + "\"name\":\"New\","
                + "\"surname\":\"Admin\","
                + "\"phone\":\"+1234567890\","
                + "\"birthdate\":\"1985-01-15\","
                + "\"gender\":\"MALE\""
                + "}";
    }

    private String patientRegisterBody(String email, long dni) {
        return "{"
                + "\"email\":\"" + email + "\","
                + "\"dni\":" + dni + ","
                + "\"password\":\"Password123\","
                + "\"name\":\"New\","
                + "\"surname\":\"Patient\","
                + "\"phone\":\"+1234567890\","
                + "\"birthdate\":\"1990-01-15\","
                + "\"gender\":\"MALE\""
                + "}";
    }

    private String doctorRegisterBody(String email, long dni) {
        return "{"
                + "\"email\":\"" + email + "\","
                + "\"dni\":" + dni + ","
                + "\"password\":\"Password123\","
                + "\"name\":\"New\","
                + "\"surname\":\"Doctor\","
                + "\"phone\":\"+1234567890\","
                + "\"birthdate\":\"1980-01-15\","
                + "\"gender\":\"MALE\","
                + "\"medicalLicense\":\"12345\","
                + "\"specialty\":\"CARDIOLOGÍA\","
                + "\"slotDurationMin\":30"
                + "}";
    }

    // ---- BSEC-C-1 ----

    @Test
    void registerAdmin_Anonymous_IsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminRegisterBody("anon-admin@example.com", 33333333L)))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(
                        status == 401 || status == 403,
                        "Anonymous admin registration must be 401/403 but was " + status);
                });
    }

    @Test
    void registerAdmin_AsNonAdmin_IsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register/admin")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminRegisterBody("patient-made-admin@example.com", 44444444L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerAdmin_AsAdmin_IsAllowed() throws Exception {
        mockMvc.perform(post("/api/auth/register/admin")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminRegisterBody("admin-made-admin@example.com", 55555555L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ---- Defense in depth: other public registrations still work ----

    @Test
    void registerPatient_Anonymous_StillWorks() throws Exception {
        mockMvc.perform(post("/api/auth/register/patient")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patientRegisterBody("brand-new-patient@example.com", 66666666L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void registerDoctor_Anonymous_StaysPending() throws Exception {
        mockMvc.perform(post("/api/auth/register/doctor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(doctorRegisterBody("brand-new-doctor@example.com", 77777777L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.status").value("PENDING"));
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

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IDOR subset: /api/badges/{userId}, /{userId}/progress and the
 * /{userId}/evaluate WRITE trigger must enforce ownership (caller owns userId or ADMIN).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BadgeControllerAuthzTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User owner;
    private User other;
    private User admin;

    private String ownerToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser("badge-owner@example.com", 30000001L, "PATIENT");
        other = createUser("badge-other@example.com", 30000002L, "PATIENT");
        admin = createUser("badge-admin@example.com", 30000003L, "ADMIN");

        ownerToken = getAuthToken(owner.getEmail());
        otherToken = getAuthToken(other.getEmail());
        adminToken = getAuthToken(admin.getEmail());
    }

    // ---- read: /{userId} ----

    @Test
    void getUserBadges_foreignUserId_returns403() throws Exception {
        mockMvc.perform(get("/api/badges/" + owner.getId())
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserBadges_ownUserId_returns200() throws Exception {
        mockMvc.perform(get("/api/badges/" + owner.getId())
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void getUserBadges_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/badges/" + owner.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ---- read: /{userId}/progress ----

    @Test
    void getUserBadgeProgress_foreignUserId_returns403() throws Exception {
        mockMvc.perform(get("/api/badges/" + owner.getId() + "/progress")
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserBadgeProgress_ownUserId_returns200() throws Exception {
        mockMvc.perform(get("/api/badges/" + owner.getId() + "/progress")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    // ---- WRITE trigger: /{userId}/evaluate ----

    @Test
    void evaluate_foreignUserId_returns403() throws Exception {
        mockMvc.perform(post("/api/badges/" + owner.getId() + "/evaluate")
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void evaluate_ownUserId_returns200() throws Exception {
        mockMvc.perform(post("/api/badges/" + owner.getId() + "/evaluate")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void evaluate_admin_returns200() throws Exception {
        mockMvc.perform(post("/api/badges/" + owner.getId() + "/evaluate")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
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

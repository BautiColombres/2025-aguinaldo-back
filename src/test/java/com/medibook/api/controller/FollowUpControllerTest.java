package com.medibook.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.DoctorProfile;
import com.medibook.api.entity.FollowUpReminder;
import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.repository.FollowUpReminderRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FollowUpControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TurnAssignedRepository turnAssignedRepository;
    @Autowired private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired private FollowUpReminderRepository followUpReminderRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User patient;
    private User otherPatient;
    private User ownerDoctor;
    private User otherDoctor;
    private User admin;

    private String patientToken;
    private String otherPatientToken;
    private String ownerDoctorToken;
    private String otherDoctorToken;
    private String adminToken;

    private UUID cleanHistoryId;         // no reminder yet -> for create
    private UUID historyWithReminderId;  // history that already has an active reminder
    private UUID reminderId;             // active reminder owned by ownerDoctor

    @BeforeEach
    void setUp() throws Exception {
        patient = createUser("fu-patient@example.com", 50000001L, "PATIENT");
        otherPatient = createUser("fu-other-patient@example.com", 50000002L, "PATIENT");
        ownerDoctor = createUser("fu-owner-doctor@example.com", 50000003L, "DOCTOR");
        otherDoctor = createUser("fu-other-doctor@example.com", 50000004L, "DOCTOR");
        admin = createUser("fu-admin@example.com", 50000005L, "ADMIN");

        // UX-1: the reminder must name the recommending doctor -> the specialty comes
        // from the doctor's profile (derived, not a persisted reminder column).
        // The owner doctor gets a DISTINCT name from createUser's default "Test User" so the
        // doctorName assertion actually discriminates: a mapper wrongly reading
        // reminder.getPatient() would yield "Test User" and fail.
        ownerDoctor.setName("Ana");
        ownerDoctor.setSurname("Gomez");
        ownerDoctor = withDoctorProfile(ownerDoctor, "Cardiologia", "MP-50003");

        // history WITH an active reminder (for list/dismiss)
        TurnAssigned turn1 = turnAssignedRepository.save(TurnAssigned.builder()
                .doctor(ownerDoctor).patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(30)).status("COMPLETED").build());
        MedicalHistory history1 = medicalHistoryRepository.save(MedicalHistory.builder()
                .doctor(ownerDoctor).patient(patient).turn(turn1).content("note1")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        FollowUpReminder reminder = followUpReminderRepository.save(FollowUpReminder.builder()
                .medicalHistory(history1).doctor(ownerDoctor).patient(patient)
                .monthsUntilControl(3).scheduledFor(LocalDate.now()).dismissed(false)
                .createdAt(LocalDateTime.now()).build());
        reminderId = reminder.getId();
        historyWithReminderId = history1.getId();

        // clean history WITHOUT a reminder (for create)
        TurnAssigned turn2 = turnAssignedRepository.save(TurnAssigned.builder()
                .doctor(ownerDoctor).patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(10)).status("COMPLETED").build());
        MedicalHistory history2 = medicalHistoryRepository.save(MedicalHistory.builder()
                .doctor(ownerDoctor).patient(patient).turn(turn2).content("note2")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        cleanHistoryId = history2.getId();

        patientToken = getAuthToken(patient.getEmail());
        otherPatientToken = getAuthToken(otherPatient.getEmail());
        ownerDoctorToken = getAuthToken(ownerDoctor.getEmail());
        otherDoctorToken = getAuthToken(otherDoctor.getEmail());
        adminToken = getAuthToken(admin.getEmail());
    }

    // ---- POST create ----

    @Test
    void create_ownerDoctor_valid_returns201() throws Exception {
        mockMvc.perform(post("/api/doctors/" + ownerDoctor.getId()
                        + "/medical-history/" + cleanHistoryId + "/followup")
                        .header("Authorization", "Bearer " + ownerDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsUntilControl\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthsUntilControl").value(3))
                .andExpect(jsonPath("$.patientId").value(patient.getId().toString()));
    }

    @Test
    void create_invalidMonths_returns400() throws Exception {
        mockMvc.perform(post("/api/doctors/" + ownerDoctor.getId()
                        + "/medical-history/" + cleanHistoryId + "/followup")
                        .header("Authorization", "Bearer " + ownerDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsUntilControl\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_foreignDoctorIdInPath_returns403() throws Exception {
        mockMvc.perform(post("/api/doctors/" + otherDoctor.getId()
                        + "/medical-history/" + cleanHistoryId + "/followup")
                        .header("Authorization", "Bearer " + ownerDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsUntilControl\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_patientRole_returns403() throws Exception {
        mockMvc.perform(post("/api/doctors/" + patient.getId()
                        + "/medical-history/" + cleanHistoryId + "/followup")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsUntilControl\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/doctors/" + ownerDoctor.getId()
                        + "/medical-history/" + cleanHistoryId + "/followup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsUntilControl\":3}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_ignoresSpoofedBodyFields_derivesPatientDoctorAndScheduledForServerSide() throws Exception {
        // Body ALSO carries spoofed patientId/doctorId/scheduledFor. Those MUST be
        // ignored: the saved reminder's patient/doctor/scheduledFor are derived from
        // the loaded medical-history entry + its turn (IDOR guarantee, regression-proof).
        String spoofed = "{"
                + "\"monthsUntilControl\":3,"
                + "\"patientId\":\"" + otherPatient.getId() + "\","
                + "\"doctorId\":\"" + otherDoctor.getId() + "\","
                + "\"scheduledFor\":\"2000-01-01\""
                + "}";

        mockMvc.perform(post("/api/doctors/" + ownerDoctor.getId()
                        + "/medical-history/" + cleanHistoryId + "/followup")
                        .header("Authorization", "Bearer " + ownerDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofed))
                .andExpect(status().isCreated())
                // Derived from the history/turn, NOT the spoofed body values.
                .andExpect(jsonPath("$.patientId").value(patient.getId().toString()))
                .andExpect(jsonPath("$.doctorId").value(ownerDoctor.getId().toString()))
                .andExpect(jsonPath("$.scheduledFor").value(not("2000-01-01")));
    }

    // ---- GET due list ----

    @Test
    void getDue_ownerDoctor_returns200() throws Exception {
        mockMvc.perform(get("/api/doctors/" + ownerDoctor.getId() + "/followups")
                        .header("Authorization", "Bearer " + ownerDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientId").value(patient.getId().toString()));
    }

    @Test
    void getDue_foreignDoctorId_returns403() throws Exception {
        mockMvc.perform(get("/api/doctors/" + otherDoctor.getId() + "/followups")
                        .header("Authorization", "Bearer " + ownerDoctorToken))
                .andExpect(status().isForbidden());
    }

    // ---- GET due-for-followup panel ----

    @Test
    void dueForFollowup_ownerDoctor_returnsOnlyOwnDuePatients() throws Exception {
        mockMvc.perform(get("/api/doctors/" + ownerDoctor.getId() + "/patients/due-for-followup")
                        .header("Authorization", "Bearer " + ownerDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientId").value(patient.getId().toString()))
                .andExpect(jsonPath("$[0].scheduledFor").exists())
                .andExpect(jsonPath("$[0].lastTurnDate").exists())
                .andExpect(jsonPath("$[0].monthsOverdue").doesNotExist());
    }

    @Test
    void dueForFollowup_foreignDoctorId_returns403() throws Exception {
        mockMvc.perform(get("/api/doctors/" + otherDoctor.getId() + "/patients/due-for-followup")
                        .header("Authorization", "Bearer " + ownerDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void dueForFollowup_patientRole_returns403() throws Exception {
        mockMvc.perform(get("/api/doctors/" + patient.getId() + "/patients/due-for-followup")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void dueForFollowup_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/doctors/" + ownerDoctor.getId() + "/patients/due-for-followup"))
                .andExpect(status().isUnauthorized());
    }

    // ---- PUT dismiss ----

    @Test
    void dismiss_ownerDoctor_returns204() throws Exception {
        mockMvc.perform(put("/api/doctors/" + ownerDoctor.getId()
                        + "/followups/" + reminderId + "/dismiss")
                        .header("Authorization", "Bearer " + ownerDoctorToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void dismiss_anotherDoctorsReminder_returns404UnderScope() throws Exception {
        // otherDoctor requests dismiss of ownerDoctor's reminder under their own scope.
        mockMvc.perform(put("/api/doctors/" + otherDoctor.getId()
                        + "/followups/" + reminderId + "/dismiss")
                        .header("Authorization", "Bearer " + otherDoctorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void dismissThenRecreate_sameHistory_returns201() throws Exception {
        // After dismiss, existsByMedicalHistory_IdAndDismissedFalse is false, so a new
        // reminder for the SAME history succeeds (201) instead of the active-dup 409.
        // Proves the dismiss + recreate interval-change flow.
        mockMvc.perform(put("/api/doctors/" + ownerDoctor.getId()
                        + "/followups/" + reminderId + "/dismiss")
                        .header("Authorization", "Bearer " + ownerDoctorToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/doctors/" + ownerDoctor.getId()
                        + "/medical-history/" + historyWithReminderId + "/followup")
                        .header("Authorization", "Bearer " + ownerDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsUntilControl\":6}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthsUntilControl").value(6))
                .andExpect(jsonPath("$.patientId").value(patient.getId().toString()));
    }

    @Test
    void createTwice_activeReminder_secondReturns409() throws Exception {
        // Sanity counterpart: without dismissing, a second create for the same
        // history is the active-duplicate conflict (409). Guards the recreate test.
        mockMvc.perform(post("/api/doctors/" + ownerDoctor.getId()
                        + "/medical-history/" + historyWithReminderId + "/followup")
                        .header("Authorization", "Bearer " + ownerDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthsUntilControl\":6}"))
                .andExpect(status().isConflict());
    }

    // ---- GET patient-owned ----

    @Test
    void patientReminders_ownPatient_returns200() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient.getId() + "/followups")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientId").value(patient.getId().toString()));
    }

    // ---- UX-1: the patient's reminder names the recommending doctor ----

    @Test
    void patientReminders_namesTheRecommendingDoctorAndSpecialty() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient.getId() + "/followups")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doctorId").value(ownerDoctor.getId().toString()))
                .andExpect(jsonPath("$[0].doctorName").value("Ana Gomez"))
                .andExpect(jsonPath("$[0].specialty").value("Cardiologia"))
                // the patient is "Test User" -> doctorName must NOT be the patient's name
                .andExpect(jsonPath("$[0].patientName").value("Test"));
    }

    /**
     * The enriched DTO must STILL carry no PHI: no clinical tag/motive, no history content,
     * and no other patient's data.
     */
    @Test
    void patientReminders_enrichedDto_stillExcludesPhi() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient.getId() + "/followups")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doctorName").exists())
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[0].tags").doesNotExist())
                .andExpect(jsonPath("$[0].motive").doesNotExist())
                .andExpect(jsonPath("$[0].notes").doesNotExist())
                .andExpect(jsonPath("$[0].diagnosis").doesNotExist())
                .andExpect(jsonPath("$[0].monthsOverdue").doesNotExist())
                // no leakage of the doctor's private contact details either
                .andExpect(jsonPath("$[0].doctorEmail").doesNotExist())
                .andExpect(jsonPath("$[0].doctorDni").doesNotExist())
                .andExpect(content().string(not(containsString("note1"))))
                .andExpect(content().string(not(containsString(otherPatient.getId().toString()))));
    }

    @Test
    void patientReminders_otherPatientId_returns403() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient.getId() + "/followups")
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientReminders_doctorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient.getId() + "/followups")
                        .header("Authorization", "Bearer " + ownerDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientReminders_admin_returns403() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient.getId() + "/followups")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientReminders_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient.getId() + "/followups"))
                .andExpect(status().isUnauthorized());
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

    private User withDoctorProfile(User doctor, String specialty, String medicalLicense) {
        DoctorProfile profile = new DoctorProfile();
        profile.setSpecialty(specialty);
        profile.setMedicalLicense(medicalLicense);
        profile.setSlotDurationMin(30);
        doctor.setDoctorProfile(profile);
        return userRepository.saveAndFlush(doctor);
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

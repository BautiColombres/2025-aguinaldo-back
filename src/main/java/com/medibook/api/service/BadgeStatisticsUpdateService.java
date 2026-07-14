package com.medibook.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.BadgeStatistics;
import com.medibook.api.entity.Rating;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.repository.BadgeRepository;
import com.medibook.api.repository.BadgeStatisticsRepository;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class BadgeStatisticsUpdateService {

    private final BadgeStatisticsRepository statisticsRepository;
    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;
    private final TurnAssignedRepository turnAssignedRepository;
    private final BadgeRepository badgeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TOP_SPECIALIST_REQUIRED_RATINGS = 35;

    @Transactional
    public void updateAfterRatingAddedSync(UUID userId, Integer communicationScore, Integer empathyScore, Integer punctualityScore) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "total_ratings_received");

            if (communicationScore != null) incrementCounter(statistics, "communication_ratings");
            if (empathyScore != null) incrementCounter(statistics, "empathy_ratings");
            if (punctualityScore != null) incrementCounter(statistics, "punctuality_ratings");

            updateRatingBasedStatistics(userId, statistics);

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating rating statistics for user {}", userId, e);
            throw new RuntimeException("Statistics update failed", e);
        }
    }

    @Transactional
    public void updateProgressAfterRatingSync(UUID userId) {
        ensureStatisticsExist(userId);
        BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();
        Map<String, Object> statistics = parseJson(stats.getStatistics());
        Map<String, Object> progress = parseJson(stats.getProgress());

        User user = userRepository.findById(userId).orElseThrow();
        Integer totalTurns = (Integer) statistics.getOrDefault("total_turns_completed", 0);
        if ("PATIENT".equals(user.getRole())) {
            Integer ratingsGiven = (Integer) statistics.getOrDefault("ratings_given", 0);
            double current = (Double) progress.getOrDefault("PATIENT_RESPONSIBLE_EVALUATOR", 0.0);
            progress.put("PATIENT_RESPONSIBLE_EVALUATOR", Math.max(current, Math.min(ratingsGiven * 100.0 / 10, 100.0)));

            Integer collaborationCount = (Integer) statistics.getOrDefault("doctor_collaboration_mentions", 0);
            current = (Double) progress.getOrDefault("PATIENT_EXCELLENT_COLLABORATOR", 0.0);
            progress.put("PATIENT_EXCELLENT_COLLABORATOR", Math.max(current, Math.min(collaborationCount * 100.0 / 10, 100.0)));

            Integer doctorPunctualityCount = (Integer) statistics.getOrDefault("doctor_punctuality_mentions", 0);
            current = (Double) progress.getOrDefault("PATIENT_EXEMPLARY_PUNCTUALITY", 0.0);
            progress.put("PATIENT_EXEMPLARY_PUNCTUALITY", Math.max(current, Math.min(doctorPunctualityCount * 100.0 / 10, 100.0)));

        } else if ("DOCTOR".equals(user.getRole())) {
            Integer totalRatings = (Integer) statistics.getOrDefault("total_ratings_received", 0);
            Integer communicationCount = (Integer) statistics.getOrDefault("total_communication_count", 0);
            Integer empathyCount = (Integer) statistics.getOrDefault("total_empathy_count", 0);
            Integer punctualityCount = (Integer) statistics.getOrDefault("total_punctuality_count", 0);
            Double avgRating = (Double) statistics.getOrDefault("total_avg_rating", 0.0);
            Integer lowRatingCount = (Integer) statistics.getOrDefault("total_low_rating_count", 0);

            double communicationProgress = Math.min(communicationCount * 100.0 / 25, 100.0);
            double current = (Double) progress.getOrDefault("DOCTOR_EXCEPTIONAL_COMMUNICATOR", 0.0);
            progress.put("DOCTOR_EXCEPTIONAL_COMMUNICATOR", Math.max(current, communicationProgress));

            double empathyProgress = Math.min(empathyCount * 100.0 / 25, 100.0);
            current = (Double) progress.getOrDefault("DOCTOR_EMPATHETIC_DOCTOR", 0.0);
            progress.put("DOCTOR_EMPATHETIC_DOCTOR", Math.max(current, empathyProgress));

            double punctualityProgress = Math.min(punctualityCount * 100.0 / 20, 100.0);
            current = (Double) progress.getOrDefault("DOCTOR_PUNCTUALITY_PROFESSIONAL", 0.0);
            progress.put("DOCTOR_PUNCTUALITY_PROFESSIONAL", Math.max(current, punctualityProgress));

            progress.put("DOCTOR_TOP_SPECIALIST", calculateTopSpecialistProgress(userId));
            progress.put("DOCTOR_MEDICAL_LEGEND", calculateMedicalLegendProgress(userId));
        }

        try {
            stats.setProgress(objectMapper.valueToTree(progress));
            statisticsRepository.save(stats);
        } catch (Exception e) {
            log.error("Error saving progress for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public void updateAfterTurnCompletedSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "total_turns_completed");

            User user = userRepository.findById(userId).orElseThrow();
            if ("PATIENT".equals(user.getRole())) {
                long maxTurns = turnAssignedRepository.findMaxCompletedTurnsWithSameDoctor(userId);
                statistics.put("turns_with_same_doctor", (int) maxTurns);
            }

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating turn completion statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateAfterTurnCompletedSync(UUID userId, UUID patientId) {
        updateAfterTurnCompletedSync(userId);

        try {
            User user = userRepository.findById(userId).orElseThrow();
            if (!"DOCTOR".equals(user.getRole())) {
                return; // Only doctors have unique patients served
            }

            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());

            int uniquePatients = turnAssignedRepository.findDistinctPatientsByDoctorId(userId).size();
            statistics.put("total_unique_patients", uniquePatients);

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating unique patient statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateProgressAfterTurnCompletionSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();
            Map<String, Object> statistics = parseJson(stats.getStatistics());
            Map<String, Object> progress = parseJson(stats.getProgress());

            User user = userRepository.findById(userId).orElseThrow();
            Integer totalTurns = (Integer) statistics.getOrDefault("total_turns_completed", 0);
            if ("PATIENT".equals(user.getRole())) {
                progress.put("PATIENT_MEDIBOOK_WELCOME", Math.min(totalTurns * 100.0 / 1, 100.0));

                progress.put("PATIENT_COMMITTED_PATIENT", Math.min(totalTurns * 100.0 / 5, 100.0));

                Integer turnsWithSameDoctor = (Integer) statistics.getOrDefault("turns_with_same_doctor", 0);
                progress.put("PATIENT_CONTINUOUS_FOLLOWUP", Math.min(turnsWithSameDoctor * 100.0 / 3, 100.0));

                progress.put("PATIENT_CONSTANT_PATIENT", Math.min(totalTurns * 100.0 / 15, 100.0));

            } else if ("DOCTOR".equals(user.getRole())) {
                updateDoctorBadgeProgress(user.getId(), statistics, progress, totalTurns);
            }

            try {
                stats.setProgress(objectMapper.valueToTree(progress));
                statisticsRepository.save(stats);
            } catch (Exception e) {
                log.error("Error saving progress for user {}: {}", userId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error updating turn completion progress for user {}", userId, e);
        }
    }

    @Transactional
    public void updateAfterMedicalHistoryDocumentedSync(UUID userId, String content) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "documentation_count");

            if (content != null && !content.trim().isEmpty()) {
                int wordCount = content.trim().split("\\s+").length;
                Integer currentWordCount = (Integer) statistics.getOrDefault("total_documentation_words", 0);
                statistics.put("total_documentation_words", currentWordCount + wordCount);
            }

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating documentation statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateProgressAfterMedicalHistorySync(UUID userId) {
        ensureStatisticsExist(userId);
        BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();
        Map<String, Object> statistics = parseJson(stats.getStatistics());
        Map<String, Object> progress = parseJson(stats.getProgress());

        User user = userRepository.findById(userId).orElseThrow();

        if ("DOCTOR".equals(user.getRole())) {
            Integer documentationCount = (Integer) statistics.getOrDefault("documentation_count", 0);
            Integer totalTurns = (Integer) statistics.getOrDefault("total_turns_completed", 0);

            if (totalTurns >= 50) {
                double documentationProgress = Math.min((documentationCount * 100.0) / 35, 100.0);
                progress.put("DOCTOR_COMPLETE_DOCUMENTER", documentationProgress);
            } else {
                double requiredDocumentation = Math.max(35.0 * (totalTurns / 50.0), 1.0);
                progress.put("DOCTOR_COMPLETE_DOCUMENTER", Math.min(documentationCount * 100.0 / requiredDocumentation, 100.0));
            }

            Integer totalWords = (Integer) statistics.getOrDefault("total_documentation_words", 0);
            progress.put("DOCTOR_DETAILED_DIAGNOSTICIAN", Math.min(totalWords * 100.0 / 100, 100.0));
        }

        try {
            stats.setProgress(objectMapper.valueToTree(progress));
            statisticsRepository.save(stats);
        } catch (Exception e) {
            log.error("Error saving progress for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public void updateAfterModifyRequestCreatedSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "requests_created");

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating request creation statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateAfterModifyRequestHandledSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "total_requests_handled");

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating request handling statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateProgressAfterModifyRequestSync(UUID userId) {
        ensureStatisticsExist(userId);
        BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();
        Map<String, Object> statistics = parseJson(stats.getStatistics());
        Map<String, Object> progress = parseJson(stats.getProgress());

        User user = userRepository.findById(userId).orElseThrow();

        if ("DOCTOR".equals(user.getRole())) {
            Integer requestsHandled = (Integer) statistics.getOrDefault("total_requests_handled", 0);
            Integer requestsCreated = (Integer) statistics.getOrDefault("requests_created", 0);

            if (requestsHandled >= 7) {
                progress.put("DOCTOR_AGILE_RESPONDER", 100.0);
            } else {
                progress.put("DOCTOR_AGILE_RESPONDER", requestsHandled * 100.0 / 7);
            }
        }

        try {
            stats.setProgress(objectMapper.valueToTree(progress));
            statisticsRepository.save(stats);
        } catch (Exception e) {
            log.error("Error saving progress for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public void updateAfterTurnCancelledSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "total_cancellations");

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating cancellation statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateProgressAfterCancellationSync(UUID userId) {
        ensureStatisticsExist(userId);
    }

    @Transactional
    public void updateAfterTurnNoShowSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "total_turns_no_show");

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating no-show statistics for user {}", userId, e);
        }
    }

    /**
     * Recomputes the only aggregate that badge progress actually consumes:
     * {@code turns_with_same_doctor} for patients (used by PATIENT_CONTINUOUS_FOLLOWUP).
     */
    private void recomputePatientTurnsWithSameDoctor(UUID userId, Map<String, Object> statistics) {
        try {
            User user = userRepository.findById(userId).orElseThrow();

            if ("PATIENT".equals(user.getRole())) {
                List<TurnAssigned> allCompletedTurns = turnAssignedRepository.findByPatient_IdAndStatus(userId, "COMPLETED");
                Map<UUID, Long> turnsByDoctor = allCompletedTurns.stream()
                        .collect(Collectors.groupingBy(turn -> turn.getDoctor().getId(), Collectors.counting()));
                long maxTurnsWithSameDoctor = turnsByDoctor.values().stream().mapToLong(Long::longValue).max().orElse(0L);
                statistics.put("turns_with_same_doctor", (int) maxTurnsWithSameDoctor);
            }
        } catch (Exception e) {
            log.error("Error recomputing turns_with_same_doctor for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public void updateAllBadgeProgress(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();
            Map<String, Object> statistics = parseJson(stats.getStatistics());
            Map<String, Object> progress = parseJson(stats.getProgress());

            recomputePatientTurnsWithSameDoctor(userId, statistics);

            User user = userRepository.findById(userId).orElseThrow();
            Integer totalTurns = (Integer) statistics.getOrDefault("total_turns_completed", 0);

            if ("PATIENT".equals(user.getRole())) {
                long completedBadges = progress.values().stream()
                        .mapToDouble(v -> (Double) v)
                        .filter(p -> p >= 100.0)
                        .count();
                Double avgRatingReceived = (Double) statistics.getOrDefault("avg_rating_received", 0.0);
                
                updatePatientBadgeProgress(statistics, progress, totalTurns, completedBadges, avgRatingReceived);
            } else if ("DOCTOR".equals(user.getRole())) {
                updateDoctorBadgeProgress(user.getId(), statistics, progress, totalTurns);
            }

            stats.setProgress(objectMapper.valueToTree(progress));
            statisticsRepository.save(stats);
        } catch (Exception e) {
            log.error("Error updating all badge progress for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Progress update failed", e);
        }
    }

    @Transactional
    public void updateAfterAdvanceBookingSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "advance_bookings");

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating advance booking statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateProgressAfterAdvanceBookingSync(UUID userId) {
        ensureStatisticsExist(userId);
    }

    @Transactional
    public void updateProgressAfterRatingReceivedSync(UUID userId) {
        ensureStatisticsExist(userId);
    }

    @Transactional
    public void updateAfterRatingGivenSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "ratings_given");

            User user = userRepository.findById(userId).orElseThrow();
            if ("PATIENT".equals(user.getRole())) {
                List<com.medibook.api.entity.Rating> ratingsGiven = ratingRepository.findByRaterId(userId);
                if (!ratingsGiven.isEmpty()) {
                    double avgRating = ratingsGiven.stream()
                            .mapToDouble(com.medibook.api.entity.Rating::getScore)
                            .average()
                            .orElse(0.0);
                    statistics.put("avg_rating_given", avgRating);
                }
            }

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating rating given statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateProgressAfterBookingSync(UUID userId) {
        ensureStatisticsExist(userId);
    }

    @Transactional
    public void updateAfterRatingReceivedSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "ratings_received");

            User user = userRepository.findById(userId).orElseThrow();
            if ("PATIENT".equals(user.getRole())) {
                List<com.medibook.api.entity.Rating> ratingsReceived = ratingRepository.findByRatedId(userId);
                if (!ratingsReceived.isEmpty()) {
                    double avgRating = ratingsReceived.stream()
                            .mapToDouble(com.medibook.api.entity.Rating::getScore)
                            .average()
                            .orElse(0.0);
                    statistics.put("avg_rating_received", avgRating);
                }
            }

            updateRatingBasedStatistics(userId, statistics);

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating rating received statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateAfterFileUploadedSync(UUID userId) {
        try {
            ensureStatisticsExist(userId);
            BadgeStatistics stats = statisticsRepository.findByUserId(userId).orElseThrow();

            Map<String, Object> statistics = parseJson(stats.getStatistics());
            incrementCounter(statistics, "files_uploaded");

            User user = userRepository.findById(userId).orElseThrow();
            if ("PATIENT".equals(user.getRole())) {
                Integer filesUploaded = (Integer) statistics.getOrDefault("files_uploaded", 0);
                statistics.put("last_10_turns_files_uploaded_count", Math.min(filesUploaded, 10));
            }

            stats.setStatistics(objectMapper.valueToTree(statistics));
            statisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("Error updating file upload statistics for user {}", userId, e);
        }
    }

    @Transactional
    public void updateProgressAfterFileUploadSync(UUID userId) {
        ensureStatisticsExist(userId);
    }

    private void updateRatingBasedStatistics(UUID userId, Map<String, Object> statistics) {
        try {
            List<com.medibook.api.entity.Rating> allRatings = ratingRepository.findByRatedId(userId);

            long communicationCount = allRatings.stream()
                    .filter(r -> r.getScore() >= 4 && r.getSubcategory() != null &&
                            (r.getSubcategory().toLowerCase().contains("explica") ||
                             r.getSubcategory().toLowerCase().contains("escucha") ||
                             r.getSubcategory().toLowerCase().contains("claramente")))
                    .count();

            long empathyCount = allRatings.stream()
                    .filter(r -> r.getScore() >= 4 && r.getSubcategory() != null &&
                            (r.getSubcategory().toLowerCase().contains("empatía") ||
                             r.getSubcategory().toLowerCase().contains("empat") ||
                             r.getSubcategory().toLowerCase().contains("confianza") ||
                             r.getSubcategory().toLowerCase().contains("atención")))
                    .count();

            long punctualityCount = allRatings.stream()
                    .filter(r -> r.getScore() >= 4 && r.getSubcategory() != null &&
                            (r.getSubcategory().toLowerCase().contains("horarios") ||
                             r.getSubcategory().toLowerCase().contains("respeta horarios") ||
                             r.getSubcategory().toLowerCase().contains("tiempo de espera")))
                    .count();

            long doctorCollaborationMentions = allRatings.stream()
                    .filter(r -> r.getScore() >= 4 && r.getSubcategory() != null &&
                            r.getRater().getRole().equals("DOCTOR") &&
                            (r.getSubcategory().toLowerCase().contains("colabora") ||
                             r.getSubcategory().toLowerCase().contains("sigue indicaciones")))
                    .count();

            long doctorPunctualityMentions = allRatings.stream()
                    .filter(r -> r.getScore() >= 4 && r.getSubcategory() != null &&
                            r.getRater().getRole().equals("DOCTOR") &&
                            r.getSubcategory().toLowerCase().contains("llega puntual"))
                    .count();

            statistics.put("total_communication_count", (int) communicationCount);
            statistics.put("total_empathy_count", (int) empathyCount);
            statistics.put("total_punctuality_count", (int) punctualityCount);
            statistics.put("doctor_collaboration_mentions", (int) doctorCollaborationMentions);
            statistics.put("doctor_punctuality_mentions", (int) doctorPunctualityMentions);

            if (!allRatings.isEmpty()) {
                double avgRating = allRatings.stream()
                        .mapToDouble(com.medibook.api.entity.Rating::getScore)
                        .average()
                        .orElse(0.0);

                long lowRatingCount = allRatings.stream()
                        .filter(r -> r.getScore() < 4.0)
                        .count();

                statistics.put("total_avg_rating", avgRating);
                statistics.put("total_low_rating_count", (int) lowRatingCount);
                statistics.put("total_ratings_received", allRatings.size());
            }

        } catch (Exception e) {
            log.error("Error calculating rating-based statistics for user {}: {}", userId, e.getMessage());
        }
    }

    private void ensureStatisticsExist(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (statisticsRepository.findByUserId(userId).isEmpty()) {
            try {
                BadgeStatistics stats = BadgeStatistics.builder()
                        .userId(userId)
                        .statistics(objectMapper.readTree("{}"))
                        .progress(objectMapper.readTree("{}"))
                        .build();

                statisticsRepository.save(stats);
                log.info("Created new statistics record for user {}", userId);
            } catch (JsonProcessingException e) {
                log.error("Error creating statistics for user {}: {}", userId, e.getMessage());
                throw new RuntimeException("Failed to create statistics for user " + userId, e);
            }
        }
    }

    protected Map<String, Object> parseJson(JsonNode json) {
        try {
            if (json == null || json.isNull()) {
                return new HashMap<>();
            }
            return objectMapper.convertValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Error parsing JSON statistics, returning empty map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private void incrementCounter(Map<String, Object> statistics, String key) {
        Integer current = (Integer) statistics.getOrDefault(key, 0);
        statistics.put(key, current + 1);
    }

    private void updatePatientBadgeProgress(Map<String, Object> statistics, Map<String, Object> progress, Integer totalTurns, long completedBadges, Double avgRatingReceived) {
        double current = (Double) progress.getOrDefault("PATIENT_MEDIBOOK_WELCOME", 0.0);
        progress.put("PATIENT_MEDIBOOK_WELCOME", Math.max(current, Math.min(totalTurns * 100.0 / 1, 100.0)));

        current = (Double) progress.getOrDefault("PATIENT_COMMITTED_PATIENT", 0.0);
        progress.put("PATIENT_COMMITTED_PATIENT", Math.max(current, Math.min(totalTurns * 100.0 / 5, 100.0)));

        Integer turnsWithSameDoctor = (Integer) statistics.getOrDefault("turns_with_same_doctor", 0);
        current = (Double) progress.getOrDefault("PATIENT_CONTINUOUS_FOLLOWUP", 0.0);
        progress.put("PATIENT_CONTINUOUS_FOLLOWUP", Math.max(current, Math.min(turnsWithSameDoctor * 100.0 / 3, 100.0)));

        current = (Double) progress.getOrDefault("PATIENT_CONSTANT_PATIENT", 0.0);
        progress.put("PATIENT_CONSTANT_PATIENT", Math.max(current, Math.min(totalTurns * 100.0 / 15, 100.0)));

        Integer ratingsGiven = (Integer) statistics.getOrDefault("ratings_given", 0);
        current = (Double) progress.getOrDefault("PATIENT_RESPONSIBLE_EVALUATOR", 0.0);
        progress.put("PATIENT_RESPONSIBLE_EVALUATOR", Math.max(current, Math.min(ratingsGiven * 100.0 / 10, 100.0)));

        Integer collaborationCount = (Integer) statistics.getOrDefault("doctor_collaboration_mentions", 0);
        current = (Double) progress.getOrDefault("PATIENT_EXCELLENT_COLLABORATOR", 0.0);
        progress.put("PATIENT_EXCELLENT_COLLABORATOR", Math.max(current, Math.min(collaborationCount * 100.0 / 10, 100.0)));

        Integer doctorPunctualityCount = (Integer) statistics.getOrDefault("doctor_punctuality_mentions", 0);
        current = (Double) progress.getOrDefault("PATIENT_EXEMPLARY_PUNCTUALITY", 0.0);
        progress.put("PATIENT_EXEMPLARY_PUNCTUALITY", Math.max(current, Math.min(doctorPunctualityCount * 100.0 / 10, 100.0)));

        Integer advanceBookings = (Integer) statistics.getOrDefault("advance_bookings", 0);
        current = (Double) progress.getOrDefault("PATIENT_SMART_PLANNER", 0.0);
        progress.put("PATIENT_SMART_PLANNER", Math.max(current, Math.min(advanceBookings * 100.0 / 10, 100.0)));

        Integer filesUploaded = (Integer) statistics.getOrDefault("files_uploaded", 0);
        current = (Double) progress.getOrDefault("PATIENT_ALWAYS_PREPARED", 0.0);
        progress.put("PATIENT_ALWAYS_PREPARED", Math.max(current, Math.min(filesUploaded * 100.0 / 10, 100.0)));

        current = (Double) progress.getOrDefault("PATIENT_EXCELLENCE_MODEL", 0.0);
        double excellenceCalculated;
        if (totalTurns >= 25 && completedBadges >= 4) {
            excellenceCalculated = 100.0;
        } else if (totalTurns >= 25) {
            excellenceCalculated = 50.0;
        } else {
            excellenceCalculated = totalTurns * 100.0 / 25;
        }
        progress.put("PATIENT_EXCELLENCE_MODEL", Math.max(current, excellenceCalculated));
    }

    void updateDoctorBadgeProgress(UUID doctorId, Map<String, Object> statistics, Map<String, Object> progress, Integer totalTurns) {
        Integer documentationCount = (Integer) statistics.getOrDefault("documentation_count", 0);
        double current = (Double) progress.getOrDefault("DOCTOR_COMPLETE_DOCUMENTER", 0.0);
        if (totalTurns >= 50) {
            double documentationProgress = Math.min((documentationCount * 100.0) / 35, 100.0);
            progress.put("DOCTOR_COMPLETE_DOCUMENTER", Math.max(current, documentationProgress));
        } else {
            double requiredDocumentation = Math.max(35.0 * (totalTurns / 50.0), 1.0);
            progress.put("DOCTOR_COMPLETE_DOCUMENTER", Math.max(current, Math.min(documentationCount * 100.0 / requiredDocumentation, 100.0)));
        }

        Integer totalCancelled = (Integer) statistics.getOrDefault("total_cancellations", 0);
        double cancellationRate = totalTurns > 0 ? (totalCancelled * 100.0 / totalTurns) : 0;
        current = (Double) progress.getOrDefault("DOCTOR_CONSISTENT_PROFESSIONAL", 0.0);
        if (totalTurns >= 80) {
            double consistentProgress = cancellationRate < 15 ? 100.0 : 0.0;
            progress.put("DOCTOR_CONSISTENT_PROFESSIONAL", Math.max(current, consistentProgress));
        } else {
            progress.put("DOCTOR_CONSISTENT_PROFESSIONAL", Math.max(current, totalTurns * 100.0 / 80));
        }

        Integer uniquePatients = (Integer) statistics.getOrDefault("total_unique_patients", 0);
        current = (Double) progress.getOrDefault("DOCTOR_RELATIONSHIP_BUILDER", 0.0);
        progress.put("DOCTOR_RELATIONSHIP_BUILDER", Math.max(current, Math.min(uniquePatients * 100.0 / 10, 100.0)));

        Integer requestsHandled = (Integer) statistics.getOrDefault("total_requests_handled", 0);
        current = (Double) progress.getOrDefault("DOCTOR_AGILE_RESPONDER", 0.0);
        if (requestsHandled >= 7) {
            progress.put("DOCTOR_AGILE_RESPONDER", Math.max(current, 100.0));
        } else {
            progress.put("DOCTOR_AGILE_RESPONDER", Math.max(current, requestsHandled * 100.0 / 7));
        }

        double topSpecialistProgress = calculateTopSpecialistProgress(doctorId);
        progress.put("DOCTOR_TOP_SPECIALIST", topSpecialistProgress);

        progress.put("DOCTOR_MEDICAL_LEGEND", calculateMedicalLegendProgress(doctorId));

        Integer totalRatings = (Integer) statistics.getOrDefault("total_ratings_received", 0);
        Integer communicationCount = (Integer) statistics.getOrDefault("total_communication_count", 0);
        Integer empathyCount = (Integer) statistics.getOrDefault("total_empathy_count", 0);
        Integer punctualityCount = (Integer) statistics.getOrDefault("total_punctuality_count", 0);
        Double avgRating = (Double) statistics.getOrDefault("total_avg_rating", 0.0);
        Integer lowRatingCount = (Integer) statistics.getOrDefault("total_low_rating_count", 0);

        double communicationProgress = Math.min(communicationCount * 100.0 / 25, 100.0);
        current = (Double) progress.getOrDefault("DOCTOR_EXCEPTIONAL_COMMUNICATOR", 0.0);
        progress.put("DOCTOR_EXCEPTIONAL_COMMUNICATOR", Math.max(current, communicationProgress));

        double empathyProgress = Math.min(empathyCount * 100.0 / 25, 100.0);
        current = (Double) progress.getOrDefault("DOCTOR_EMPATHETIC_DOCTOR", 0.0);
        progress.put("DOCTOR_EMPATHETIC_DOCTOR", Math.max(current, empathyProgress));

        double punctualityProgress = Math.min(punctualityCount * 100.0 / 20, 100.0);
        current = (Double) progress.getOrDefault("DOCTOR_PUNCTUALITY_PROFESSIONAL", 0.0);
        progress.put("DOCTOR_PUNCTUALITY_PROFESSIONAL", Math.max(current, punctualityProgress));
    }

    private double calculateTopSpecialistProgress(UUID doctorId) {
        List<Rating> recentRatings = ratingRepository.findTop35ByRated_IdAndRater_RoleOrderByCreatedAtDesc(doctorId, "PATIENT");
        if (recentRatings == null) {
            recentRatings = java.util.Collections.emptyList();
        }
        long highScoreCount = recentRatings.stream()
                .filter(rating -> rating.getScore() != null && rating.getScore() >= 4)
                .count();
        return Math.min(highScoreCount * 100.0 / TOP_SPECIALIST_REQUIRED_RATINGS, 100.0);
    }

    private double calculateMedicalLegendProgress(UUID doctorId) {
        long activeRequiredBadges = BadgeService.MEDICAL_LEGEND_REQUIRED_BADGES.stream()
                .filter(badgeType -> badgeRepository.existsByUser_IdAndBadgeTypeAndIsActive(doctorId, badgeType, true))
                .count();
        return BadgeService.MEDICAL_LEGEND_REQUIRED_BADGES.isEmpty()
                ? 0.0
                : Math.min(activeRequiredBadges * 100.0 / BadgeService.MEDICAL_LEGEND_REQUIRED_BADGES.size(), 100.0);
    }
}

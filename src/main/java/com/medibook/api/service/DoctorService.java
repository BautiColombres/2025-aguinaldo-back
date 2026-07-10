package com.medibook.api.service;
import com.medibook.api.dto.Badge.BadgeDTO;
import com.medibook.api.dto.DoctorDTO;
import com.medibook.api.dto.DoctorMetricsDTO;
import com.medibook.api.dto.MedicalHistoryDTO;
import com.medibook.api.dto.PatientDTO;
import com.medibook.api.dto.Rating.SubcategoryCountDTO;
import com.medibook.api.entity.Badge;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.DoctorMapper;
import com.medibook.api.repository.BadgeRepository;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DoctorService {

    private final UserRepository userRepository;
    private final TurnAssignedRepository turnAssignedRepository;
    private final DoctorMapper doctorMapper;
    private final MedicalHistoryService medicalHistoryService;
    private final RatingRepository ratingRepository;
    private final BadgeRepository badgeRepository;
    private final BadgeService badgeService;

    public List<DoctorDTO> getAllDoctors() {
        List<User> doctors = userRepository.findDoctorsByStatus("ACTIVE");
        return doctors.stream()
                .map(doctorMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<DoctorDTO> getDoctorsBySpecialty(String specialty) {
        List<User> doctors = userRepository.findDoctorsByStatusAndSpecialty("ACTIVE", specialty);
        return doctors.stream()
                .map(doctorMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<String> getAllSpecialties() {
        List<User> doctors = userRepository.findDoctorsByStatus("ACTIVE");
        return doctors.stream()
                .map(User::getDoctorProfile)
                .filter(java.util.Objects::nonNull)
                .map(com.medibook.api.entity.DoctorProfile::getSpecialty)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<PatientDTO> getPatientsByDoctor(UUID doctorId) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        
        if (!"DOCTOR".equals(doctor.getRole()) || !"ACTIVE".equals(doctor.getStatus())) {
            throw new RuntimeException("Invalid doctor or doctor is not active");
        }

        List<User> patients = turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId);
        
        List<UUID> patientIds = patients.stream()
                .map(User::getId)
                .collect(Collectors.toList());
        
        Map<UUID, String> latestHistories = medicalHistoryService.getLatestMedicalHistoryContents(patientIds);
        
        Map<UUID, Map<String, Long>> ratingsMap = getRatingsMap(patientIds);
        
        return patients.stream()
                .map(patient -> mapPatientToDTO(patient, latestHistories.get(patient.getId()), ratingsMap.get(patient.getId())))
                .collect(Collectors.toList());
    }

    private Map<UUID, Map<String, Long>> getRatingsMap(List<UUID> patientIds) {
        List<Object[]> results = ratingRepository.countSubcategoriesByRatedIds(patientIds, "DOCTOR");
        Map<UUID, Map<String, Long>> ratingsMap = new java.util.HashMap<>();
        for (Object[] row : results) {
            UUID patientId = (UUID) row[0];
            String subcategoriesString = (String) row[1];
            Long count = (Long) row[2];
            if (subcategoriesString != null && !subcategoriesString.trim().isEmpty()) {
                String[] subcategories = subcategoriesString.split(",");
                for (String subcategory : subcategories) {
                    String trimmed = subcategory.trim();
                    if (!trimmed.isEmpty()) {
                        ratingsMap.computeIfAbsent(patientId, k -> new java.util.HashMap<>()).merge(trimmed, count, Long::sum);
                    }
                }
            }
        }
        return ratingsMap;
    }

    @Transactional
    public void updatePatientMedicalHistory(UUID doctorId, UUID patientId, UUID turnId, String medicalHistory) {
        TurnAssigned turn = turnAssignedRepository.findById(turnId)
                .orElseThrow(() -> new RuntimeException("Turn not found"));

        if (!turn.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Doctor can only update medical history for their own turns");
        }

        if (turn.getPatient() == null || !turn.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Turn does not belong to the specified patient");
        }

        medicalHistoryService.addMedicalHistory(doctorId, turnId, medicalHistory);
    }

    private PatientDTO mapPatientToDTO(User patient, String latestMedicalHistory, Map<String, Long> ratings) {
        List<MedicalHistoryDTO> medicalHistories = medicalHistoryService.getPatientMedicalHistory(patient.getId());
        
        // latestMedicalHistory is now passed as parameter
        
        List<SubcategoryCountDTO> ratingSubcategories = (ratings != null ? ratings : new java.util.HashMap<String, Long>()).entrySet().stream()
                .map(entry -> new SubcategoryCountDTO(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(3)
                .collect(Collectors.toList());
        
        return PatientDTO.builder()
                .id(patient.getId())
                .name(patient.getName())
                .surname(patient.getSurname())
                .email(patient.getEmail())
                .dni(patient.getDni())
                .phone(patient.getPhone())
                .birthdate(patient.getBirthdate())
                .gender(patient.getGender())
                .status(patient.getStatus())
                .medicalHistories(medicalHistories)
                .medicalHistory(latestMedicalHistory)
                .score(patient.getScore())
                .ratingSubcategories(ratingSubcategories)
                .build();
    }

    public DoctorMetricsDTO getDoctorMetrics(UUID doctorId) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        
        if (!"DOCTOR".equals(doctor.getRole())) {
            throw new RuntimeException("User is not a doctor");
        }

        List<TurnAssigned> allTurns = turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId);
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        
        int upcomingTurns = (int) allTurns.stream()
                .filter(turn -> "SCHEDULED".equals(turn.getStatus()) && turn.getScheduledAt().isAfter(now))
                .count();
        
        int completedTurnsThisMonth = (int) allTurns.stream()
                .filter(turn -> "COMPLETED".equals(turn.getStatus()) 
                        && turn.getScheduledAt().isAfter(startOfMonth) 
                        && turn.getScheduledAt().isBefore(now))
                .count();
        
        int cancelledTurns = (int) allTurns.stream()
                .filter(turn -> "CANCELED".equals(turn.getStatus()))
                .count();
        
        List<User> patients = turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId);
        int totalPatients = patients.size();
        
        List<RatingRepository.SubcategoryCount> subcategoryCounts = 
                ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT");
        
        java.util.Map<String, Long> subcategoryMap = new java.util.HashMap<>();
        for (RatingRepository.SubcategoryCount sc : subcategoryCounts) {
            String subcategoriesString = sc.getSubcategory();
            if (subcategoriesString != null && !subcategoriesString.trim().isEmpty()) {
                String[] subcategories = subcategoriesString.split(",");
                for (String subcategory : subcategories) {
                    String trimmed = subcategory.trim();
                    if (!trimmed.isEmpty()) {
                        subcategoryMap.merge(trimmed, sc.getCount(), Long::sum);
                    }
                }
            }
        }
        
        List<SubcategoryCountDTO> ratingSubcategories = subcategoryMap.entrySet().stream()
                .map(entry -> new SubcategoryCountDTO(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
        
        List<Badge> activeBadges = badgeRepository.findByUser_IdAndIsActiveTrue(doctorId);
        
        List<BadgeDTO> badgeDTOs = activeBadges.stream()
                .map(badge -> badgeService.toBadgeDTO(badge, "DOCTOR"))
                .collect(Collectors.toList());
        
        return DoctorMetricsDTO.builder()
                .doctorId(doctor.getId())
                .name(doctor.getName())
                .surname(doctor.getSurname())
                .specialty(doctor.getDoctorProfile() != null ? doctor.getDoctorProfile().getSpecialty() : null)
                .score(doctor.getScore())
                .ratingSubcategories(ratingSubcategories)
                .totalPatients(totalPatients)
                .upcomingTurns(upcomingTurns)
                .completedTurnsThisMonth(completedTurnsThisMonth)
                .cancelledTurns(cancelledTurns)
                .activeBadges(badgeDTOs)
                .totalActiveBadges(badgeDTOs.size())
                .build();
    }
}
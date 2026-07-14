package com.medibook.api.mapper;
import com.medibook.api.dto.Badge.BadgeDTO;
import com.medibook.api.service.BadgeService;
import com.medibook.api.dto.DoctorDTO;
import com.medibook.api.entity.Badge;
import com.medibook.api.entity.User;
import com.medibook.api.repository.BadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DoctorMapper {

    private final BadgeRepository badgeRepository;
    private final BadgeService badgeService;

    public DoctorDTO toDTO(User user) {
        if (user == null || user.getDoctorProfile() == null) {
            return null;
        }
        List<Badge> activeBadges = badgeRepository.findByUser_IdAndIsActiveTrue(user.getId());
        return buildDTO(user, activeBadges);
    }

    /**
     * Batch mapping — loads the active badges for ALL given doctors in a SINGLE
     * query (instead of one {@code findByUser_IdAndIsActiveTrue} per doctor) and maps each.
     */
    public List<DoctorDTO> toDTOList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = users.stream()
                .filter(u -> u != null && u.getDoctorProfile() != null)
                .map(User::getId)
                .collect(Collectors.toList());

        Map<UUID, List<Badge>> badgesByUser = userIds.isEmpty()
                ? Map.of()
                : badgeRepository.findByUser_IdInAndIsActiveTrue(userIds).stream()
                        .collect(Collectors.groupingBy(b -> b.getUser().getId()));

        return users.stream()
                .filter(u -> u != null && u.getDoctorProfile() != null)
                .map(u -> buildDTO(u, badgesByUser.getOrDefault(u.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private DoctorDTO buildDTO(User user, List<Badge> activeBadges) {
        List<BadgeDTO> badgeDTOs = activeBadges.stream()
                .map(badge -> badgeService.toBadgeDTO(badge, "DOCTOR"))
                .collect(Collectors.toList());

        return DoctorDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .surname(user.getSurname())
                .email(user.getEmail())
                .medicalLicense(user.getDoctorProfile().getMedicalLicense())
                .specialty(user.getDoctorProfile().getSpecialty())
                .slotDurationMin(user.getDoctorProfile().getSlotDurationMin())
                .score(user.getScore())
                .activeBadges(badgeDTOs)
                .totalActiveBadges(badgeDTOs.size())
                .build();
    }
}

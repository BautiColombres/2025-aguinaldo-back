package com.medibook.api.mapper;

import com.medibook.api.dto.Turn.TurnCreateRequestDTO;
import com.medibook.api.dto.Turn.TurnResponseDTO;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.TurnFile;
import com.medibook.api.entity.User;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.service.TurnFileService;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TurnAssignedMapper {

    private final RatingRepository ratingRepository;
    private final TurnFileService turnFileService;

    public TurnAssigned toEntity(TurnCreateRequestDTO dto, User doctor) {
        return TurnAssigned.builder()
                .doctor(doctor)
                .motive(dto.getMotive())
                .scheduledAt(dto.getScheduledAt())
                .status("AVAILABLE")
                .build();
    }

    public TurnResponseDTO toDTO(TurnAssigned turn) {
        Set<UUID> raterIds = Collections.emptySet();
        if (turn.getPatient() != null && turn.getDoctor() != null && "COMPLETED".equals(turn.getStatus())) {
            raterIds = new HashSet<>();
            if (ratingRepository.existsByTurnAssigned_IdAndRater_Id(turn.getId(), turn.getPatient().getId())) {
                raterIds.add(turn.getPatient().getId());
            }
            if (ratingRepository.existsByTurnAssigned_IdAndRater_Id(turn.getId(), turn.getDoctor().getId())) {
                raterIds.add(turn.getDoctor().getId());
            }
        }
        TurnFile turnFile = turnFileService.getTurnFileInfo(turn.getId()).orElse(null);
        return buildDTO(turn, raterIds, turnFile);
    }

    /**
     * BBUG-L4: batch mapping — resolves the "already rated" flags and attached files for ALL
     * given turns with TWO queries total (one for ratings, one for files) instead of up to
     * three per row. The single-row {@link #toDTO(TurnAssigned)} API is unchanged.
     */
    public List<TurnResponseDTO> toDTOList(List<TurnAssigned> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }

        Set<UUID> turnIds = turns.stream()
                .map(TurnAssigned::getId)
                .collect(Collectors.toSet());

        Map<UUID, Set<UUID>> ratersByTurn = new HashMap<>();
        for (Object[] row : ratingRepository.findTurnAndRaterIdsByTurnIds(turnIds)) {
            UUID turnId = (UUID) row[0];
            UUID raterId = (UUID) row[1];
            ratersByTurn.computeIfAbsent(turnId, k -> new HashSet<>()).add(raterId);
        }

        Map<UUID, TurnFile> filesByTurn = turnFileService.getTurnFileInfoBatch(turnIds);
        if (filesByTurn == null) {
            filesByTurn = Collections.emptyMap();
        }
        final Map<UUID, TurnFile> filesByTurnFinal = filesByTurn;

        return turns.stream()
                .map(turn -> buildDTO(
                        turn,
                        ratersByTurn.getOrDefault(turn.getId(), Collections.emptySet()),
                        filesByTurnFinal.get(turn.getId())))
                .collect(Collectors.toList());
    }

    private TurnResponseDTO buildDTO(TurnAssigned turn, Set<UUID> raterIds, TurnFile file) {
        boolean isCompleted = "COMPLETED".equals(turn.getStatus());

        boolean needsPatientRating = false;
        boolean needsDoctorRating = false;

        if (turn.getPatient() != null && turn.getDoctor() != null && isCompleted) {
            needsPatientRating = !raterIds.contains(turn.getPatient().getId());
            needsDoctorRating = !raterIds.contains(turn.getDoctor().getId());
        }

        Optional<TurnFile> turnFile = Optional.ofNullable(file);

        return TurnResponseDTO.builder()
                .id(turn.getId())
                .doctorId(turn.getDoctor().getId())
                .doctorName(turn.getDoctor().getName() + " " + turn.getDoctor().getSurname())
                .doctorSpecialty(turn.getDoctor().getDoctorProfile() != null ? 
                    turn.getDoctor().getDoctorProfile().getSpecialty() : null)
                .patientId(turn.getPatient() != null ? turn.getPatient().getId() : null)
                .patientName(turn.getPatient() != null ? turn.getPatient().getName() + " " + turn.getPatient().getSurname() : null)
                .patientScore(turn.getPatient() != null ? turn.getPatient().getScore() : null)
                .scheduledAt(turn.getScheduledAt())
                .motive(turn.getMotive())
                .status(turn.getStatus())
                .needsPatientRating(needsPatientRating)
                .needsDoctorRating(needsDoctorRating)
                .fileUrl(turnFile.map(TurnFile::getFileUrl).orElse(null))
                .fileName(turnFile.map(TurnFile::getFileName).orElse(null))
                .uploadedAt(turnFile.map(TurnFile::getUploadedAt).orElse(null))
                .build();
    }
}

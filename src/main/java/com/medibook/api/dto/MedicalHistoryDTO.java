package com.medibook.api.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalHistoryDTO {
    private UUID id;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID patientId;
    private String patientName;
    private String patientSurname;
    private UUID doctorId;
    private String doctorName;
    private String doctorSurname;
    private UUID turnId;
    private List<String> tags;
}
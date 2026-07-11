package com.medibook.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * DTO for creating medical history entries associated with turns.
 *
 * <p>Tags are validated for count (&le;10) and per-tag length (&le;50) here at
 * the bean-validation layer (OQ-6). Trim/lowercase/dedupe and the server-side
 * character allowlist are applied in {@code MedicalHistoryService}.
 */
@Data
public class CreateMedicalHistoryRequestDTO {
    @NotNull(message = "Turn ID is required")
    private UUID turnId;

    @Size(max = 5000, message = "Medical history content must be less than 5000 characters")
    private String content;

    @Size(max = 10, message = "A medical history entry can have at most 10 tags")
    private List<@Size(max = 50, message = "Each tag must be at most 50 characters") String> tags;
}

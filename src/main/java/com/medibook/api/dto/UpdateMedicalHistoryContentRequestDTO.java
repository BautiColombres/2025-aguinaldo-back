package com.medibook.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateMedicalHistoryContentRequestDTO {
    @Size(max = 5000, message = "Medical history content must be less than 5000 characters")
    private String content;

    @Size(max = 10, message = "A medical history entry can have at most 10 tags")
    private List<@Size(max = 50, message = "Each tag must be at most 50 characters") String> tags;
}

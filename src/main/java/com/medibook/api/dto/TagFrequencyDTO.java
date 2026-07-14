package com.medibook.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single consultation tag with how many times the requesting doctor has
 * applied it to a given patient (per-patient, per-doctor scope).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagFrequencyDTO {
    private String tag;
    private long count;
}

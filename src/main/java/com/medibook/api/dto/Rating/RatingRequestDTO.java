package com.medibook.api.dto.Rating;

import lombok.Getter;
import lombok.Setter;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
public class RatingRequestDTO {
    @NotNull(message = "Score is required")
    @Min(value = 1, message = "Score must be between 1 and 5")
    @Max(value = 5, message = "Score must be between 1 and 5")
    private Integer score;
    
    @JsonAlias({"subcategory", "subcategories"})
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> subcategories;
}

package com.medibook.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CreateFollowUpReminderRequestDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 6, 12})
    void validMonths_passValidation(int months) {
        CreateFollowUpReminderRequestDTO dto = new CreateFollowUpReminderRequestDTO();
        dto.setMonthsUntilControl(months);
        assertTrue(validator.validate(dto).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 5, 7, 24, -3})
    void monthsOutsideAllowedSet_failValidation(int months) {
        CreateFollowUpReminderRequestDTO dto = new CreateFollowUpReminderRequestDTO();
        dto.setMonthsUntilControl(months);
        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test
    void nullMonths_failValidation() {
        CreateFollowUpReminderRequestDTO dto = new CreateFollowUpReminderRequestDTO();
        dto.setMonthsUntilControl(null);
        assertFalse(validator.validate(dto).isEmpty());
    }
}

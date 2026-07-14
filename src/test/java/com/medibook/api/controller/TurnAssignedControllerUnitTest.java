package com.medibook.api.controller;

import com.medibook.api.dto.Availability.AvailableSlotDTO;
import com.medibook.api.entity.User;
import com.medibook.api.service.DoctorAvailabilityService;
import com.medibook.api.service.TurnAssignedService;
import com.medibook.api.repository.TurnAssignedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TurnAssignedController} covering the null-principal case (-> 401)
 * and the available-turns offset derived from ARGENTINA_ZONE, not a hardcoded literal.
 */
@ExtendWith(MockitoExtension.class)
class TurnAssignedControllerUnitTest {

    @Mock
    private TurnAssignedService turnService;
    @Mock
    private DoctorAvailabilityService doctorAvailabilityService;
    @Mock
    private TurnAssignedRepository turnAssignedRepository;

    @InjectMocks
    private TurnAssignedController controller;

    // ---- null principal ----

    @Test
    void cancelTurn_nullAuthentication_returns401() {
        ResponseEntity<Object> response = controller.cancelTurn(UUID.randomUUID(), null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(turnService);
    }

    @Test
    void cancelTurn_authenticationWithNonUserPrincipal_returns401() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        ResponseEntity<Object> response = controller.cancelTurn(UUID.randomUUID(), authentication);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(turnService);
    }

    // ---- available-turns offset ----

    @Test
    void getAvailableTurns_usesArgentinaZoneOffset_notHardcodedMinus03() {
        UUID doctorId = UUID.randomUUID();
        // A date inside a historical Argentina DST window (2008 summer) where the zone
        // offset is NOT -03:00. This makes the test fail against the old hardcoded literal.
        LocalDate dstDate = LocalDate.of(2008, 1, 15);
        LocalTime start = LocalTime.of(10, 0);

        ZoneOffset zoneOffset = ARGENTINA_ZONE.getRules().getOffset(dstDate.atTime(start));
        // Guard: ensure the chosen date actually discriminates the fix from the old literal.
        assertNotEquals(ZoneOffset.of("-03:00"), zoneOffset,
                "test date must have a zone offset different from -03:00 to be meaningful");

        when(doctorAvailabilityService.getAvailableSlots(doctorId, dstDate, dstDate))
                .thenReturn(List.of(new AvailableSlotDTO(dstDate, start, LocalTime.of(10, 30), "TUESDAY")));
        when(turnAssignedRepository.existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(eq(doctorId), any()))
                .thenReturn(false);

        ResponseEntity<List<OffsetDateTime>> response =
                controller.getAvailableTurns(doctorId, dstDate.toString(), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(zoneOffset, response.getBody().get(0).getOffset(),
                "available-turn offset must be derived from ARGENTINA_ZONE");
    }
}

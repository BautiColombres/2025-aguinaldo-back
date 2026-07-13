package com.medibook.api.controller;

import com.medibook.api.mapper.RatingMapper;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.service.TurnAssignedService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * A missing/invalid principal must yield 401, never an NPE -> 500.
 */
@ExtendWith(MockitoExtension.class)
class RatingControllerUnitTest {

    @Mock
    private TurnAssignedService turnService;
    @Mock
    private RatingMapper ratingMapper;
    @Mock
    private RatingRepository ratingRepository;

    @InjectMocks
    private RatingController controller;

    @Test
    void rateTurn_nullAuthentication_returns401() {
        ResponseEntity<Object> response = controller.rateTurn(UUID.randomUUID(), null, null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(turnService);
    }

    @Test
    void rateTurn_authenticationWithNonUserPrincipal_returns401() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        ResponseEntity<Object> response = controller.rateTurn(UUID.randomUUID(), null, authentication, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(turnService);
    }
}

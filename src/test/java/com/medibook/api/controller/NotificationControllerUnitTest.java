package com.medibook.api.controller;

import com.medibook.api.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * A missing "authenticatedUser" request attribute must yield 401, never an
 * NPE -> 500 when the notification endpoints dereference the principal.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerUnitTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private NotificationController controller;

    @Test
    void getNotifications_nullPrincipal_returns401() {
        when(request.getAttribute("authenticatedUser")).thenReturn(null);

        ResponseEntity<Object> response = controller.getNotifications(false, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(notificationService);
    }

    @Test
    void getUnreadCount_nullPrincipal_returns401() {
        when(request.getAttribute("authenticatedUser")).thenReturn(null);

        ResponseEntity<Object> response = controller.getUnreadCount(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(notificationService);
    }

    @Test
    void markAsRead_nullPrincipal_returns401() {
        when(request.getAttribute("authenticatedUser")).thenReturn(null);

        ResponseEntity<Object> response = controller.markAsRead(UUID.randomUUID(), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(notificationService);
    }

    @Test
    void deleteNotification_nullPrincipal_returns401() {
        when(request.getAttribute("authenticatedUser")).thenReturn(null);

        ResponseEntity<Object> response = controller.deleteNotification(UUID.randomUUID(), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(notificationService);
    }
}

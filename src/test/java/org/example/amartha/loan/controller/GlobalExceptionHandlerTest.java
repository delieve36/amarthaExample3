package org.example.amartha.loan.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct handler-method tests — no Spring container needed.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("DataIntegrityViolationException → 409 DATA_CONFLICT (Bug #10)")
    void dataIntegrityViolation_returnsConflict() {
        ResponseEntity<Map<String, Object>> resp =
            handler.handleDataIntegrity(new DataIntegrityViolationException("Duplicate entry '3001' for key 'investor_id'"));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("DATA_CONFLICT", resp.getBody().get("code"));
        assertNotNull(resp.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 BAD_REQUEST")
    void illegalArgument_returnsBadRequest() {
        ResponseEntity<Map<String, Object>> resp =
            handler.handleBadRequest(new IllegalArgumentException("Loan not found: 1"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("BAD_REQUEST", resp.getBody().get("code"));
    }

    @Test
    @DisplayName("IllegalStateException → 409 STATE_CONFLICT")
    void illegalState_returnsConflict() {
        ResponseEntity<Map<String, Object>> resp =
            handler.handleConflict(new IllegalStateException("Cannot invest a loan in DISBURSED state"));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("STATE_CONFLICT", resp.getBody().get("code"));
    }

    @Test
    @DisplayName("Generic Exception → 500 INTERNAL")
    void genericException_returnsInternalServerError() {
        ResponseEntity<Map<String, Object>> resp =
            handler.handleGeneral(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("INTERNAL", resp.getBody().get("code"));
    }
}

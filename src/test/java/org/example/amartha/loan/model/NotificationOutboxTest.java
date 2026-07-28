package org.example.amartha.loan.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationOutbox")
class NotificationOutboxTest {

    @Test
    @DisplayName("createPending → PENDING with correct fields")
    void createPending_shouldSetCorrectFields() {
        var outbox = NotificationOutbox.createPending(100L, 3001L, "alice@example.com", "http://localhost:8080/api/loans/100/agreement");

        assertEquals(100L, outbox.getLoanId());
        assertEquals(3001L, outbox.getInvestorId());
        assertEquals("alice@example.com", outbox.getRecipientEmail());
        assertEquals(NotificationType.AGREEMENT_LETTER, outbox.getType());
        assertEquals(NotificationStatus.PENDING, outbox.getStatus());
        assertEquals("http://localhost:8080/api/loans/100/agreement", outbox.getAgreementUrl());
        assertEquals(0, outbox.getRetryCount());
    }

    @Test
    @DisplayName("markSent → status=SENT, sentDatetime populated")
    void markSent_shouldUpdateStatusAndTimestamp() {
        var outbox = NotificationOutbox.createPending(100L, 3001L, "alice@example.com", "http://example.com");
        outbox.markSent();

        assertEquals(NotificationStatus.SENT, outbox.getStatus());
        assertNotNull(outbox.getSentDatetime());
    }

    @Test
    @DisplayName("markFailed → status=FAILED, errorMessage set, retryCount incremented")
    void markFailed_shouldUpdateStatusAndIncrementRetry() {
        var outbox = NotificationOutbox.createPending(100L, 3001L, "alice@example.com", "http://example.com");
        outbox.markFailed("SMTP connection refused");
        assertEquals(NotificationStatus.FAILED, outbox.getStatus());
        assertEquals("SMTP connection refused", outbox.getErrorMessage());
        assertEquals(1, outbox.getRetryCount());

        outbox.markFailed("timeout");
        assertEquals(2, outbox.getRetryCount());
        assertEquals("timeout", outbox.getErrorMessage());
    }

    @Test
    @DisplayName("id-based equality")
    void equals_shouldUseIdOnly() {
        var a = NotificationOutbox.createPending(1L, 3001L, "a@x.com", "http://a");
        var b = NotificationOutbox.createPending(1L, 3002L, "b@x.com", "http://b");
        a.setId(1L);
        b.setId(1L);

        assertEquals(a, b);
    }
}

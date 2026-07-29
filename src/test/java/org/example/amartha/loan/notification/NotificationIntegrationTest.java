package org.example.amartha.loan.notification;

import org.example.amartha.loan.email.EmailService;
import org.example.amartha.loan.model.*;
import org.example.amartha.loan.repository.InvestorRepository;
import org.example.amartha.loan.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for the notification pipeline:
 * investor → outbox insert → email send → email.log + DB status.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.mode=always",
    "app.notification.email-log-path=${java.io.tmpdir}/email-integration-test.log"
})
@DisplayName("NotificationIntegration")
class NotificationIntegrationTest {

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Autowired
    private EmailService emailService;

    private Path logPath;

    @BeforeEach
    void setUp() {
        logPath = Path.of(System.getProperty("java.io.tmpdir"), "email-integration-test.log");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(logPath);
    }

    // ---- helpers ----

    private Investor createTestInvestor(Long investorId, String email) {
        Investor inv = new Investor();
        inv.setInvestorId(investorId);
        inv.setName("Test-" + investorId);
        inv.setEmailUrl(email);
        inv.setRegisterDate(LocalDate.now());
        return investorRepository.save(inv);
    }

    // ---- tests ----

    @Test
    @DisplayName("full notification pipeline: investor → outbox → email.log → status")
    void fullPipeline() throws IOException {
        // 1. Create investor
        Investor investor = createTestInvestor(9001L, "test9001@example.com");
        assertNotNull(investor);

        // 2. Insert PENDING outbox record
        NotificationOutbox outbox = NotificationOutbox.createPending(
            100L, 9001L, "test9001@example.com",
            "http://localhost:8080/api/loans/agreement/100");
        outbox = outboxRepository.insert(outbox);
        assertNotNull(outbox.getId());
        assertEquals(NotificationStatus.PENDING, outbox.getStatus());

        // 3. Send mock email
        emailService.sendAgreementEmail(
            outbox.getRecipientEmail(),
            "Investor-9001",
            outbox.getLoanId(),
            outbox.getAgreementUrl());

        // 4. Verify email.log was written
        assertTrue(Files.exists(logPath), "email.log should exist after send");
        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.contains("TO=test9001@example.com"), "email.log should contain recipient: " + line);
        assertTrue(line.contains("LOAN=100"), "email.log should contain loan ID: " + line);
        assertTrue(line.startsWith("["), "email.log should have ISO timestamp prefix");

        // 5. Mark sent in outbox
        outbox.markSent();
        outboxRepository.markSent(outbox.getId(), outbox.getSentDatetime());

        // 6. Verify outbox status in DB
        List<NotificationOutbox> records = outboxRepository.findByLoanId(100L);
        assertEquals(1, records.size());
        NotificationOutbox persisted = records.get(0);
        assertEquals(NotificationStatus.SENT, persisted.getStatus());
        assertEquals("test9001@example.com", persisted.getRecipientEmail());
        assertNotNull(persisted.getSentDatetime());
        assertEquals(0, persisted.getRetryCount());

        System.out.println("✅ email.log content: " + line);
    }

    @Test
    @DisplayName("outbox: PENDING → FAILED → retry logic")
    void outboxFailedAndRetry() {
        // Insert
        createTestInvestor(9002L, "test9002@example.com");
        NotificationOutbox outbox = NotificationOutbox.createPending(
            200L, 9002L, "test9002@example.com", "http://localhost:8080/api/loans/agreement/200");
        outbox = outboxRepository.insert(outbox);
        final Long outboxId = outbox.getId();

        // Mark failed
        outbox.markFailed("SMTP connection refused");
        outboxRepository.markFailed(outbox.getId(), outbox.getErrorMessage());

        // Verify
        List<NotificationOutbox> records = outboxRepository.findByLoanId(200L);
        assertEquals(1, records.size());
        assertEquals(NotificationStatus.FAILED, records.get(0).getStatus());
        assertEquals("SMTP connection refused", records.get(0).getErrorMessage());
        assertEquals(1, records.get(0).getRetryCount());

        // Retry eligible (retry_count=1 < max=3)
        List<NotificationOutbox> retryable = outboxRepository.findFailedForRetry(3);
        assertFalse(retryable.isEmpty());
        assertTrue(retryable.stream().anyMatch(r -> r.getId().equals(outboxId)));

        // Retry not eligible after too many failures
        outbox.markFailed("timeout");
        outboxRepository.markFailed(outbox.getId(), outbox.getErrorMessage());
        outbox.markFailed("timeout again");
        outboxRepository.markFailed(outbox.getId(), outbox.getErrorMessage());
        // retry_count=3, not < 3 → excluded
        List<NotificationOutbox> exhausted = outboxRepository.findFailedForRetry(3);
        assertTrue(exhausted.stream().noneMatch(r -> r.getId().equals(outboxId)));
    }

    @Test
    @DisplayName("outbox status guard: markSent on already-SENT record returns 0")
    void markSent_idempotentGuard() {
        createTestInvestor(9003L, "test9003@example.com");
        NotificationOutbox outbox = NotificationOutbox.createPending(
            300L, 9003L, "test9003@example.com", "http://localhost:8080/api/loans/agreement/300");
        outbox = outboxRepository.insert(outbox);

        // First markSent → succeeds
        outbox.markSent();
        int rows1 = outboxRepository.markSent(outbox.getId(), outbox.getSentDatetime());
        assertEquals(1, rows1, "First markSent should affect 1 row");

        // Second markSent on already-SENT → rejected by status guard
        outbox.markSent();
        int rows2 = outboxRepository.markSent(outbox.getId(), outbox.getSentDatetime());
        assertEquals(0, rows2, "Second markSent on SENT record must return 0 — status guard prevents overwrite");
    }

    @Test
    @DisplayName("outbox status guard: markFailed on already-SENT record returns 0")
    void markFailed_onSentReturnsZero() {
        createTestInvestor(9004L, "test9004@example.com");
        NotificationOutbox outbox = NotificationOutbox.createPending(
            400L, 9004L, "test9004@example.com", "http://localhost:8080/api/loans/agreement/400");
        outbox = outboxRepository.insert(outbox);

        // Mark as SENT first
        outbox.markSent();
        outboxRepository.markSent(outbox.getId(), outbox.getSentDatetime());

        // Try markFailed on SENT record → rejected
        outbox.markFailed("should not happen");
        int rows = outboxRepository.markFailed(outbox.getId(), outbox.getErrorMessage());
        assertEquals(0, rows, "markFailed on SENT record must return 0");

        // Verify status still SENT
        List<NotificationOutbox> records = outboxRepository.findByLoanId(400L);
        assertEquals(NotificationStatus.SENT, records.get(0).getStatus());
    }
}

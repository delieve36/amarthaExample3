package org.example.amartha.loan.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.email.EmailService;
import org.example.amartha.loan.model.NotificationOutbox;
import org.example.amartha.loan.repository.NotificationOutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic retry of failed notification sends.
 * <p>Every 5 minutes, scans FAILED outbox records (up to 3 retries)
 * and re-attempts the send. This is a simple best-effort mechanism;
 * for production, consider a proper dead-letter queue with exponential backoff.</p>
 */
@Slf4j
@Component
public class NotificationRetryScheduler {

    private static final int MAX_RETRIES = 3;

    private final NotificationOutboxRepository outboxRepository;
    private final EmailService emailService;

    public NotificationRetryScheduler(NotificationOutboxRepository outboxRepository,
                                       EmailService emailService) {
        this.outboxRepository = outboxRepository;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelay = 300_000) // 5 minutes
    public void retryFailedNotifications() {
        List<NotificationOutbox> failed = outboxRepository.findFailedForRetry(MAX_RETRIES);
        if (failed.isEmpty()) {
            return;
        }
        log.info("Retry scan — found {} failed notifications to retry", failed.size());

        for (NotificationOutbox outbox : failed) {
            try {
                emailService.sendAgreementEmail(
                    outbox.getRecipientEmail(),
                    "Investor-" + outbox.getInvestorId(),
                    outbox.getLoanId(),
                    outbox.getAgreementUrl());
                outbox.markSent();
                outboxRepository.markSent(outbox.getId(), outbox.getSentDatetime());
                log.info("Retry SUCCESS — outbox id={} investor={} loan={}",
                    outbox.getId(), outbox.getInvestorId(), outbox.getLoanId());
            } catch (Exception e) {
                log.error("Retry FAILED — outbox id={} investor={} loan={}: {}",
                    outbox.getId(), outbox.getInvestorId(), outbox.getLoanId(), e.getMessage());
                outbox.markFailed(e.getMessage());
                outboxRepository.markFailed(outbox.getId(), outbox.getErrorMessage());
            }
        }
    }
}

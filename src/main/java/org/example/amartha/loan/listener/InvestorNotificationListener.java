package org.example.amartha.loan.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.email.EmailService;
import org.example.amartha.loan.event.LoanFullyInvestedEvent;
import org.example.amartha.loan.model.Investment;
import org.example.amartha.loan.model.Investor;
import org.example.amartha.loan.model.NotificationOutbox;
import org.example.amartha.loan.repository.InvestorRepository;
import org.example.amartha.loan.repository.NotificationOutboxRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Asynchronously processes {@link LoanFullyInvestedEvent}:
 * <ol>
 *   <li>Looks up investor emails from {@code investors} table</li>
 *   <li>For each investor: inserts a PENDING outbox record, sends email, updates status</li>
 * </ol>
 *
 * <p>Failure of any single email does NOT affect others or the loan state.</p>
 */
@Slf4j
@Component
public class InvestorNotificationListener {

    private final InvestorRepository investorRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final EmailService emailService;

    public InvestorNotificationListener(InvestorRepository investorRepository,
                                         NotificationOutboxRepository outboxRepository,
                                         EmailService emailService) {
        this.investorRepository = investorRepository;
        this.outboxRepository = outboxRepository;
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoanFullyInvested(LoanFullyInvestedEvent event) {
        var loan = event.loan();
        log.info("Received LoanFullyInvestedEvent — loan={} investors={}",
            loan.getId(), loan.getInvestments().size());

        // 1. Collect unique investor IDs
        List<Long> investorIds = loan.getInvestments().stream()
            .map(Investment::getInvestorId)
            .distinct()
            .toList();

        // 2. Look up investor profiles
        List<Investor> investors = investorRepository.findByInvestorIds(investorIds);
        Map<Long, String> emailMap = investors.stream()
            .filter(inv -> inv.getEmailUrl() != null && !inv.getEmailUrl().isBlank())
            .collect(Collectors.toMap(Investor::getInvestorId, Investor::getEmailUrl));

        // 3. Process each investment: insert outbox → send email → update status
        int sent = 0;
        int failed = 0;
        for (var inv : loan.getInvestments()) {
            String email = emailMap.get(inv.getInvestorId());
            if (email == null) {
                log.warn("No email found for investor {} — skipping", inv.getInvestorId());
                continue;
            }

            // 3a. Insert PENDING outbox record
            NotificationOutbox outbox = NotificationOutbox.createPending(
                loan.getId(), inv.getInvestorId(), email, loan.getAgreeLetterUrl());
            outbox = outboxRepository.insert(outbox);

            // 3b. Send email
            try {
                emailService.sendAgreementEmail(
                    outbox.getRecipientEmail(),
                    "Investor-" + outbox.getInvestorId(),
                    outbox.getLoanId(),
                    outbox.getAgreementUrl());
                outbox.markSent();
                outboxRepository.markSent(outbox.getId(), outbox.getSentDatetime());
                sent++;
            } catch (Exception e) {
                log.error("Failed to send email to investor {} (loan {}): {}",
                    outbox.getInvestorId(), loan.getId(), e.getMessage());
                outbox.markFailed(e.getMessage());
                outboxRepository.markFailed(outbox.getId(), outbox.getErrorMessage());
                failed++;
            }
        }

        log.info("Notification complete — loan={} sent={} failed={}",
            loan.getId(), sent, failed);
    }
}

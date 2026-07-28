package org.example.amartha.loan.service;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.event.LoanFullyInvestedEvent;
import org.example.amartha.loan.model.*;
import org.example.amartha.loan.repository.LoanRepository;
import org.example.amartha.loan.state.LoanStateHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Loan lifecycle orchestrator — delegates state transitions to
 * {@link LoanStateHandler} implementations and persists via
 * {@link LoanRepository}.
 */
@Slf4j
@Service
@Transactional
public class LoanService {

    private final LoanRepository loanRepository;
    private final AgreementService agreementService;
    private final ApplicationEventPublisher eventPublisher;

    public LoanService(LoanRepository loanRepository,
                       AgreementService agreementService,
                       ApplicationEventPublisher eventPublisher) {
        this.loanRepository = loanRepository;
        this.agreementService = agreementService;
        this.eventPublisher = eventPublisher;
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    public Loan createLoan(Long borrowerId, String borrowerName, BigDecimal principalAmount,
                           BigDecimal interestRate, BigDecimal roi, String currency) {
        Loan loan = new Loan(borrowerId, principalAmount, interestRate, roi, currency);
        loan.setBorrowerName(borrowerName);
        loan.setInitDatetime(java.time.OffsetDateTime.now());
        loan = loanRepository.save(loan);
        log.info("Loan created: id={} borrower={} amount={} {} state={}",
            loan.getId(), borrowerId, principalAmount, currency, loan.getCurrState());
        return loan;
    }

    @Transactional(readOnly = true)
    public Loan queryLoan(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
    }

    // ========================================================================
    // State transitions
    // ========================================================================

    public Loan approveLoan(Long loanId, Approval approval) {
        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        LoanStateHandler handler = LoanStateHandler.forState(loan.getCurrState());
        loan.setCurrState(handler.approve(loan, approval));

        loanRepository.saveApproval(approval, approval.getValidatorPhotoUrls());
        loan = loanRepository.update(loan);

        log.info("Loan {} approved — persisted approval record", loanId);
        return loan;
    }

    public Loan investLoan(Long loanId, Investment investment) {
        Loan loan = loanRepository.findByIdForUpdate(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        LoanStateHandler handler = LoanStateHandler.forState(loan.getCurrState());
        loan.setCurrState(handler.invest(loan, investment));

        loanRepository.saveInvestment(loanId, investment);

        if (loan.getCurrState() == LoanStateEnum.INVESTED) {
            // 1. Generate agreement letter URL and persist
            String agreementUrl = agreementService.generateAgreementUrl(loan.getId());
            loan.setAgreeLetterUrl(agreementUrl);
            loan.setAgreeLetterSendDatetime(OffsetDateTime.now());

            // 2. Check if all funds received
            boolean allReceived = loan.getInvestments().stream()
                .allMatch(inv -> inv.getFundStatus() == FundStatus.RECEIVED);
            if (allReceived) {
                loan.setFundsReceivedDatetime(OffsetDateTime.now());
            }
        }

        loan = loanRepository.update(loan);

        // 3. Publish event AFTER URL is persisted (async listener handles email)
        if (loan.getCurrState() == LoanStateEnum.INVESTED) {
            eventPublisher.publishEvent(new LoanFullyInvestedEvent(loan));
            log.info("Loan {} — published LoanFullyInvestedEvent for agreement={}",
                loanId, loan.getAgreeLetterUrl());
        }

        log.info("Investment persisted: loan={} investor={} amount={}",
            loanId, investment.getInvestorId(), investment.getAmount());
        return loan;
    }

    public Loan disburseLoan(Long loanId, Disbursement disbursement) {
        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        LoanStateHandler handler = LoanStateHandler.forState(loan.getCurrState());
        loan.setCurrState(handler.disburse(loan, disbursement));

        loanRepository.saveDisbursement(disbursement);
        loan = loanRepository.update(loan);

        log.info("Loan {} disbursed — persisted disbursement record", loanId);
        return loan;
    }

    @Transactional
    public void receiveFunds(Long loanId, Long investmentId) {
        loanRepository.updateInvestmentFundStatus(investmentId, FundStatus.RECEIVED);
        log.info("Investment {} funds marked as RECEIVED", investmentId);

        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        // guard: only set once to avoid race on concurrent receiveFunds calls
        if (loan.getFundsReceivedDatetime() != null) {
            log.debug("Loan {} fundsReceivedDatetime already set — skipping", loanId);
            return;
        }

        boolean allReceived = loan.getInvestments().stream()
            .allMatch(inv -> inv.getFundStatus() == FundStatus.RECEIVED);
        if (allReceived) {
            loan.setFundsReceivedDatetime(java.time.OffsetDateTime.now());
            loanRepository.update(loan);
            log.info("Loan {} — all investments received", loanId);
        }
    }
}

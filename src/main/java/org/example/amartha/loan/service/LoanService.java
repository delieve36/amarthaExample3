package org.example.amartha.loan.service;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.*;
import org.example.amartha.loan.repository.LoanRepository;
import org.example.amartha.loan.state.LoanStateHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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

    public LoanService(LoanRepository loanRepository) {
        this.loanRepository = loanRepository;
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    public Loan createLoan(Long borrowerId, BigDecimal principalAmount,
                           BigDecimal interestRate, BigDecimal roi, String currency) {
        Loan loan = new Loan(borrowerId, principalAmount, interestRate, roi, currency);
        loan.setInitDatetime(java.time.OffsetDateTime.now());
        loan = loanRepository.save(loan);
        log.info("Loan created: id={} borrower={} amount={} {} state={}",
            loan.getId(), borrowerId, principalAmount, currency, loan.getCurrState());
        return loan;
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
        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        LoanStateHandler handler = LoanStateHandler.forState(loan.getCurrState());
        loan.setCurrState(handler.invest(loan, investment));

        loanRepository.saveInvestment(loanId, investment);

        // TODO: if transitioned to INVESTED, generate agreement letter & notify

        loan = loanRepository.update(loan);
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
}

package org.example.amartha.loan.service;

import org.example.amartha.loan.model.*;
import org.example.amartha.loan.repository.LoanRepository;
import org.example.amartha.loan.state.LoanStateHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loan lifecycle orchestrator — delegates state transitions to
 * {@link LoanStateHandler} implementations and persists via
 * {@link LoanRepository}.
 */
@Service
@Transactional
public class LoanService {

    private final LoanRepository loanRepository;

    public LoanService(LoanRepository loanRepository) {
        this.loanRepository = loanRepository;
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

        return loanRepository.update(loan);
    }

    public Loan investLoan(Long loanId, Investment investment) {
        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        LoanStateHandler handler = LoanStateHandler.forState(loan.getCurrState());
        loan.setCurrState(handler.invest(loan, investment));

        loanRepository.saveInvestment(loanId, investment);

        // TODO: if transitioned to INVESTED, generate agreement letter & notify

        return loanRepository.update(loan);
    }

    public Loan disburseLoan(Long loanId, Disbursement disbursement) {
        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        LoanStateHandler handler = LoanStateHandler.forState(loan.getCurrState());
        loan.setCurrState(handler.disburse(loan, disbursement));

        loanRepository.saveDisbursement(disbursement);

        return loanRepository.update(loan);
    }
}

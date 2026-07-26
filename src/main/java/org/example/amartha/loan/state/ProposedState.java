package org.example.amartha.loan.state;

import org.example.amartha.loan.model.*;

/**
 * PROPOSED — initial state. Only {@code approve} is legal.
 */
public final class ProposedState implements LoanStateHandler {

    public static final ProposedState INSTANCE = new ProposedState();

    private ProposedState() {}

    @Override
    public LoanStateEnum approve(Loan loan, Approval approval) {
        // TODO: validate approval fields (photo, employee ID, datetime)
        //       loan.setApproval(approval);
        //       return APPROVED
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public LoanStateEnum invest(Loan loan, Investment investment) {
        // TODO: illegal state — throw IllegalStateException
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public LoanStateEnum disburse(Loan loan, Disbursement disbursement) {
        // TODO: illegal state — throw IllegalStateException
        throw new UnsupportedOperationException("TODO");
    }
}

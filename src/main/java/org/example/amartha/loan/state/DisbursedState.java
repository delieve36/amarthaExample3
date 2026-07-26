package org.example.amartha.loan.state;

import org.example.amartha.loan.model.*;

/**
 * DISBURSED — terminal state. All operations are illegal.
 */
public final class DisbursedState implements LoanStateHandler {

    public static final DisbursedState INSTANCE = new DisbursedState();

    private DisbursedState() {}

    @Override
    public LoanStateEnum approve(Loan loan, Approval approval) {
        // TODO: illegal state — throw IllegalStateException
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

package org.example.amartha.loan.state;

import org.example.amartha.loan.model.*;

/**
 * INVESTED — fully funded. Only {@code disburse} is legal.
 */
public final class InvestedState implements LoanStateHandler {

    public static final InvestedState INSTANCE = new InvestedState();

    private InvestedState() {}

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
        // TODO: validate disbursement fields (agreement URL, officer ID, datetime)
        //       loan.setDisbursement(disbursement);
        //       return DISBURSED
        throw new UnsupportedOperationException("TODO");
    }
}

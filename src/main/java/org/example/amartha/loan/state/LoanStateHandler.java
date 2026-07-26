package org.example.amartha.loan.state;

import org.example.amartha.loan.model.*;

/**
 * State machine contract — every loan state handler must implement
 * these three transition operations.
 *
 * <h3>Adding a new operation</h3>
 * <ol>
 *   <li>Add the method signature here.</li>
 *   <li>Add a default {@code throw} implementation in {@link AbstractLoanState}.</li>
 *   <li>Override it in the concrete states that support it.</li>
 * </ol>
 *
 * <h3>Adding a new state</h3>
 * <ol>
 *   <li>Create a class extending {@link AbstractLoanState}.</li>
 *   <li>Override only the methods that are legal for that state.</li>
 *   <li>Register it in {@link #forState(LoanStateEnum)}.</li>
 * </ol>
 *
 * @see AbstractLoanState
 */
public interface LoanStateHandler {

    LoanStateEnum approve(Loan loan, Approval approval);

    LoanStateEnum invest(Loan loan, Investment investment);

    LoanStateEnum disburse(Loan loan, Disbursement disbursement);

    static LoanStateHandler forState(LoanStateEnum state) {
        return switch (state) {
            case PROPOSED  -> ProposedState.INSTANCE;
            case APPROVED  -> ApprovedState.INSTANCE;
            case INVESTED  -> InvestedState.INSTANCE;
            case DISBURSED -> DisbursedState.INSTANCE;
        };
    }
}

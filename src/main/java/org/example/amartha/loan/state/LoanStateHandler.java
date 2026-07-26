package org.example.amartha.loan.state;

import org.example.amartha.loan.model.*;

/**
 * State Pattern — each implementation handles the allowed operations
 * for a single loan lifecycle stage. All implementations are stateless
 * singletons (immutable, thread-safe).
 */
public interface LoanStateHandler {

    /**
     * Transition from current state → APPROVED.
     *
     * @param loan     the loan being operated on
     * @param approval approval info (photo proof, employee ID, datetime)
     * @return next state after the transition
     * @throws IllegalStateException    if the current state does not allow approval
     * @throws IllegalArgumentException if required approval fields are missing
     */
    LoanStateEnum approve(Loan loan, Approval approval);

    /**
     * Record an investment — may or may not advance the state.
     *
     * @param loan       the loan being invested in
     * @param investment investment record (investor, amount, currency, etc.)
     * @return next state (APPROVED if still underfunded, INVESTED if fully funded)
     * @throws IllegalStateException    if the current state does not accept investments
     * @throws IllegalArgumentException if investment amount is invalid or exceeds remaining principal
     */
    LoanStateEnum invest(Loan loan, Investment investment);

    /**
     * Transition from INVESTED → DISBURSED.
     *
     * @param loan         the loan being disbursed
     * @param disbursement disbursement info (agreement, officer, datetime)
     * @return next state after the transition
     * @throws IllegalStateException    if the current state does not allow disbursement
     * @throws IllegalArgumentException if required disbursement fields are missing
     */
    LoanStateEnum disburse(Loan loan, Disbursement disbursement);

    /**
     * Dispatch to the correct handler for the given state.
     */
    static LoanStateHandler forState(LoanStateEnum state) {
        return switch (state) {
            case PROPOSED  -> ProposedState.INSTANCE;
            case APPROVED  -> ApprovedState.INSTANCE;
            case INVESTED  -> InvestedState.INSTANCE;
            case DISBURSED -> DisbursedState.INSTANCE;
        };
    }
}

package org.example.amartha.loan.state;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.*;

/**
 * Default illegal-transition behaviour for all loan states.
 * Every operation throws {@link IllegalStateException} unless a
 * concrete state overrides it.
 *
 * <p>Concrete states only need to:</p>
 * <ol>
 *   <li>Implement {@link #stateName()} for error messages.</li>
 *   <li>Override the operation(s) they actually permit.</li>
 * </ol>
 */
@Slf4j
public abstract class AbstractLoanState implements LoanStateHandler {

    @Override
    public LoanStateEnum approve(Loan loan, Approval approval) {
        throw illegal("approve", loan.getId());
    }

    @Override
    public LoanStateEnum invest(Loan loan, Investment investment) {
        throw illegal("invest", loan.getId());
    }

    @Override
    public LoanStateEnum disburse(Loan loan, Disbursement disbursement) {
        throw illegal("disburse", loan.getId());
    }

    protected IllegalStateException illegal(String action, Long loanId) {
        log.warn("Illegal transition attempted: {} on {} loan {}", action, stateName(), loanId);
        return new IllegalStateException(
            "Cannot " + action + " a loan in " + stateName() + " state (id=" + loanId + ")");
    }

    protected abstract String stateName();
}

package org.example.amartha.loan.state;

import org.example.amartha.loan.model.*;

/**
 * APPROVED — ready to accept investments. Only {@code invest} is legal.
 * Transitions to INVESTED when total investments equal the principal.
 */
public final class ApprovedState implements LoanStateHandler {

    public static final ApprovedState INSTANCE = new ApprovedState();

    private ApprovedState() {}

    @Override
    public LoanStateEnum approve(Loan loan, Approval approval) {
        // TODO: illegal state — throw IllegalStateException
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public LoanStateEnum invest(Loan loan, Investment investment) {
        // TODO: validate amount > 0, total + amount ≤ principalAmount
        //       loan.getInvestments().add(investment);
        //       BigDecimal total = loan.getInvestments().stream()
        //           .map(Investment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        //       return total.equals(loan.getPrincipalAmount()) ? INVESTED : APPROVED
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public LoanStateEnum disburse(Loan loan, Disbursement disbursement) {
        // TODO: illegal state — throw IllegalStateException
        throw new UnsupportedOperationException("TODO");
    }
}

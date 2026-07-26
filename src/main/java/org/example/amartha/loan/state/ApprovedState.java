package org.example.amartha.loan.state;

import org.example.amartha.loan.model.*;

import java.math.BigDecimal;

/**
 * APPROVED — accepting investments.
 *
 * <p>Legal transition: {@code invest} → INVESTED (when fully funded).</p>
 */
public final class ApprovedState extends AbstractLoanState {

    public static final ApprovedState INSTANCE = new ApprovedState();

    private ApprovedState() {}

    @Override
    protected String stateName() { return "APPROVED"; }

    @Override
    public LoanStateEnum invest(Loan loan, Investment investment) {
        if (investment == null) {
            throw new IllegalArgumentException("Investment must not be null");
        }
        if (investment.getInvestorId() == null) {
            throw new IllegalArgumentException("Investment must include investorId");
        }
        if (investment.getAmount() == null || investment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Investment amount must be positive");
        }

        BigDecimal totalBefore = loan.getInvestments().stream()
            .map(Investment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal newTotal = totalBefore.add(investment.getAmount());

        if (newTotal.compareTo(loan.getPrincipalAmount()) > 0) {
            throw new IllegalArgumentException(
                "Total investment cannot exceed principal. " +
                "Invested: " + totalBefore + ", new: " + investment.getAmount() +
                ", principal: " + loan.getPrincipalAmount());
        }

        investment.setLoanId(loan.getId());
        loan.getInvestments().add(investment);

        if (newTotal.compareTo(loan.getPrincipalAmount()) == 0) {
            return LoanStateEnum.INVESTED;
        }
        return LoanStateEnum.APPROVED;
    }
}

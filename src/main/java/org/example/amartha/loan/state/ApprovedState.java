package org.example.amartha.loan.state;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.*;

import java.math.BigDecimal;

/**
 * APPROVED — accepting investments.
 *
 * <p>Legal transition: {@code invest} → INVESTED (when fully funded).</p>
 */
@Slf4j
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
        if (investment.getCurrency() == null || !investment.getCurrency().equals(loan.getCurrency())) {
            throw new IllegalArgumentException(
                "Investment currency (" + investment.getCurrency() + ") must match loan currency (" + loan.getCurrency() + ")");
        }

        BigDecimal totalBefore = loan.getInvestments().stream()
            .map(Investment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal newTotal = totalBefore.add(investment.getAmount());

        if (newTotal.compareTo(loan.getPrincipalAmount()) > 0) {
            log.error("Investment exceeds principal: loan={} invested={} new={} principal={}",
                loan.getId(), totalBefore, investment.getAmount(), loan.getPrincipalAmount());
            throw new IllegalArgumentException(
                "Total investment cannot exceed principal. " +
                "Invested: " + totalBefore + ", new: " + investment.getAmount() +
                ", principal: " + loan.getPrincipalAmount());
        }

        if (investment.getDatetime() != null
                && investment.getDatetime().isAfter(java.time.OffsetDateTime.now())) {
            log.warn("FUTURE INVESTMENT — investor={} loan={} amount={} scheduled={}",
                    investment.getInvestorId(), loan.getId(),
                    investment.getAmount(), investment.getDatetime());
        }

        investment.setLoanId(loan.getId());
        loan.getInvestments().add(investment);

        if (newTotal.compareTo(loan.getPrincipalAmount()) == 0) {
            log.info("Loan {} fully funded by investor {} ({} total={}) — {} → INVESTED",
                loan.getId(), investment.getInvestorId(), investment.getAmount(),
                newTotal, stateName());
            return LoanStateEnum.INVESTED;
        }
        log.info("Investment recorded: loan={} investor={} amount={} total={}/{}",
            loan.getId(), investment.getInvestorId(), investment.getAmount(),
            newTotal, loan.getPrincipalAmount());
        return LoanStateEnum.APPROVED;
    }

    @Override
    public LoanStateEnum disburse(Loan loan, Disbursement disbursement) {
        BigDecimal invested = loan.getInvestments().stream()
            .map(Investment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        throw new IllegalStateException(
            "Cannot disburse — loan is not fully invested. " +
            "Invested: " + invested + " / " + loan.getPrincipalAmount() +
            " (id=" + loan.getId() + ")");
    }
}

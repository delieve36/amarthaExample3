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
        if (approval == null) {
            throw new IllegalArgumentException("Approval must not be null");
        }
        if (approval.getValidatorEmployeeId() == null) {
            throw new IllegalArgumentException("Approval must include validatorEmployeeId");
        }
        if (approval.getApprovalDatetime() == null) {
            throw new IllegalArgumentException("Approval must include approvalDatetime");
        }
        if (approval.getValidatorPhotoUrls() == null || approval.getValidatorPhotoUrls().isEmpty()) {
            throw new IllegalArgumentException("Approval must include at least one validator photo URL");
        }

        approval.setLoanId(loan.getId());
        loan.setApproval(approval);

        return LoanStateEnum.APPROVED;
    }

    @Override
    public LoanStateEnum invest(Loan loan, Investment investment) {
        throw new IllegalStateException(
            "Cannot invest in a PROPOSED loan (id=" + loan.getId() + "). It must be approved first.");
    }

    @Override
    public LoanStateEnum disburse(Loan loan, Disbursement disbursement) {
        throw new IllegalStateException(
            "Cannot disburse a PROPOSED loan (id=" + loan.getId() + "). It must be approved and invested first.");
    }
}

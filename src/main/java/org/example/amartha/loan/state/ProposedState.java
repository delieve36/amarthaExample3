package org.example.amartha.loan.state;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.*;

/**
 * PROPOSED — ready for approval.
 *
 * <p>Legal transition: {@code approve} → APPROVED.</p>
 */
@Slf4j
public final class ProposedState extends AbstractLoanState {

    public static final ProposedState INSTANCE = new ProposedState();

    private ProposedState() {}

    @Override
    protected String stateName() { return "PROPOSED"; }

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

        log.info("Loan {} approved by employee {} — {} → APPROVED",
            loan.getId(), approval.getValidatorEmployeeId(), stateName());
        return LoanStateEnum.APPROVED;
    }
}

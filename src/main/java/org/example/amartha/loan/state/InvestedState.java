package org.example.amartha.loan.state;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.*;

/**
 * INVESTED — fully funded, ready to disburse.
 *
 * <p>Legal transition: {@code disburse} → DISBURSED.</p>
 */
@Slf4j
public final class InvestedState extends AbstractLoanState {

    public static final InvestedState INSTANCE = new InvestedState();

    private InvestedState() {}

    @Override
    protected String stateName() { return "INVESTED"; }

    @Override
    public LoanStateEnum disburse(Loan loan, Disbursement disbursement) {
        if (disbursement == null) {
            throw new IllegalArgumentException("Disbursement must not be null");
        }
        validateSignedAgreementUrl(disbursement);
        if (disbursement.getFieldOfficerEmployeeId() == null) {
            throw new IllegalArgumentException("Disbursement must include fieldOfficerEmployeeId");
        }
        if (disbursement.getDisbursementDatetime() == null) {
            throw new IllegalArgumentException("Disbursement must include disbursementDatetime");
        }

        disbursement.setLoanId(loan.getId());
        disbursement.setDisbursed(true);
        loan.setDisbursement(disbursement);

        if (disbursement.getDisbursementDatetime().isAfter(java.time.OffsetDateTime.now())) {
            log.warn("FUTURE DISBURSEMENT — would send Kafka message: loan={} officer={} scheduled={}",
                loan.getId(), disbursement.getFieldOfficerEmployeeId(),
                disbursement.getDisbursementDatetime());
        }

        log.info("Loan {} disbursed by officer {} — {} → DISBURSED",
            loan.getId(), disbursement.getFieldOfficerEmployeeId(), stateName());
        return LoanStateEnum.DISBURSED;
    }

    private void validateSignedAgreementUrl(Disbursement disbursement) {
        String url = disbursement.getSignedAgreementUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Disbursement must include signedAgreementUrl");
        }
        String lowered = url.toLowerCase();
        if (!lowered.endsWith(".pdf") && !lowered.endsWith(".jpeg") && !lowered.endsWith(".jpg")) {
            throw new IllegalArgumentException(
                "signedAgreementUrl must be a pdf or jpeg file — got: " + url);
        }
    }
}

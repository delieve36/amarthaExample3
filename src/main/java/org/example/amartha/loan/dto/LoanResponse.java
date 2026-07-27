package org.example.amartha.loan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.amartha.loan.model.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {

    private Long id;
    private Long borrowerId;
    private String borrowerName;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private BigDecimal roi;
    private String currency;
    private LoanStateEnum currState;
    private OffsetDateTime initDatetime;
    private OffsetDateTime agreeLetterSendDatetime;
    private OffsetDateTime fundsReceivedDatetime;
    private String agreeLetterUrl;
    private ApprovalInfo approval;
    private List<InvestmentInfo> investments;
    private DisbursementInfo disbursement;

    public static LoanResponse from(Loan loan) {
        LoanResponse r = new LoanResponse();
        r.setId(loan.getId());
        r.setBorrowerId(loan.getBorrowerId());
        r.setBorrowerName(loan.getBorrowerName());
        r.setPrincipalAmount(loan.getPrincipalAmount());
        r.setInterestRate(loan.getInterestRate());
        r.setRoi(loan.getRoi());
        r.setCurrency(loan.getCurrency());
        r.setCurrState(loan.getCurrState());
        r.setInitDatetime(loan.getInitDatetime());
        r.setAgreeLetterSendDatetime(loan.getAgreeLetterSendDatetime());
        r.setFundsReceivedDatetime(loan.getFundsReceivedDatetime());
        r.setAgreeLetterUrl(loan.getAgreeLetterUrl());
        r.setApproval(loan.getApproval() != null ? ApprovalInfo.from(loan.getApproval()) : null);
        r.setInvestments(loan.getInvestments().stream().map(InvestmentInfo::from).toList());
        r.setDisbursement(loan.getDisbursement() != null ? DisbursementInfo.from(loan.getDisbursement()) : null);
        return r;
    }

    // ---- nested DTOs --------------------------------------------------

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalInfo {
        private Long id;
        private Long validatorEmployeeId;
        private String validatorEmployeeName;
        private OffsetDateTime approvalDatetime;
        private List<String> validatorPhotoUrls;

        static ApprovalInfo from(Approval a) {
            ApprovalInfo info = new ApprovalInfo();
            info.setId(a.getId());
            info.setValidatorEmployeeId(a.getValidatorEmployeeId());
            info.setValidatorEmployeeName(a.getValidatorEmployeeName());
            info.setApprovalDatetime(a.getApprovalDatetime());
            info.setValidatorPhotoUrls(a.getValidatorPhotoUrls());
            return info;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestmentInfo {
        private Long id;
        private Long investorId;
        private String investorName;
        private BigDecimal amount;
        private String currency;
        private OffsetDateTime datetime;
        private FundStatus fundStatus;

        static InvestmentInfo from(Investment i) {
            InvestmentInfo info = new InvestmentInfo();
            info.setId(i.getId());
            info.setInvestorId(i.getInvestorId());
            info.setInvestorName(i.getInvestorName());
            info.setAmount(i.getAmount());
            info.setCurrency(i.getCurrency());
            info.setDatetime(i.getDatetime());
            info.setFundStatus(i.getFundStatus());
            return info;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisbursementInfo {
        private Long id;
        private String signedAgreementUrl;
        private Long fieldOfficerEmployeeId;
        private String fieldOfficerEmployeeName;
        private OffsetDateTime disbursementDatetime;
        private boolean disbursed;

        static DisbursementInfo from(Disbursement d) {
            DisbursementInfo info = new DisbursementInfo();
            info.setId(d.getId());
            info.setSignedAgreementUrl(d.getSignedAgreementUrl());
            info.setFieldOfficerEmployeeId(d.getFieldOfficerEmployeeId());
            info.setFieldOfficerEmployeeName(d.getFieldOfficerEmployeeName());
            info.setDisbursementDatetime(d.getDisbursementDatetime());
            info.setDisbursed(d.isDisbursed());
            return info;
        }
    }
}

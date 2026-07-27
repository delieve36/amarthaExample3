package org.example.amartha.loan.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.dto.*;
import org.example.amartha.loan.model.*;
import org.example.amartha.loan.service.LoanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<LoanResponse> createLoan(@Valid @RequestBody CreateLoanRequest req) {
        log.info("POST /api/loans — {}", req);
        Loan loan = loanService.createLoan(req.getBorrowerId(), req.getBorrowerName(),
            req.getPrincipalAmount(), req.getInterestRate(), req.getRoi(), req.getCurrency());
        return ResponseEntity.status(HttpStatus.CREATED).body(LoanResponse.from(loan));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanResponse> queryLoan(@PathVariable Long id) {
        log.info("GET /api/loans/{}", id);
        Loan loan = loanService.queryLoan(id);
        return ResponseEntity.ok(LoanResponse.from(loan));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<LoanResponse> approveLoan(@PathVariable Long id,
                                                     @Valid @RequestBody ApproveLoanRequest req) {
        log.info("PATCH /api/loans/{}/approve — {}", id, req);
        Approval approval = new Approval();
        approval.setValidatorEmployeeId(req.getValidatorEmployeeId());
        approval.setValidatorEmployeeName(req.getValidatorEmployeeName());
        approval.setApprovalDatetime(req.getApprovalDatetime());
        approval.setValidatorPhotoUrls(req.getValidatorPhotoUrls());

        Loan loan = loanService.approveLoan(id, approval);
        return ResponseEntity.ok(LoanResponse.from(loan));
    }

    @PostMapping("/{id}/investments")
    public ResponseEntity<LoanResponse> invest(@PathVariable Long id,
                                                @Valid @RequestBody InvestRequest req) {
        log.info("POST /api/loans/{}/investments — {}", id, req);
        Investment investment = new Investment();
        investment.setInvestorId(req.getInvestorId());
        investment.setInvestorName(req.getInvestorName());
        investment.setAmount(req.getAmount());
        investment.setCurrency(req.getCurrency());
        investment.setDatetime(req.getDatetime());
        investment.setFundStatus(req.getFundStatus() != null ? req.getFundStatus() : FundStatus.PENDING);

        Loan loan = loanService.investLoan(id, investment);
        return ResponseEntity.ok(LoanResponse.from(loan));
    }

    @PatchMapping("/{loanId}/investments/{investmentId}/receive")
    public ResponseEntity<Void> receiveFunds(@PathVariable Long loanId,
                                              @PathVariable Long investmentId) {
        log.info("PATCH /api/loans/{}/investments/{}/receive", loanId, investmentId);
        loanService.receiveFunds(loanId, investmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/disburse")
    public ResponseEntity<LoanResponse> disburse(@PathVariable Long id,
                                                  @Valid @RequestBody DisburseRequest req) {
        log.info("PATCH /api/loans/{}/disburse — {}", id, req);
        Disbursement disbursement = new Disbursement();
        disbursement.setSignedAgreementUrl(req.getSignedAgreementUrl());
        disbursement.setFieldOfficerEmployeeId(req.getFieldOfficerEmployeeId());
        disbursement.setFieldOfficerEmployeeName(req.getFieldOfficerEmployeeName());
        disbursement.setDisbursementDatetime(req.getDisbursementDatetime());

        Loan loan = loanService.disburseLoan(id, disbursement);
        return ResponseEntity.ok(LoanResponse.from(loan));
    }
}

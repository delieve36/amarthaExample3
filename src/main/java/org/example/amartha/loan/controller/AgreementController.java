package org.example.amartha.loan.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.Loan;
import org.example.amartha.loan.service.AgreementService;
import org.example.amartha.loan.service.LoanService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders agreement letter HTML pages.
 * <p>{@code GET /api/loans/agreement/{id}} — open in browser to view.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/loans")
public class AgreementController {

    private final LoanService loanService;
    private final AgreementService agreementService;

    public AgreementController(LoanService loanService, AgreementService agreementService) {
        this.loanService = loanService;
        this.agreementService = agreementService;
    }

    @GetMapping(value = "/agreement/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewAgreement(@PathVariable Long id) {
        log.info("GET /api/loans/agreement/{}", id);
        Loan loan = loanService.queryLoan(id);
        String html = agreementService.renderAgreement(loan);
        return ResponseEntity.ok(html);
    }
}

package org.example.amartha.loan.service;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.Loan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Generates agreement letter URLs and renders agreement letter HTML.
 */
@Slf4j
@Service
public class AgreementService {

    private final TemplateEngine templateEngine;
    private final String baseUrl;

    public AgreementService(TemplateEngine templateEngine,
                            @Value("${app.agreement.base-url}") String baseUrl) {
        this.templateEngine = templateEngine;
        this.baseUrl = baseUrl;
    }

    /**
     * Generate the agreement letter URL for a loan.
     * <p>The actual HTML page is rendered on-demand by
     * {@link org.example.amartha.loan.controller.AgreementController}.</p>
     */
    public String generateAgreementUrl(Long loanId) {
        return baseUrl + "/api/loans/" + loanId + "/agreement";
    }

    /**
     * Render the agreement letter as HTML using Thymeleaf template.
     */
    public String renderAgreement(Loan loan) {
        Context ctx = new Context();
        ctx.setVariable("loan", loan);
        ctx.setVariable("approval", loan.getApproval());
        ctx.setVariable("investments", loan.getInvestments());
        ctx.setVariable("totalInvested", loan.getInvestments().stream()
            .map(inv -> inv.getAmount())
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        return templateEngine.process("agreement-letter", ctx);
    }
}

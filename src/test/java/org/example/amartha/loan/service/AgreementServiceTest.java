package org.example.amartha.loan.service;

import org.example.amartha.loan.model.Loan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgreementService")
class AgreementServiceTest {

    @Mock
    private TemplateEngine templateEngine;

    private AgreementService service;

    @BeforeEach
    void setUp() {
        service = new AgreementService(templateEngine, "http://localhost:8080");
    }

    @Test
    @DisplayName("generateAgreementUrl → correct URL format")
    void generateAgreementUrl_shouldReturnCorrectUrl() {
        String url = service.generateAgreementUrl(42L);
        assertEquals("http://localhost:8080/api/loans/42/agreement", url);
    }

    @Test
    @DisplayName("renderAgreement → delegates to TemplateEngine with correct template name")
    void renderAgreement_shouldDelegateToTemplateEngine() {
        Loan loan = new Loan(1001L, BigDecimal.valueOf(5000), BigDecimal.TEN, BigDecimal.valueOf(8), "USD");
        loan.setId(1L);
        loan.setBorrowerName("Zhang Wei");
        when(templateEngine.process(eq("agreement-letter"), any(Context.class)))
            .thenReturn("<html>Mock Agreement</html>");

        String result = service.renderAgreement(loan);

        assertEquals("<html>Mock Agreement</html>", result);
        verify(templateEngine).process(eq("agreement-letter"), any(Context.class));
    }
}

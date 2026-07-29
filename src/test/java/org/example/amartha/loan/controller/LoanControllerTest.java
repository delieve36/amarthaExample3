package org.example.amartha.loan.controller;

import org.example.amartha.loan.dto.InvestRequest;
import org.example.amartha.loan.model.FundStatus;
import org.example.amartha.loan.model.Investment;
import org.example.amartha.loan.model.Loan;
import org.example.amartha.loan.model.LoanStateEnum;
import org.example.amartha.loan.service.LoanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanController")
class LoanControllerTest {

    @Mock
    private LoanService loanService;

    @InjectMocks
    private LoanController controller;

    @Test
    @DisplayName("invest: fundStatus forced to PENDING regardless of request (Bug #9)")
    void invest_ignoresUserFundStatus() {
        InvestRequest req = new InvestRequest();
        req.setLoanId(1L);
        req.setInvestorId(3001L);
        req.setAmount(BigDecimal.valueOf(1000));
        req.setCurrency("USD");
        req.setDatetime(OffsetDateTime.now());
        req.setFundStatus(FundStatus.RECEIVED);  // 用户尝试绕过

        Loan loan = new Loan();
        loan.setId(1L);
        loan.setCurrState(LoanStateEnum.APPROVED);
        when(loanService.investLoan(anyLong(), any())).thenReturn(loan);

        controller.invest(req);

        var captor = ArgumentCaptor.forClass(Investment.class);
        verify(loanService).investLoan(anyLong(), captor.capture());
        assertEquals(FundStatus.PENDING, captor.getValue().getFundStatus(),
            "fundStatus must always be PENDING, ignoring user input");
    }

    @Test
    @DisplayName("invest: defaults fundStatus to PENDING when null")
    void invest_defaultsToPending() {
        InvestRequest req = new InvestRequest();
        req.setLoanId(1L);
        req.setInvestorId(3001L);
        req.setAmount(BigDecimal.valueOf(1000));
        req.setCurrency("USD");
        req.setDatetime(OffsetDateTime.now());
        req.setFundStatus(null);  // 未设置

        Loan loan = new Loan();
        loan.setId(1L);
        loan.setCurrState(LoanStateEnum.APPROVED);
        when(loanService.investLoan(anyLong(), any())).thenReturn(loan);

        controller.invest(req);

        var captor = ArgumentCaptor.forClass(Investment.class);
        verify(loanService).investLoan(anyLong(), captor.capture());
        assertEquals(FundStatus.PENDING, captor.getValue().getFundStatus());
    }
}

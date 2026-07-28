package org.example.amartha.loan.service;

import org.example.amartha.loan.event.LoanFullyInvestedEvent;
import org.example.amartha.loan.model.*;
import org.example.amartha.loan.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanService")
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private AgreementService agreementService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LoanService loanService;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, agreementService, eventPublisher);
    }

    // ---- helpers ----

    private Loan approvedLoanWithStateSet() {
        Loan loan = new Loan(1001L, BigDecimal.valueOf(5000), BigDecimal.TEN, BigDecimal.valueOf(8), "USD");
        loan.setId(1L);
        loan.setCurrState(LoanStateEnum.APPROVED);
        loan.setInvestments(new ArrayList<>());
        return loan;
    }

    private Investment validInvestment(long amount) {
        Investment inv = new Investment();
        inv.setInvestorId(3001L);
        inv.setAmount(BigDecimal.valueOf(amount));
        inv.setCurrency("USD");
        inv.setDatetime(OffsetDateTime.now());
        return inv;
    }

    // ---- tests ----

    @Test
    @DisplayName("investLoan: partially funded → stays APPROVED, no event published")
    void investLoan_partial() {
        Loan loan = approvedLoanWithStateSet();
        when(loanRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.update(any())).thenReturn(loan);

        Loan result = loanService.investLoan(1L, validInvestment(3000));

        assertEquals(LoanStateEnum.APPROVED, result.getCurrState());
        assertNull(result.getAgreeLetterUrl());
        verify(eventPublisher, never()).publishEvent(any());
        verify(agreementService, never()).generateAgreementUrl(anyLong());
    }

    @Test
    @DisplayName("investLoan: fully funded → INVESTED, URL generated, event published")
    void investLoan_fullyFunded() {
        Loan loan = approvedLoanWithStateSet();
        when(loanRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.update(any())).thenReturn(loan);
        when(agreementService.generateAgreementUrl(1L))
            .thenReturn("http://localhost:8080/api/loans/1/agreement");

        Loan result = loanService.investLoan(1L, validInvestment(5000));

        assertEquals(LoanStateEnum.INVESTED, result.getCurrState());
        assertEquals("http://localhost:8080/api/loans/1/agreement", result.getAgreeLetterUrl());
        assertNotNull(result.getAgreeLetterSendDatetime());

        var captor = ArgumentCaptor.forClass(LoanFullyInvestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(1L, captor.getValue().loan().getId());
    }

    @Test
    @DisplayName("investLoan: fully funded → all funds received, fundsReceivedDatetime set")
    void investLoan_allFundsReceived() {
        Loan loan = approvedLoanWithStateSet();
        Investment inv = validInvestment(5000);
        inv.setFundStatus(FundStatus.RECEIVED);
        when(loanRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.update(any())).thenReturn(loan);
        when(agreementService.generateAgreementUrl(1L))
            .thenReturn("http://localhost:8080/api/loans/1/agreement");

        Loan result = loanService.investLoan(1L, inv);

        assertNotNull(result.getFundsReceivedDatetime());
    }

    @Test
    @DisplayName("investLoan: loan not found → IllegalArgumentException")
    void investLoan_notFound() {
        when(loanRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> loanService.investLoan(99L, validInvestment(1000)));
    }
}

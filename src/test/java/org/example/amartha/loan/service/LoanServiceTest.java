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
            .thenReturn("http://localhost:8080/api/loans/agreement/1");

        Loan result = loanService.investLoan(1L, validInvestment(5000));

        assertEquals(LoanStateEnum.INVESTED, result.getCurrState());
        assertEquals("http://localhost:8080/api/loans/agreement/1", result.getAgreeLetterUrl());
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
            .thenReturn("http://localhost:8080/api/loans/agreement/1");

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

    // ================================================================
    // approveLoan
    // ================================================================

    private Loan proposedLoan() {
        Loan loan = new Loan(1001L, BigDecimal.valueOf(5000), BigDecimal.TEN, BigDecimal.valueOf(8), "USD");
        loan.setId(1L);
        loan.setCurrState(LoanStateEnum.PROPOSED);
        return loan;
    }

    private Approval validApproval() {
        Approval a = new Approval();
        a.setLoanId(1L);
        a.setValidatorEmployeeId(2001L);
        a.setApprovalDatetime(OffsetDateTime.now());
        a.setValidatorPhotoUrls(java.util.List.of("https://example.com/photo.jpg"));
        return a;
    }

    @Test
    @DisplayName("approveLoan: uses findByIdForUpdate to prevent concurrent duplicate approval")
    void approveLoan_usesFindByIdForUpdate() {
        Loan loan = proposedLoan();
        when(loanRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.update(any())).thenReturn(loan);

        Loan result = loanService.approveLoan(1L, validApproval());

        assertEquals(LoanStateEnum.APPROVED, result.getCurrState());
        verify(loanRepository).findByIdForUpdate(1L);          // Bug #4: 必须有行锁
        verify(loanRepository, never()).findById(anyLong());   // 不能漏锁
        verify(loanRepository).saveApproval(any(), any());
    }

    @Test
    @DisplayName("approveLoan: loan not found → IllegalArgumentException")
    void approveLoan_notFound() {
        when(loanRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> loanService.approveLoan(99L, validApproval()));
    }

    // ================================================================
    // disburseLoan
    // ================================================================

    private Loan investedLoan() {
        Loan loan = new Loan(1001L, BigDecimal.valueOf(5000), BigDecimal.TEN, BigDecimal.valueOf(8), "USD");
        loan.setId(1L);
        loan.setCurrState(LoanStateEnum.INVESTED);
        loan.setInvestments(new ArrayList<>());
        return loan;
    }

    private Disbursement validDisbursement() {
        Disbursement d = new Disbursement();
        d.setLoanId(1L);
        d.setSignedAgreementUrl("https://example.com/agreement.pdf");
        d.setFieldOfficerEmployeeId(4001L);
        d.setDisbursementDatetime(OffsetDateTime.now());
        return d;
    }

    @Test
    @DisplayName("disburseLoan: uses findByIdForUpdate to prevent concurrent duplicate disbursement")
    void disburseLoan_usesFindByIdForUpdate() {
        Loan loan = investedLoan();
        when(loanRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.update(any())).thenReturn(loan);

        Loan result = loanService.disburseLoan(1L, validDisbursement());

        assertEquals(LoanStateEnum.DISBURSED, result.getCurrState());
        verify(loanRepository).findByIdForUpdate(1L);          // Bug #6: 必须有行锁
        verify(loanRepository, never()).findById(anyLong());   // 不能漏锁
        verify(loanRepository).saveDisbursement(any());
    }

    @Test
    @DisplayName("disburseLoan: loan not found → IllegalArgumentException")
    void disburseLoan_notFound() {
        when(loanRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> loanService.disburseLoan(99L, validDisbursement()));
    }

    // ================================================================
    // receiveFunds
    // ================================================================

    @Test
    @DisplayName("receiveFunds: marks investment RECEIVED and sets fundsReceivedDatetime when all done")
    void receiveFunds_allReceived() {
        Loan loan = investedLoan();
        // one investment already RECEIVED in list
        Investment inv = new Investment();
        inv.setId(1L);
        inv.setInvestorId(3001L);
        inv.setFundStatus(FundStatus.RECEIVED);
        loan.getInvestments().add(inv);

        when(loanRepository.updateInvestmentFundStatus(2L, 1L, FundStatus.RECEIVED)).thenReturn(1);
        when(loanRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loan));

        loanService.receiveFunds(1L, 2L);

        assertNotNull(loan.getFundsReceivedDatetime());          // Bug #1: 应该被设置
        verify(loanRepository).findByIdForUpdate(1L);            // Bug #1: 行锁
        verify(loanRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("receiveFunds: skips when fundsReceivedDatetime already set")
    void receiveFunds_alreadySet() {
        Loan loan = investedLoan();
        loan.setFundsReceivedDatetime(OffsetDateTime.now());  // already set

        when(loanRepository.updateInvestmentFundStatus(2L, 1L, FundStatus.RECEIVED)).thenReturn(1);
        when(loanRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loan));

        loanService.receiveFunds(1L, 2L);

        // update() should NOT be called again
        verify(loanRepository, never()).update(any());
    }

    @Test
    @DisplayName("receiveFunds: throws when investment not found or wrong loan (Bug #8)")
    void receiveFunds_investmentNotFound() {
        when(loanRepository.updateInvestmentFundStatus(99L, 1L, FundStatus.RECEIVED)).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
            () -> loanService.receiveFunds(1L, 99L));
        verify(loanRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("receiveFunds: partial — does NOT set fundsReceivedDatetime")
    void receiveFunds_partial() {
        Loan loan = investedLoan();
        Investment received = new Investment();
        received.setId(1L);
        received.setFundStatus(FundStatus.RECEIVED);
        loan.getInvestments().add(received);
        Investment pending = new Investment();
        pending.setId(2L);
        pending.setFundStatus(FundStatus.PENDING);
        loan.getInvestments().add(pending);

        when(loanRepository.updateInvestmentFundStatus(1L, 1L, FundStatus.RECEIVED)).thenReturn(1);
        when(loanRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loan));

        loanService.receiveFunds(1L, 1L);

        assertNull(loan.getFundsReceivedDatetime());  // not all received yet
    }
}

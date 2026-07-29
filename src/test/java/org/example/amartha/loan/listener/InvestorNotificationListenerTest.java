package org.example.amartha.loan.listener;

import org.example.amartha.loan.email.EmailService;
import org.example.amartha.loan.event.LoanFullyInvestedEvent;
import org.example.amartha.loan.model.*;
import org.example.amartha.loan.repository.InvestorRepository;
import org.example.amartha.loan.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestorNotificationListener")
class InvestorNotificationListenerTest {

    @Mock
    private InvestorRepository investorRepository;
    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private EmailService emailService;

    private InvestorNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new InvestorNotificationListener(investorRepository, outboxRepository, emailService);
    }

    // ---- helpers ----

    private Loan investedLoan() {
        Loan loan = new Loan(1001L, BigDecimal.valueOf(5000), BigDecimal.TEN, BigDecimal.valueOf(8), "USD");
        loan.setId(1L);
        loan.setCurrState(LoanStateEnum.INVESTED);
        loan.setAgreeLetterUrl("http://localhost:8080/api/loans/agreement/1");

        Investment inv1 = new Investment();
        inv1.setInvestorId(3001L);
        inv1.setAmount(BigDecimal.valueOf(3000));
        inv1.setCurrency("USD");
        inv1.setDatetime(OffsetDateTime.now());

        Investment inv2 = new Investment();
        inv2.setInvestorId(3002L);
        inv2.setAmount(BigDecimal.valueOf(2000));
        inv2.setCurrency("USD");
        inv2.setDatetime(OffsetDateTime.now());

        loan.setInvestments(new ArrayList<>(List.of(inv1, inv2)));
        return loan;
    }

    private Investor investor(Long investorId, String email) {
        Investor inv = new Investor();
        inv.setInvestorId(investorId);
        inv.setName("Investor-" + investorId);
        inv.setEmailUrl(email);
        inv.setRegisterDate(LocalDate.now());
        return inv;
    }

    // ---- tests ----

    @Test
    @DisplayName("onLoanFullyInvested → sends email to each investor with email")
    void onLoanFullyInvested_shouldSendEmailToEachInvestor() {
        Loan loan = investedLoan();
        when(investorRepository.findByInvestorIds(List.of(3001L, 3002L)))
            .thenReturn(List.of(investor(3001L, "alice@example.com"), investor(3002L, "bob@example.com")));
        when(outboxRepository.insert(any())).thenAnswer(inv -> {
            NotificationOutbox ob = inv.getArgument(0);
            ob.setId(1L);
            return ob;
        });

        listener.onLoanFullyInvested(new LoanFullyInvestedEvent(loan));

        verify(emailService, times(2)).sendAgreementEmail(anyString(), anyString(), eq(1L), anyString());
        verify(outboxRepository, times(2)).insert(any());
        verify(outboxRepository, times(2)).markSent(anyLong(), any());
        verify(outboxRepository, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("onLoanFullyInvested → skips investors without email")
    void onLoanFullyInvested_whenEmailMissing_shouldSkip() {
        Loan loan = investedLoan();
        // Only one investor has an email
        when(investorRepository.findByInvestorIds(List.of(3001L, 3002L)))
            .thenReturn(List.of(investor(3001L, null))); // no email

        listener.onLoanFullyInvested(new LoanFullyInvestedEvent(loan));

        verify(outboxRepository, never()).insert(any());
        verify(emailService, never()).sendAgreementEmail(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("onLoanFullyInvested → marks FAILED when emailService throws")
    void onLoanFullyInvested_whenEmailFails_shouldMarkFailed() {
        Loan loan = investedLoan();
        when(investorRepository.findByInvestorIds(List.of(3001L, 3002L)))
            .thenReturn(List.of(investor(3001L, "alice@example.com"), investor(3002L, "bob@example.com")));
        when(outboxRepository.insert(any())).thenAnswer(inv -> {
            NotificationOutbox ob = inv.getArgument(0);
            ob.setId(1L);
            return ob;
        });
        doThrow(new RuntimeException("SMTP error"))
            .when(emailService).sendAgreementEmail(anyString(), anyString(), anyLong(), anyString());

        listener.onLoanFullyInvested(new LoanFullyInvestedEvent(loan));

        verify(outboxRepository, times(2)).markFailed(anyLong(), anyString());
        verify(outboxRepository, never()).markSent(anyLong(), any());
    }

    @Test
    @DisplayName("onLoanFullyInvested → inserts outbox with correct agreement URL")
    void onLoanFullyInvested_shouldInsertOutboxWithCorrectUrl() {
        Loan loan = investedLoan();
        when(investorRepository.findByInvestorIds(List.of(3001L, 3002L)))
            .thenReturn(List.of(investor(3001L, "alice@example.com"), investor(3002L, "bob@example.com")));

        var captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        when(outboxRepository.insert(captor.capture())).thenAnswer(inv -> {
            NotificationOutbox ob = inv.getArgument(0);
            ob.setId(1L);
            return ob;
        });

        listener.onLoanFullyInvested(new LoanFullyInvestedEvent(loan));

        var inserted = captor.getAllValues();
        assertEquals(2, inserted.size());
        inserted.forEach(ob -> {
            assertEquals(1L, ob.getLoanId());
            assertEquals(NotificationType.AGREEMENT_LETTER, ob.getType());
            assertEquals("http://localhost:8080/api/loans/agreement/1", ob.getAgreementUrl());
        });
    }

    @Test
    @DisplayName("onLoanFullyInvested → deduplicates by investorId (Bank1×2 → 1 email)")
    void onLoanFullyInvested_dedupByInvestor() {
        Loan loan = new Loan(1001L, BigDecimal.valueOf(5000), BigDecimal.TEN, BigDecimal.valueOf(8), "USD");
        loan.setId(2L);
        loan.setCurrState(LoanStateEnum.INVESTED);
        loan.setAgreeLetterUrl("http://localhost:8080/api/loans/agreement/2");

        // Bank1 invests twice, Bank2 once → 3 investments, but only 2 unique investors
        Investment inv1 = new Investment();
        inv1.setInvestorId(3001L); inv1.setAmount(BigDecimal.valueOf(3000)); inv1.setCurrency("USD");
        Investment inv2 = new Investment();
        inv2.setInvestorId(3001L); inv2.setAmount(BigDecimal.valueOf(1000)); inv2.setCurrency("USD");
        Investment inv3 = new Investment();
        inv3.setInvestorId(3002L); inv3.setAmount(BigDecimal.valueOf(1000)); inv3.setCurrency("USD");
        loan.setInvestments(new ArrayList<>(List.of(inv1, inv2, inv3)));

        when(investorRepository.findByInvestorIds(List.of(3001L, 3002L)))
            .thenReturn(List.of(investor(3001L, "bank1@example.com"), investor(3002L, "bank2@example.com")));
        when(outboxRepository.insert(any())).thenAnswer(inv -> {
            NotificationOutbox ob = inv.getArgument(0);
            ob.setId(1L);
            return ob;
        });

        listener.onLoanFullyInvested(new LoanFullyInvestedEvent(loan));

        // Only 2 emails, not 3
        verify(emailService, times(2)).sendAgreementEmail(anyString(), anyString(), eq(2L), anyString());
        verify(outboxRepository, times(2)).insert(any());
    }
}

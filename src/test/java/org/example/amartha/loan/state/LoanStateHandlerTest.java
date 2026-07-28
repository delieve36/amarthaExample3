package org.example.amartha.loan.state;

import org.example.amartha.loan.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests — no Spring container, no database.
 * Each state handler is tested in isolation.
 */
@DisplayName("LoanStateHandler")
class LoanStateHandlerTest {

    // ---- helpers ----------------------------------------------------------

    private static Loan newLoan(long principal) {
        return new Loan(1001L, BigDecimal.valueOf(principal), BigDecimal.TEN, BigDecimal.valueOf(8), "USD");
    }

    private static Approval validApproval() {
        Approval a = new Approval();
        a.setValidatorEmployeeId(2001L);
        a.setApprovalDatetime(OffsetDateTime.now());
        a.setValidatorPhotoUrls(List.of("https://example.com/photo.jpg"));
        return a;
    }

    private static Investment validInvestment(long amount) {
        Investment i = new Investment();
        i.setInvestorId(3001L);
        i.setAmount(BigDecimal.valueOf(amount));
        i.setCurrency("USD");
        i.setDatetime(OffsetDateTime.now());
        return i;
    }

    private static Disbursement validDisbursement() {
        Disbursement d = new Disbursement();
        d.setSignedAgreementUrl("https://example.com/agreement.pdf");
        d.setFieldOfficerEmployeeId(4001L);
        d.setDisbursementDatetime(OffsetDateTime.now());
        return d;
    }

    private static Loan approvedLoan(long principal) {
        Loan loan = newLoan(principal);
        LoanStateHandler.forState(loan.getCurrState()).approve(loan, validApproval());
        return loan;
    }

    // ========================================================================

    @Nested
    @DisplayName("ProposedState")
    class ProposedStateTests {

        @Test
        @DisplayName("approve → APPROVED")
        void approve_success() {
            Loan loan = newLoan(5000);
            LoanStateEnum next = LoanStateHandler.forState(LoanStateEnum.PROPOSED)
                .approve(loan, validApproval());

            loan.setCurrState(next);
            assertEquals(LoanStateEnum.APPROVED, loan.getCurrState());
            assertNotNull(loan.getApproval());
        }

        @Test
        @DisplayName("approve with missing employeeId → IllegalArgumentException")
        void approve_missingEmployeeId() {
            Loan loan = newLoan(5000);
            Approval a = validApproval();
            a.setValidatorEmployeeId(null);

            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.PROPOSED).approve(loan, a));
        }

        @Test
        @DisplayName("approve with null approval → IllegalArgumentException")
        void approve_nullApproval() {
            Loan loan = newLoan(5000);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.PROPOSED).approve(loan, null));
        }

        @Test
        @DisplayName("approve with missing photos → IllegalArgumentException")
        void approve_missingPhotos() {
            Loan loan = newLoan(5000);
            Approval a = validApproval();
            a.setValidatorPhotoUrls(null);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.PROPOSED).approve(loan, a));
        }

        @Test
        @DisplayName("approve with empty photos → IllegalArgumentException")
        void approve_emptyPhotos() {
            Loan loan = newLoan(5000);
            Approval a = validApproval();
            a.setValidatorPhotoUrls(List.of());
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.PROPOSED).approve(loan, a));
        }

        @Test
        @DisplayName("approve with missing datetime → IllegalArgumentException")
        void approve_missingDatetime() {
            Loan loan = newLoan(5000);
            Approval a = validApproval();
            a.setApprovalDatetime(null);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.PROPOSED).approve(loan, a));
        }

        @Test
        @DisplayName("invest → IllegalStateException")
        void invest_throws() {
            Loan loan = newLoan(5000);
            assertThrows(IllegalStateException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.PROPOSED).invest(loan, validInvestment(1000)));
        }

        @Test
        @DisplayName("disburse → IllegalStateException")
        void disburse_throws() {
            Loan loan = newLoan(5000);
            assertThrows(IllegalStateException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.PROPOSED).disburse(loan, validDisbursement()));
        }
    }

    @Nested
    @DisplayName("ApprovedState")
    class ApprovedStateTests {

        @Test
        @DisplayName("partial investment → stays APPROVED")
        void invest_partial() {
            Loan loan = approvedLoan(5000);
            LoanStateEnum next = LoanStateHandler.forState(LoanStateEnum.APPROVED)
                .invest(loan, validInvestment(3000));

            loan.setCurrState(next);
            assertEquals(LoanStateEnum.APPROVED, loan.getCurrState());
            assertEquals(1, loan.getInvestments().size());
        }

        @Test
        @DisplayName("multiple investments sum to principal → INVESTED")
        void invest_fullyFunded() {
            Loan loan = approvedLoan(5000);
            LoanStateHandler handler = LoanStateHandler.forState(LoanStateEnum.APPROVED);

            loan.setCurrState(handler.invest(loan, validInvestment(2000)));
            loan.setCurrState(handler.invest(loan, validInvestment(2000)));
            loan.setCurrState(handler.invest(loan, validInvestment(1000)));

            assertEquals(LoanStateEnum.INVESTED, loan.getCurrState());
            assertEquals(3, loan.getInvestments().size());
        }

        @Test
        @DisplayName("single investment equals principal → INVESTED")
        void invest_singleFullAmount() {
            Loan loan = approvedLoan(5000);
            loan.setCurrState(LoanStateHandler.forState(LoanStateEnum.APPROVED)
                .invest(loan, validInvestment(5000)));

            assertEquals(LoanStateEnum.INVESTED, loan.getCurrState());
        }

        @Test
        @DisplayName("investment exceeds remaining principal → IllegalArgumentException")
        void invest_overflow() {
            Loan loan = approvedLoan(5000);
            LoanStateHandler handler = LoanStateHandler.forState(LoanStateEnum.APPROVED);
            loan.setCurrState(handler.invest(loan, validInvestment(4000)));

            assertThrows(IllegalArgumentException.class,
                () -> handler.invest(loan, validInvestment(2000)));
            assertEquals(LoanStateEnum.APPROVED, loan.getCurrState());
        }

        @Test
        @DisplayName("approve on approved → IllegalStateException")
        void approve_throws() {
            Loan loan = approvedLoan(5000);
            assertThrows(IllegalStateException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.APPROVED).approve(loan, validApproval()));
        }

        @Test
        @DisplayName("invest with null investorId → IllegalArgumentException")
        void invest_nullInvestorId() {
            Loan loan = approvedLoan(5000);
            Investment inv = validInvestment(1000);
            inv.setInvestorId(null);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.APPROVED).invest(loan, inv));
        }

        @Test
        @DisplayName("invest with zero amount → IllegalArgumentException")
        void invest_zeroAmount() {
            Loan loan = approvedLoan(5000);
            Investment inv = validInvestment(0);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.APPROVED).invest(loan, inv));
        }

        @Test
        @DisplayName("invest with null amount → IllegalArgumentException")
        void invest_nullAmount() {
            Loan loan = approvedLoan(5000);
            Investment inv = validInvestment(1000);
            inv.setAmount(null);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.APPROVED).invest(loan, inv));
        }

        @Test
        @DisplayName("invest equals remaining principal → INVESTED")
        void invest_exactRemaining() {
            Loan loan = approvedLoan(5000);
            LoanStateHandler handler = LoanStateHandler.forState(LoanStateEnum.APPROVED);
            loan.setCurrState(handler.invest(loan, validInvestment(3000)));
            // invest exactly the remaining 2000
            loan.setCurrState(handler.invest(loan, validInvestment(2000)));
            assertEquals(LoanStateEnum.INVESTED, loan.getCurrState());
        }

        @Test
        @DisplayName("disburse on approved → IllegalStateException")
        void disburse_throws() {
            Loan loan = approvedLoan(5000);
            assertThrows(IllegalStateException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.APPROVED).disburse(loan, validDisbursement()));
        }

        @Test
        @DisplayName("invest with currency mismatch → IllegalArgumentException")
        void invest_currencyMismatch() {
            Loan loan = approvedLoan(5000);
            Investment inv = validInvestment(1000);
            inv.setCurrency("IDR");
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.APPROVED).invest(loan, inv));
        }

        @Test
        @DisplayName("invest with null currency → IllegalArgumentException")
        void invest_nullCurrency() {
            Loan loan = approvedLoan(5000);
            Investment inv = validInvestment(1000);
            inv.setCurrency(null);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.APPROVED).invest(loan, inv));
        }
    }

    @Nested
    @DisplayName("InvestedState")
    class InvestedStateTests {

        private Loan investedLoan() {
            Loan loan = approvedLoan(5000);
            LoanStateHandler handler = LoanStateHandler.forState(LoanStateEnum.APPROVED);
            loan.setCurrState(handler.invest(loan, validInvestment(5000)));
            return loan;
        }

        @Test
        @DisplayName("disburse → DISBURSED")
        void disburse_success() {
            Loan loan = investedLoan();
            LoanStateEnum next = LoanStateHandler.forState(LoanStateEnum.INVESTED)
                .disburse(loan, validDisbursement());

            loan.setCurrState(next);
            assertEquals(LoanStateEnum.DISBURSED, loan.getCurrState());
            assertNotNull(loan.getDisbursement());
        }

        @Test
        @DisplayName("invest after fully funded → IllegalStateException")
        void invest_throws() {
            Loan loan = investedLoan();
            assertThrows(IllegalStateException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.INVESTED).invest(loan, validInvestment(1)));
        }

        @Test
        @DisplayName("approve on invested → IllegalStateException")
        void approve_throws() {
            Loan loan = investedLoan();
            assertThrows(IllegalStateException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.INVESTED).approve(loan, validApproval()));
        }

        @Test
        @DisplayName("disburse with null agreement URL → IllegalArgumentException")
        void disburse_missingAgreementUrl() {
            Loan loan = investedLoan();
            Disbursement d = validDisbursement();
            d.setSignedAgreementUrl(null);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.INVESTED).disburse(loan, d));
        }

        @Test
        @DisplayName("disburse with blank agreement URL → IllegalArgumentException")
        void disburse_blankAgreementUrl() {
            Loan loan = investedLoan();
            Disbursement d = validDisbursement();
            d.setSignedAgreementUrl("  ");
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.INVESTED).disburse(loan, d));
        }

        @Test
        @DisplayName("disburse with null officer ID → IllegalArgumentException")
        void disburse_missingOfficerId() {
            Loan loan = investedLoan();
            Disbursement d = validDisbursement();
            d.setFieldOfficerEmployeeId(null);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.INVESTED).disburse(loan, d));
        }

        @Test
        @DisplayName("disburse with null datetime → IllegalArgumentException")
        void disburse_missingDatetime() {
            Loan loan = investedLoan();
            Disbursement d = validDisbursement();
            d.setDisbursementDatetime(null);
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.INVESTED).disburse(loan, d));
        }

        @Test
        @DisplayName("disburse with non-pdf/jpeg file type → IllegalArgumentException")
        void disburse_invalidFileType() {
            Loan loan = investedLoan();
            Disbursement d = validDisbursement();
            d.setSignedAgreementUrl("https://example.com/agreement.txt");
            assertThrows(IllegalArgumentException.class,
                () -> LoanStateHandler.forState(LoanStateEnum.INVESTED).disburse(loan, d));
        }
    }

    @Nested
    @DisplayName("DisbursedState")
    class DisbursedStateTests {

        @Test
        @DisplayName("all operations throw IllegalStateException")
        void allOperationsTerminal() {
            LoanStateHandler handler = LoanStateHandler.forState(LoanStateEnum.DISBURSED);
            Loan loan = newLoan(5000);

            assertThrows(IllegalStateException.class,
                () -> handler.approve(loan, validApproval()));
            assertThrows(IllegalStateException.class,
                () -> handler.invest(loan, validInvestment(1000)));
            assertThrows(IllegalStateException.class,
                () -> handler.disburse(loan, validDisbursement()));
        }
    }
}

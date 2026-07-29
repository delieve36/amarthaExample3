package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests — real H2 in-memory database.
 * Covers Repository-layer bugs that Mockito unit tests cannot catch.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.mode=always"
})
@Transactional
@DisplayName("LoanRepository")
class LoanRepositoryTest {

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private InvestorRepository investorRepository;

    // ---- helpers ----

    private Loan createTestLoan() {
        Loan loan = new Loan(1001L, BigDecimal.valueOf(5000), BigDecimal.TEN, BigDecimal.valueOf(8), "USD");
        loan.setBorrowerName("Test Borrower");
        loan.setCurrState(LoanStateEnum.APPROVED);
        loan.setInitDatetime(OffsetDateTime.now());
        return loanRepository.save(loan);
    }

    // ---- tests ----

    @Test
    @DisplayName("saveDisbursement: sets database-generated ID (Bug #7)")
    void saveDisbursement_setsId() {
        Loan loan = createTestLoan();

        Disbursement d = new Disbursement();
        d.setLoanId(loan.getId());
        d.setSignedAgreementUrl("https://example.com/agreement.pdf");
        d.setFieldOfficerEmployeeId(4001L);
        d.setDisbursementDatetime(OffsetDateTime.now());
        d.setDisbursed(true);

        loanRepository.saveDisbursement(d);

        assertNotNull(d.getId(), "Disbursement ID must be set after save (Bug #7)");
        assertNotNull(d.getGmtCreate());
        assertNotNull(d.getGmtModify());
    }

    @Test
    @DisplayName("updateInvestmentFundStatus: returns 1 for valid investment (Bug #8)")
    void updateInvestmentFundStatus_returnsRows() {
        Loan loan = createTestLoan();

        Investment inv = new Investment();
        inv.setInvestorId(3001L);
        inv.setAmount(BigDecimal.valueOf(2000));
        inv.setCurrency("USD");
        inv.setDatetime(OffsetDateTime.now());
        inv.setFundStatus(FundStatus.PENDING);
        loanRepository.saveInvestment(loan.getId(), inv);

        int rows = loanRepository.updateInvestmentFundStatus(inv.getId(), loan.getId(), FundStatus.RECEIVED);
        assertEquals(1, rows, "Should affect exactly 1 row");

        // Verify the update actually persisted
        var investments = loanRepository.findInvestmentsByLoanId(loan.getId());
        assertEquals(FundStatus.RECEIVED, investments.get(0).getFundStatus());
    }

    @Test
    @DisplayName("updateInvestmentFundStatus: returns 0 for wrong loanId (Bug #2 + #8)")
    void updateInvestmentFundStatus_wrongLoan_returnsZero() {
        Loan loan = createTestLoan();

        Investment inv = new Investment();
        inv.setInvestorId(3001L);
        inv.setAmount(BigDecimal.valueOf(2000));
        inv.setCurrency("USD");
        inv.setDatetime(OffsetDateTime.now());
        inv.setFundStatus(FundStatus.PENDING);
        loanRepository.saveInvestment(loan.getId(), inv);

        // Try to update with wrong loanId
        int rows = loanRepository.updateInvestmentFundStatus(inv.getId(), 999L, FundStatus.RECEIVED);
        assertEquals(0, rows, "Should affect 0 rows — investment does not belong to loan 999");

        // Original investment should still be PENDING
        var investments = loanRepository.findInvestmentsByLoanId(loan.getId());
        assertEquals(FundStatus.PENDING, investments.get(0).getFundStatus());
    }

    @Test
    @DisplayName("InvestorRepository.save: sets database-generated ID (Bug #5)")
    void investorSave_setsId() {
        Investor investor = new Investor();
        investor.setInvestorId(5001L);
        investor.setName("Test Investor");
        investor.setEmailUrl("test@example.com");
        investor.setRegisterDate(java.time.LocalDate.now());

        Investor saved = investorRepository.save(investor);

        assertNotNull(saved.getId(), "Investor ID must be set after save (Bug #5)");
        assertEquals(5001L, saved.getInvestorId());
    }
}

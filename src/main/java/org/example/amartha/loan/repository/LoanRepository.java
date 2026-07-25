package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.Approval;
import org.example.amartha.loan.model.Disbursement;
import org.example.amartha.loan.model.Investment;
import org.example.amartha.loan.model.Loan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loan data-access layer — plain JdbcTemplate for full SQL visibility.
 */
@Repository
public class LoanRepository {

    private final JdbcTemplate jdbc;
    private final LoanRowMapper loanRowMapper;
    private final ApprovalRowMapper approvalRowMapper;
    private final InvestmentRowMapper investmentRowMapper;
    private final DisbursementRowMapper disbursementRowMapper;

    public LoanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.loanRowMapper = new LoanRowMapper();
        this.approvalRowMapper = new ApprovalRowMapper();
        this.investmentRowMapper = new InvestmentRowMapper();
        this.disbursementRowMapper = new DisbursementRowMapper();
    }

    // ================================================================
    // Queries
    // ================================================================

    public List<Loan> findAll() {
        String sql = "SELECT * FROM loans ORDER BY id";
        return jdbc.query(sql, loanRowMapper);
    }

    public Optional<Loan> findById(Long id) {
        String sql = "SELECT * FROM loans WHERE id = ?";
        List<Loan> result = jdbc.query(sql, loanRowMapper, id);
        if (result.isEmpty()) return Optional.empty();

        Loan loan = result.get(0);

        // eager: load approval (including photos)
        findApprovalByLoanId(id).ifPresent(loan::setApproval);

        // eager: load investments
        loan.setInvestments(findInvestmentsByLoanId(id));

        // eager: load disbursement
        findDisbursementByLoanId(id).ifPresent(loan::setDisbursement);

        return Optional.of(loan);
    }

    private Optional<Approval> findApprovalByLoanId(Long loanId) {
        String sql = "SELECT * FROM approvals WHERE loan_id = ?";
        List<Approval> results = jdbc.query(sql, approvalRowMapper, loanId);
        if (results.isEmpty()) return Optional.empty();
        Approval approval = results.get(0);
        approval.setValidatorPhotoUrls(findPhotosByLoanId(loanId));
        return Optional.of(approval);
    }

    public List<String> findPhotosByLoanId(Long loanId) {
        String sql = """
            SELECT ap.photo_url
            FROM approval_photos ap
            JOIN approvals a ON a.id = ap.approval_id
            WHERE a.loan_id = ?
            """;
        return jdbc.queryForList(sql, String.class, loanId);
    }

    public List<Investment> findInvestmentsByLoanId(Long loanId) {
        String sql = "SELECT * FROM investments WHERE loan_id = ?";
        return jdbc.query(sql, investmentRowMapper, loanId);
    }

    private Optional<Disbursement> findDisbursementByLoanId(Long loanId) {
        String sql = "SELECT * FROM disbursements WHERE loan_id = ?";
        List<Disbursement> results = jdbc.query(sql, disbursementRowMapper, loanId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // ================================================================
    // Mutations
    // ================================================================

    @Transactional
    public Loan save(Loan loan) {
        String sql = """
            INSERT INTO loans (borrower_id, borrower_name, principal_amount,
                               interest_rate, roi, currency, curr_state,
                               init_datetime, agree_letter_send_datetime,
                               funds_received_datetime, agree_letter_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, loan.getBorrowerId());
            ps.setString(2, loan.getBorrowerName());
            ps.setBigDecimal(3, loan.getPrincipalAmount());
            ps.setBigDecimal(4, loan.getInterestRate());
            ps.setBigDecimal(5, loan.getRoi());
            ps.setString(6, loan.getCurrency());
            ps.setString(7, loan.getCurrState().name());
            ps.setObject(8, loan.getInitDatetime());
            ps.setObject(9, loan.getAgreeLetterSendDatetime());
            ps.setObject(10, loan.getFundsReceivedDatetime());
            ps.setString(11, loan.getAgreeLetterUrl());
            return ps;
        });
        return loan;
    }

    @Transactional
    public Loan update(Loan loan) {
        String sql = """
            UPDATE loans SET borrower_id = ?, borrower_name = ?,
                             principal_amount = ?, interest_rate = ?, roi = ?,
                             currency = ?, curr_state = ?,
                             init_datetime = ?, agree_letter_send_datetime = ?,
                             funds_received_datetime = ?, agree_letter_url = ?
            WHERE id = ?
            """;
        jdbc.update(sql,
            loan.getBorrowerId(), loan.getBorrowerName(),
            loan.getPrincipalAmount(), loan.getInterestRate(), loan.getRoi(),
            loan.getCurrency(), loan.getCurrState().name(),
            loan.getInitDatetime(), loan.getAgreeLetterSendDatetime(),
            loan.getFundsReceivedDatetime(), loan.getAgreeLetterUrl(),
            loan.getId());
        return loan;
    }

    @Transactional
    public void saveInvestment(Long loanId, Investment investment) {
        String sql = """
            INSERT INTO investments (loan_id, investor_id, investor_name,
                                     amount, currency, datetime, fund_status)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        jdbc.update(sql,
            loanId, investment.getInvestorId(), investment.getInvestorName(),
            investment.getAmount(), investment.getCurrency(),
            investment.getDatetime(), investment.isFundReceived());
    }

    @Transactional
    public void saveApproval(Approval approval, List<String> photoUrls) {
        String insertApproval = """
            INSERT INTO approvals (loan_id, validator_employee_id,
                                   validator_employee_name, approval_datetime)
            VALUES (?, ?, ?, ?)
            """;
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertApproval, new String[]{"id"});
            ps.setLong(1, approval.getLoanId());
            ps.setString(2, approval.getValidatorEmployeeId());
            ps.setString(3, approval.getValidatorEmployeeName());
            ps.setObject(4, approval.getApprovalDatetime());
            return ps;
        });

        if (photoUrls != null && !photoUrls.isEmpty()) {
            String insertPhoto = "INSERT INTO approval_photos (approval_id, photo_url) VALUES (?, ?)";
            for (String url : photoUrls) {
                jdbc.update(insertPhoto, approval.getLoanId(), url);
            }
        }
    }

    @Transactional
    public void saveDisbursement(Disbursement disbursement) {
        String sql = """
            INSERT INTO disbursements (loan_id, signed_agreement_url,
                                       field_officer_employee_id,
                                       field_officer_employee_name,
                                       disbursement_datetime, status)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbc.update(sql,
            disbursement.getLoanId(), disbursement.getSignedAgreementUrl(),
            disbursement.getFieldOfficerEmployeeId(),
            disbursement.getFieldOfficerEmployeeName(),
            disbursement.getDisbursementDatetime(), disbursement.isSuccess());
    }
}

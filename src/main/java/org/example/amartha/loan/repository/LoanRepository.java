package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.Approval;
import org.example.amartha.loan.model.Disbursement;
import org.example.amartha.loan.model.FundStatus;
import org.example.amartha.loan.model.Investment;
import org.example.amartha.loan.model.Loan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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

    /**
     * Pessimistic-lock read for investment flow — prevents concurrent over-investment.
     * <p>Uses {@code SELECT ... FOR UPDATE} to serialize investment writes
     * on the same loan row. Must be called inside an active transaction.</p>
     */
    public Optional<Loan> findByIdForUpdate(Long id) {
        String sql = "SELECT * FROM loans WHERE id = ? FOR UPDATE";
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
            INSERT INTO loans (gmt_create, gmt_modify,
                               borrower_id, borrower_name, principal_amount,
                               interest_rate, roi, currency, curr_state,
                               init_datetime, agree_letter_send_datetime,
                               funds_received_datetime, agree_letter_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setLong(3, loan.getBorrowerId());
            ps.setString(4, loan.getBorrowerName());
            ps.setBigDecimal(5, loan.getPrincipalAmount());
            ps.setBigDecimal(6, loan.getInterestRate());
            ps.setBigDecimal(7, loan.getRoi());
            ps.setString(8, loan.getCurrency());
            ps.setString(9, loan.getCurrState().name());
            ps.setObject(10, loan.getInitDatetime());
            ps.setObject(11, loan.getAgreeLetterSendDatetime());
            ps.setObject(12, loan.getFundsReceivedDatetime());
            ps.setString(13, loan.getAgreeLetterUrl());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            loan.setId(key.longValue());
        }
        loan.setGmtCreate(now);
        loan.setGmtModify(now);
        return loan;
    }

    @Transactional
    public Loan update(Loan loan) {
        String sql = """
            UPDATE loans SET gmt_modify = ?,
                             borrower_id = ?, borrower_name = ?,
                             principal_amount = ?, interest_rate = ?, roi = ?,
                             currency = ?, curr_state = ?,
                             init_datetime = ?, agree_letter_send_datetime = ?,
                             funds_received_datetime = ?, agree_letter_url = ?
            WHERE id = ?
            """;
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(sql,
            Timestamp.valueOf(now),
            loan.getBorrowerId(), loan.getBorrowerName(),
            loan.getPrincipalAmount(), loan.getInterestRate(), loan.getRoi(),
            loan.getCurrency(), loan.getCurrState().name(),
            loan.getInitDatetime(), loan.getAgreeLetterSendDatetime(),
            loan.getFundsReceivedDatetime(), loan.getAgreeLetterUrl(),
            loan.getId());
        loan.setGmtModify(now);
        return loan;
    }

    @Transactional
    public Investment saveInvestment(Long loanId, Investment investment) {
        String sql = """
            INSERT INTO investments (gmt_create, gmt_modify,
                                     loan_id, investor_id, investor_name,
                                     amount, currency, datetime, fund_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setLong(3, loanId);
            ps.setLong(4, investment.getInvestorId());
            ps.setString(5, investment.getInvestorName());
            ps.setBigDecimal(6, investment.getAmount());
            ps.setString(7, investment.getCurrency());
            ps.setObject(8, investment.getDatetime());
            ps.setString(9, investment.getFundStatus().name());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            investment.setId(key.longValue());
        }
        investment.setGmtCreate(now);
        investment.setGmtModify(now);
        return investment;
    }

    @Transactional
    public int updateInvestmentFundStatus(Long investmentId, Long loanId, FundStatus status) {
        String sql = "UPDATE investments SET fund_status = ?, gmt_modify = ? WHERE id = ? AND loan_id = ?";
        return jdbc.update(sql, status.name(), Timestamp.valueOf(LocalDateTime.now()), investmentId, loanId);
    }

    @Transactional
    public void saveApproval(Approval approval, List<String> photoUrls) {
        String insertApproval = """
            INSERT INTO approvals (gmt_create, gmt_modify,
                                   loan_id, validator_employee_id,
                                   validator_employee_name, approval_datetime)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertApproval, new String[]{"id"});
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setLong(3, approval.getLoanId());
            ps.setLong(4, approval.getValidatorEmployeeId());
            ps.setString(5, approval.getValidatorEmployeeName());
            ps.setObject(6, approval.getApprovalDatetime());
            return ps;
        }, keyHolder);

        Number approvalKey = keyHolder.getKey();
        if (approvalKey == null) {
            throw new IllegalStateException("Failed to retrieve generated approval ID");
        }
        Long approvalId = approvalKey.longValue();
        approval.setId(approvalId);
        approval.setGmtCreate(now);
        approval.setGmtModify(now);

        if (photoUrls != null && !photoUrls.isEmpty()) {
            String insertPhoto = """
                INSERT INTO approval_photos (gmt_create, gmt_modify, approval_id, photo_url)
                VALUES (?, ?, ?, ?)
                """;
            for (String url : photoUrls) {
                jdbc.update(insertPhoto, Timestamp.valueOf(now), Timestamp.valueOf(now), approvalId, url);
            }
        }
    }

    @Transactional
    public void saveDisbursement(Disbursement disbursement) {
        String sql = """
            INSERT INTO disbursements (gmt_create, gmt_modify,
                                       loan_id, signed_agreement_url,
                                       field_officer_employee_id,
                                       field_officer_employee_name,
                                       disbursement_datetime, disbursed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setLong(3, disbursement.getLoanId());
            ps.setString(4, disbursement.getSignedAgreementUrl());
            ps.setLong(5, disbursement.getFieldOfficerEmployeeId());
            ps.setString(6, disbursement.getFieldOfficerEmployeeName());
            ps.setObject(7, disbursement.getDisbursementDatetime());
            ps.setBoolean(8, disbursement.isDisbursed());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            disbursement.setId(key.longValue());
        }
        disbursement.setGmtCreate(now);
        disbursement.setGmtModify(now);
    }
}

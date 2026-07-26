package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.Loan;
import org.example.amartha.loan.model.LoanStateEnum;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps {@code loans} table row → {@link Loan} entity.
 */
public class LoanRowMapper implements RowMapper<Loan> {

    @Override
    public Loan mapRow(ResultSet rs, int rowNum) throws SQLException {
        Loan loan = new Loan();
        loan.setId(rs.getLong("id"));
        loan.setGmtCreate(rs.getObject("gmt_create", java.time.LocalDateTime.class));
        loan.setGmtModify(rs.getObject("gmt_modify", java.time.LocalDateTime.class));
        loan.setBorrowerId(rs.getLong("borrower_id"));
        loan.setBorrowerName(rs.getString("borrower_name"));
        loan.setPrincipalAmount(rs.getBigDecimal("principal_amount"));
        loan.setInterestRate(rs.getBigDecimal("interest_rate"));
        loan.setRoi(rs.getBigDecimal("roi"));
        loan.setCurrency(rs.getString("currency"));
        loan.setCurrState(LoanStateEnum.valueOf(rs.getString("curr_state")));
        loan.setInitDatetime(rs.getObject("init_datetime", java.time.OffsetDateTime.class));
        loan.setAgreeLetterSendDatetime(rs.getObject("agree_letter_send_datetime", java.time.OffsetDateTime.class));
        loan.setFundsReceivedDatetime(rs.getObject("funds_received_datetime", java.time.OffsetDateTime.class));
        loan.setAgreeLetterUrl(rs.getString("agree_letter_url"));
        return loan;
    }
}

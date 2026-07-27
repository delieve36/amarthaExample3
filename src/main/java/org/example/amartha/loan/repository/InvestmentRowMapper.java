package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.FundStatus;
import org.example.amartha.loan.model.Investment;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps {@code investments} table row → {@link Investment} entity.
 */
public class InvestmentRowMapper implements RowMapper<Investment> {

    @Override
    public Investment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Investment inv = new Investment();
        inv.setId(rs.getLong("id"));
        inv.setGmtCreate(rs.getObject("gmt_create", java.time.LocalDateTime.class));
        inv.setGmtModify(rs.getObject("gmt_modify", java.time.LocalDateTime.class));
        inv.setLoanId(rs.getLong("loan_id"));
        inv.setInvestorId(rs.getLong("investor_id"));
        inv.setInvestorName(rs.getString("investor_name"));
        inv.setAmount(rs.getBigDecimal("amount"));
        inv.setCurrency(rs.getString("currency"));
        inv.setDatetime(rs.getObject("datetime", java.time.OffsetDateTime.class));
        inv.setFundStatus(FundStatus.valueOf(rs.getString("fund_status")));
        return inv;
    }
}

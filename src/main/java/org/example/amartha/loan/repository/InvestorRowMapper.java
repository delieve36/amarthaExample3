package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.Investor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps {@code investors} table row → {@link Investor} entity.
 */
public class InvestorRowMapper implements RowMapper<Investor> {

    @Override
    public Investor mapRow(ResultSet rs, int rowNum) throws SQLException {
        Investor investor = new Investor();
        investor.setId(rs.getLong("id"));
        investor.setInvestorId(rs.getLong("investor_id"));
        investor.setName(rs.getString("name"));
        investor.setEmailUrl(rs.getString("email_url"));
        investor.setRegisterDate(rs.getDate("register_date") != null
            ? rs.getDate("register_date").toLocalDate()
            : null);
        return investor;
    }
}

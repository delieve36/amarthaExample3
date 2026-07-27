package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.Disbursement;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps {@code disbursements} table row → {@link Disbursement} entity.
 */
public class DisbursementRowMapper implements RowMapper<Disbursement> {

    @Override
    public Disbursement mapRow(ResultSet rs, int rowNum) throws SQLException {
        Disbursement d = new Disbursement();
        d.setId(rs.getLong("id"));
        d.setGmtCreate(rs.getObject("gmt_create", java.time.LocalDateTime.class));
        d.setGmtModify(rs.getObject("gmt_modify", java.time.LocalDateTime.class));
        d.setLoanId(rs.getLong("loan_id"));
        d.setSignedAgreementUrl(rs.getString("signed_agreement_url"));
        d.setFieldOfficerEmployeeId(rs.getLong("field_officer_employee_id"));
        d.setFieldOfficerEmployeeName(rs.getString("field_officer_employee_name"));
        d.setDisbursementDatetime(rs.getObject("disbursement_datetime", java.time.OffsetDateTime.class));
        d.setDisbursed(rs.getBoolean("disbursed"));
        return d;
    }
}

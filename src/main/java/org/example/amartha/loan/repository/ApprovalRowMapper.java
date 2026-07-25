package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.Approval;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps {@code approvals} table row → {@link Approval} entity.
 * <p>
 * Note: {@link Approval#getValidatorPhotoUrls()} is loaded separately
 * via {@link LoanRepository#findPhotosByApprovalId}.
 */
public class ApprovalRowMapper implements RowMapper<Approval> {

    @Override
    public Approval mapRow(ResultSet rs, int rowNum) throws SQLException {
        Approval approval = new Approval();
        approval.setLoanId(rs.getLong("loan_id"));
        approval.setValidatorEmployeeId(rs.getString("validator_employee_id"));
        approval.setValidatorEmployeeName(rs.getString("validator_employee_name"));
        approval.setApprovalDatetime(rs.getObject("approval_datetime", java.time.OffsetDateTime.class));
        return approval;
    }
}

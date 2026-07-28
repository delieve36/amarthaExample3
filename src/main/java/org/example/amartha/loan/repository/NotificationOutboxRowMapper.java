package org.example.amartha.loan.repository;

import org.example.amartha.loan.model.NotificationOutbox;
import org.example.amartha.loan.model.NotificationStatus;
import org.example.amartha.loan.model.NotificationType;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps {@code notification_outbox} table row → {@link NotificationOutbox} entity.
 */
public class NotificationOutboxRowMapper implements RowMapper<NotificationOutbox> {

    @Override
    public NotificationOutbox mapRow(ResultSet rs, int rowNum) throws SQLException {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setId(rs.getLong("id"));
        outbox.setGmtCreate(rs.getObject("gmt_create", java.time.LocalDateTime.class));
        outbox.setGmtModify(rs.getObject("gmt_modify", java.time.LocalDateTime.class));
        outbox.setLoanId(rs.getLong("loan_id"));
        outbox.setInvestorId(rs.getLong("investor_id"));
        outbox.setRecipientEmail(rs.getString("recipient_email"));
        outbox.setType(NotificationType.valueOf(rs.getString("type")));
        outbox.setStatus(NotificationStatus.valueOf(rs.getString("status")));
        outbox.setAgreementUrl(rs.getString("agreement_url"));
        outbox.setSentDatetime(rs.getObject("sent_datetime", java.time.OffsetDateTime.class));
        outbox.setErrorMessage(rs.getString("error_message"));
        outbox.setRetryCount(rs.getInt("retry_count"));
        return outbox;
    }
}

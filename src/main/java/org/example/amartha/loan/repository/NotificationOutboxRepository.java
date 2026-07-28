package org.example.amartha.loan.repository;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.model.NotificationOutbox;
import org.example.amartha.loan.model.NotificationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Notification outbox data access.
 */
@Slf4j
@Repository
public class NotificationOutboxRepository {

    private final JdbcTemplate jdbc;

    public NotificationOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert a single outbox record — the most common pattern.
     * <p>Returns the same instance with {@code id} populated.</p>
     */
    @Transactional
    public NotificationOutbox insert(NotificationOutbox outbox) {
        String sql = """
            INSERT INTO notification_outbox (gmt_create, gmt_modify,
                loan_id, investor_id, recipient_email, type, status, agreement_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setLong(3, outbox.getLoanId());
            ps.setLong(4, outbox.getInvestorId());
            ps.setString(5, outbox.getRecipientEmail());
            ps.setString(6, outbox.getType().name());
            ps.setString(7, outbox.getStatus().name());
            ps.setString(8, outbox.getAgreementUrl());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            outbox.setId(key.longValue());
        }
        outbox.setGmtCreate(now);
        outbox.setGmtModify(now);
        log.debug("Inserted notification_outbox id={} loan={} investor={}",
            outbox.getId(), outbox.getLoanId(), outbox.getInvestorId());
        return outbox;
    }

    /**
     * Batch-insert pending outbox records (optional optimisation).
     * <p>Prefer {@link #insert(NotificationOutbox)} for clarity;
     * use this only when inserting many records in one go.</p>
     */
    @Transactional
    public void batchInsert(List<NotificationOutbox> outboxes) {
        if (outboxes == null || outboxes.isEmpty()) {
            return;
        }
        String sql = """
            INSERT INTO notification_outbox (gmt_create, gmt_modify,
                loan_id, investor_id, recipient_email, type, status, agreement_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        LocalDateTime now = LocalDateTime.now();
        jdbc.batchUpdate(sql, outboxes, outboxes.size(), (ps, outbox) -> {
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setLong(3, outbox.getLoanId());
            ps.setLong(4, outbox.getInvestorId());
            ps.setString(5, outbox.getRecipientEmail());
            ps.setString(6, outbox.getType().name());
            ps.setString(7, outbox.getStatus().name());
            ps.setString(8, outbox.getAgreementUrl());
        });
        log.debug("Batch-inserted {} notification_outbox records for loan {}",
            outboxes.size(), outboxes.get(0).getLoanId());
    }

    /**
     * Mark a single outbox record as SENT.
     */
    @Transactional
    public void markSent(Long outboxId, java.time.OffsetDateTime sentDatetime) {
        String sql = """
            UPDATE notification_outbox
            SET status = ?, sent_datetime = ?, gmt_modify = ?
            WHERE id = ?
            """;
        jdbc.update(sql, NotificationStatus.SENT.name(), sentDatetime,
            Timestamp.valueOf(LocalDateTime.now()), outboxId);
    }

    /**
     * Mark a single outbox record as FAILED.
     */
    @Transactional
    public void markFailed(Long outboxId, String errorMessage) {
        String sql = """
            UPDATE notification_outbox
            SET status = ?, error_message = ?, retry_count = retry_count + 1, gmt_modify = ?
            WHERE id = ?
            """;
        jdbc.update(sql, NotificationStatus.FAILED.name(), errorMessage,
            Timestamp.valueOf(LocalDateTime.now()), outboxId);
    }

    /**
     * Load all outbox records for a given loan.
     */
    public List<NotificationOutbox> findByLoanId(Long loanId) {
        String sql = "SELECT * FROM notification_outbox WHERE loan_id = ? ORDER BY id";
        return jdbc.query(sql, new NotificationOutboxRowMapper(), loanId);
    }

    /**
     * Load failed outbox records eligible for retry (status=FAILED, retries under threshold).
     */
    public List<NotificationOutbox> findFailedForRetry(int maxRetries) {
        String sql = """
            SELECT * FROM notification_outbox
            WHERE status = ? AND retry_count < ?
            ORDER BY gmt_create
            LIMIT 20
            """;
        return jdbc.query(sql, new NotificationOutboxRowMapper(),
            NotificationStatus.FAILED.name(), maxRetries);
    }
}

package org.example.amartha.loan.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 通知发件箱 — 每封待发送/已发送邮件的记录。
 * <p>与 loan 主流程解耦：investLoan 事务内批量写入 PENDING 记录，
 * 异步监听器逐条发送并更新状态。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
public class NotificationOutbox {

    private Long id;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModify;

    /** 关联贷款 ID */
    private Long loanId;

    /** 投资者 ID */
    private Long investorId;

    /** 收件人邮箱 */
    private String recipientEmail;

    /** 通知类型 */
    private NotificationType type;

    /** 发送状态 */
    private NotificationStatus status;

    /** 协议信链接 */
    private String agreementUrl;

    /** 实际发送时间（含时区） */
    private OffsetDateTime sentDatetime;

    /** 发送失败时的错误信息 */
    private String errorMessage;

    /** 重试次数 */
    private int retryCount;

    // ------------------------------------------------------------------
    // Factory
    // ------------------------------------------------------------------

    public static NotificationOutbox createPending(Long loanId, Long investorId,
                                                    String recipientEmail, String agreementUrl) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.loanId = loanId;
        outbox.investorId = investorId;
        outbox.recipientEmail = recipientEmail;
        outbox.type = NotificationType.AGREEMENT_LETTER;
        outbox.status = NotificationStatus.PENDING;
        outbox.agreementUrl = agreementUrl;
        outbox.retryCount = 0;
        return outbox;
    }

    // ------------------------------------------------------------------
    // State transitions
    // ------------------------------------------------------------------

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentDatetime = OffsetDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }
}

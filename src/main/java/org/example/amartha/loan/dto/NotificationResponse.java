package org.example.amartha.loan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.amartha.loan.model.NotificationOutbox;
import org.example.amartha.loan.model.NotificationStatus;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private Long investorId;
    private String recipientEmail;
    private NotificationStatus status;
    private String agreementUrl;
    private OffsetDateTime sentDatetime;
    private String errorMessage;
    private int retryCount;

    public static NotificationResponse from(NotificationOutbox outbox) {
        return new NotificationResponse(
            outbox.getId(),
            outbox.getInvestorId(),
            outbox.getRecipientEmail(),
            outbox.getStatus(),
            outbox.getAgreementUrl(),
            outbox.getSentDatetime(),
            outbox.getErrorMessage(),
            outbox.getRetryCount()
        );
    }
}

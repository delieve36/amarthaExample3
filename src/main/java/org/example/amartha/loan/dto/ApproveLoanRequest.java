package org.example.amartha.loan.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ApproveLoanRequest {

    @NotNull(message = "validatorEmployeeId is required")
    @Positive(message = "validatorEmployeeId must be positive")
    private Long validatorEmployeeId;

    private String validatorEmployeeName;

    @NotNull(message = "approvalDatetime is required")
    @PastOrPresent(message = "approvalDatetime must not be in the future")
    private OffsetDateTime approvalDatetime;

    @NotEmpty(message = "validatorPhotoUrls must not be empty")
    @Size(max = 20, message = "validatorPhotoUrls limit is 20")
    private List<String> validatorPhotoUrls;
}

package org.example.amartha.loan.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    private Long validatorEmployeeId;

    private String validatorEmployeeName;

    @NotNull(message = "approvalDatetime is required")
    private OffsetDateTime approvalDatetime;

    @NotEmpty(message = "validatorPhotoUrls must not be empty")
    private List<String> validatorPhotoUrls;
}

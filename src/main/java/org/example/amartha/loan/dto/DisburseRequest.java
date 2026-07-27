package org.example.amartha.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DisburseRequest {

    @NotBlank(message = "signedAgreementUrl is required")
    @Size(max = 2000, message = "signedAgreementUrl exceeds 2000 characters")
    private String signedAgreementUrl;

    @NotNull(message = "fieldOfficerEmployeeId is required")
    @Positive(message = "fieldOfficerEmployeeId must be positive")
    private Long fieldOfficerEmployeeId;

    private String fieldOfficerEmployeeName;

    @NotNull(message = "disbursementDatetime is required")
    private OffsetDateTime disbursementDatetime;
}

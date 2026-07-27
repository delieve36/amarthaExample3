package org.example.amartha.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String signedAgreementUrl;

    @NotNull(message = "fieldOfficerEmployeeId is required")
    private Long fieldOfficerEmployeeId;

    private String fieldOfficerEmployeeName;

    @NotNull(message = "disbursementDatetime is required")
    private OffsetDateTime disbursementDatetime;
}

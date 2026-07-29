package org.example.amartha.loan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ReceiveFundsRequest {

    @NotNull(message = "loanId is required")
    @Positive(message = "loanId must be positive")
    private Long loanId;

    @NotNull(message = "investmentId is required")
    @Positive(message = "investmentId must be positive")
    private Long investmentId;
}

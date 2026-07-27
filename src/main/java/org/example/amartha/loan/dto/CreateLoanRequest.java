package org.example.amartha.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CreateLoanRequest {

    @NotNull(message = "borrowerId is required")
    private Long borrowerId;

    private String borrowerName;

    @NotNull(message = "principalAmount is required")
    @Positive(message = "principalAmount must be positive")
    private BigDecimal principalAmount;

    @NotNull(message = "interestRate is required")
    private BigDecimal interestRate;

    @NotNull(message = "roi is required")
    private BigDecimal roi;

    @NotBlank(message = "currency is required")
    private String currency;
}

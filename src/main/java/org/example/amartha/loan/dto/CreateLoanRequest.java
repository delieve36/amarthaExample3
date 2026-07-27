package org.example.amartha.loan.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    @DecimalMin(value = "0.00", message = "interestRate must be >= 0")
    @DecimalMax(value = "100.00", message = "interestRate must be <= 100")
    private BigDecimal interestRate;

    @NotNull(message = "roi is required")
    @DecimalMin(value = "0.00", message = "roi must be >= 0")
    @DecimalMax(value = "100.00", message = "roi must be <= 100")
    private BigDecimal roi;

    @NotBlank(message = "currency is required")
    private String currency;
}

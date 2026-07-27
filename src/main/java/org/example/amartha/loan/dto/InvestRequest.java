package org.example.amartha.loan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.example.amartha.loan.model.FundStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InvestRequest {

    @NotNull(message = "investorId is required")
    @Positive(message = "investorId must be positive")
    private Long investorId;

    private String investorName;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    private String currency;

    private OffsetDateTime datetime;

    private FundStatus fundStatus;
}

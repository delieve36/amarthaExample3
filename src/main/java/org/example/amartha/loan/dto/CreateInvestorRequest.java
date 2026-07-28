package org.example.amartha.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CreateInvestorRequest {

    @NotNull
    @Positive
    private Long investorId;

    @NotBlank
    private String name;

    @NotBlank
    private String emailUrl;

    private LocalDate registerDate;
}
